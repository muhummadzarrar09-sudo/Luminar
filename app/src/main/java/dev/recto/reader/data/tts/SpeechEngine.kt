package dev.recto.reader.data.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * What Recto needs from a voice, and nothing else.
 *
 * WHY THIS INTERFACE EXISTS
 * There are two ways to get neural speech on Android:
 *
 *  - The platform TextToSpeech API, pointed at whichever engine the user has
 *    installed. Google's engine, or a sherpa-onnx Piper/Kokoro engine APK.
 *  - Bundling sherpa-onnx and an ONNX voice directly. Same models, +55 MB,
 *    and Recto would have to become GPL-3.0 because Piper phonemises through
 *    espeak-ng. The sherpa-onnx maintainers hit exactly this and are removing
 *    espeak-ng to stay Apache-2.0.
 *
 * The platform API also reports onRangeStart - which characters are being
 * spoken right now - and that is what drives the on-page highlight. A
 * bundled ONNX model returns audio samples with no word timings at all.
 *
 * WHY THE API IS QUEUE-SHAPED
 * The first version spoke one sentence, waited for onDone, then synthesised
 * the next. That leaves a dead gap after every full stop while the next
 * sentence is generated - a few hundred milliseconds on a fast phone,
 * far worse on a slow one. It made good voices sound broken.
 *
 * So the engine now takes a QUEUE. Several sentences are handed over at
 * once, and the engine synthesises ahead while the current one plays.
 * That means one listener dispatching by utterance id, rather than a fresh
 * listener per sentence, which is why the callbacks are registered once in
 * [setListener] instead of being passed to each speak call.
 */
interface SpeechEngine {

    val isReady: Boolean

    /**
     * @param enginePackage which TTS engine to use, or null for the system
     *                      default. Lets Recto switch engines without sending
     *                      the reader into Android's accessibility settings.
     */
    suspend fun prepare(enginePackage: String? = null): Boolean

    /** TTS engines installed on this device. */
    fun engines(): List<EngineOption>

    /** Speaks a short sample so a voice can be auditioned before committing. */
    fun preview(text: String, voiceId: String?, speed: Float, pitch: Float)

    /** Registered once. Every callback carries the id passed to [enqueue]. */
    fun setListener(listener: SpeechListener)

    /**
     * Hands one utterance to the engine.
     *
     * @param flush true to drop whatever is queued and start here; false to
     *              append, which is what keeps the engine working ahead.
     * @return false if the engine refused it
     */
    fun enqueue(
        id: String,
        text: String,
        flush: Boolean,
        speed: Float,
        pitch: Float
    ): Boolean

    fun stop()

    fun shutdown()

    fun voices(locale: Locale): List<VoiceOption>

    fun selectVoice(id: String?)
}

/** Utterance id used for voice previews, so callbacks can ignore them. */
const val PREVIEW_ID = "recto-preview"

/**
 * Progress for queued utterances.
 *
 * All of these arrive on an engine thread, not the main thread.
 */
interface SpeechListener {
    /** Audio for [id] has actually begun. The right moment to highlight. */
    fun onStart(id: String)

    /** Characters [start] until [end] of [id]'s text are being spoken. */
    fun onRange(id: String, start: Int, end: Int)

    fun onDone(id: String)

    fun onError(id: String, message: String)
}

/** A TTS engine installed on the device. */
data class EngineOption(
    val packageName: String,
    val label: String
)

/** A voice the user can pick, flattened out of whatever the engine exposes. */
data class VoiceOption(
    val id: String,
    val label: String,
    val localeTag: String,
    /** True if it works with no network. */
    val offline: Boolean,
    /** Engine's own 100..500 rating; higher is better. */
    val quality: Int
)

/**
 * The platform engine.
 *
 * Speaks through whatever TTS engine Android is set to. Recto does not care
 * which, and that is the point.
 */
class SystemSpeechEngine(private val context: Context) : SpeechEngine {

    private var tts: TextToSpeech? = null
    private var preferredVoiceId: String? = null
    private var listener: SpeechListener? = null

    /** Rate and pitch are engine-wide, so only re-apply when they change. */
    private var appliedSpeed = Float.NaN
    private var appliedPitch = Float.NaN

    @Volatile
    private var ready = false

    override val isReady: Boolean get() = ready

    override suspend fun prepare(enginePackage: String?): Boolean =
        suspendCancellableCoroutine { cont ->
        // Guard against a double resume: onInit is documented to fire once,
        // but a misbehaving third-party engine calling it twice would crash
        // the coroutine machinery rather than the engine.
        var resumed = false

        val onInit = TextToSpeech.OnInitListener { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!resumed) {
                resumed = true
                cont.resume(ready)
            }
        }

        // The three-arg constructor picks a specific engine. Passing null
        // would NOT mean "default" - it throws - so the branch is real.
        val engine = if (enginePackage.isNullOrBlank()) {
            TextToSpeech(context.applicationContext, onInit)
        } else {
            TextToSpeech(context.applicationContext, onInit, enginePackage)
        }
        tts = engine
        appliedSpeed = Float.NaN
        appliedPitch = Float.NaN

        // One listener for the whole session, dispatching by id. Setting a
        // fresh listener per utterance would break queueing outright: the
        // last one set wins, so every earlier sentence in the queue would
        // lose its callbacks.
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { listener?.onStart(it) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { listener?.onDone(it) }
            }

            @Deprecated("Superseded by onError(String, Int)")
            override fun onError(utteranceId: String?) {
                utteranceId?.let {
                    listener?.onError(it, "The voice stopped unexpectedly")
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                val id = utteranceId ?: return
                listener?.onError(id, describe(errorCode))
            }

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int
            ) {
                utteranceId?.let { listener?.onRange(it, start, end) }
            }
        })

        cont.invokeOnCancellation {
            runCatching { engine.shutdown() }
            tts = null
            ready = false
        }
    }

    private fun describe(errorCode: Int): String = when (errorCode) {
        TextToSpeech.ERROR_NETWORK,
        TextToSpeech.ERROR_NETWORK_TIMEOUT ->
            "That voice needs the internet. Pick one marked offline, or " +
                "reconnect."
        TextToSpeech.ERROR_NOT_INSTALLED_YET ->
            "The voice data is still downloading"
        TextToSpeech.ERROR_SYNTHESIS ->
            "The engine could not read that passage"
        else -> "The voice stopped unexpectedly"
    }

    override fun setListener(listener: SpeechListener) {
        this.listener = listener
    }

    override fun enqueue(
        id: String,
        text: String,
        flush: Boolean,
        speed: Float,
        pitch: Float
    ): Boolean {
        val engine = tts ?: return false
        if (!ready) return false

        // setSpeechRate/setPitch affect everything queued afterwards, so
        // calling them on every utterance is wasted work. More importantly,
        // some engines restart synthesis when the rate changes, which would
        // undo the buffering we are here to build.
        if (speed != appliedSpeed) {
            engine.setSpeechRate(speed)
            appliedSpeed = speed
        }
        if (pitch != appliedPitch) {
            engine.setPitch(pitch)
            appliedPitch = pitch
        }

        preferredVoiceId?.let { wanted ->
            if (engine.voice?.name != wanted) {
                engine.voices?.firstOrNull { it.name == wanted }
                    ?.let { engine.voice = it }
            }
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(Utterances.forSpeech(text), mode, params, id)
        return result != TextToSpeech.ERROR
    }

    override fun engines(): List<EngineOption> {
        val engine = tts ?: return emptyList()
        return runCatching {
            engine.engines.orEmpty().map { EngineOption(it.name, it.label) }
        }.getOrDefault(emptyList())
    }

    override fun preview(text: String, voiceId: String?, speed: Float, pitch: Float) {
        val engine = tts ?: return
        if (!ready) return

        // Apply directly rather than through enqueue(): a preview must be
        // audible at the CURRENT settings even if they match what is already
        // applied, and it always interrupts.
        engine.setSpeechRate(speed)
        engine.setPitch(pitch)
        appliedSpeed = speed
        appliedPitch = pitch

        voiceId?.let { wanted ->
            engine.voices?.firstOrNull { it.name == wanted }?.let { engine.voice = it }
        }

        engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), PREVIEW_ID)
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        listener = null
        ready = false
    }

    override fun voices(locale: Locale): List<VoiceOption> {
        val engine = tts ?: return emptyList()
        val all = runCatching { engine.voices }.getOrNull() ?: return emptyList()

        return all
            .asSequence()
            .filter { it.locale.language == locale.language }
            // A voice the engine has not downloaded yet fails at the moment
            // you press play, which is the worst time to find out.
            .filterNot { it.features?.contains(FEATURE_NOT_INSTALLED) == true }
            .map { v ->
                VoiceOption(
                    id = v.name,
                    label = prettyName(v),
                    localeTag = v.locale.toLanguageTag(),
                    offline = !v.isNetworkConnectionRequired,
                    quality = v.quality
                )
            }
            // Quality first, then offline. Google's best voices are network
            // ones, and on a slow phone they are both better AND faster than
            // anything local - so burying them under offline voices would
            // hide the best option this device has.
            .sortedWith(
                compareByDescending<VoiceOption> { it.quality }
                    .thenByDescending { it.offline }
            )
            .toList()
    }

    override fun selectVoice(id: String?) {
        preferredVoiceId = id
    }

    /**
     * Voice names are machine-ish - "en-us-x-tpf-local", "en_US-amy-medium".
     * This makes them scannable without pretending to know every engine's
     * naming scheme.
     */
    private fun prettyName(voice: Voice): String {
        val raw = voice.name
        val cleaned = raw
            .removePrefix(voice.locale.toLanguageTag().lowercase() + "-")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
        val label = cleaned.ifBlank { raw }
        return label.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private companion object {
        const val FEATURE_NOT_INSTALLED = "notInstalled"
    }
}
