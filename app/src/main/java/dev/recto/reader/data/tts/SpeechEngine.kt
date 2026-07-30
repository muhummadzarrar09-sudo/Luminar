package dev.recto.reader.data.tts

import android.content.Context
import android.os.Build
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
 * There are two ways to get neural speech on Android, and they differ by
 * about 55 MB and a licence:
 *
 *  - The platform TextToSpeech API, pointed at whichever engine the user
 *    has installed. Install the sherpa-onnx Piper engine and you get the
 *    same Piper neural voices you would have bundled, at zero APK cost.
 *  - Bundling sherpa-onnx and an ONNX voice directly. Same models, same
 *    audio, but +55 MB and Recto would have to become GPL-3.0, because
 *    Piper voices phonemise through espeak-ng, which is GPL. The
 *    sherpa-onnx maintainers hit exactly this and are removing espeak-ng
 *    to stay Apache-2.0.
 *
 * There is also a technical reason the platform API wins, which is less
 * obvious than the licence: it reports onRangeStart, telling us which
 * characters are being spoken RIGHT NOW. That is what drives the on-page
 * highlight. A bundled ONNX model hands back a buffer of audio samples with
 * no word timings at all, so the highlight would have to be guessed from
 * elapsed time.
 *
 * Everything above this interface - sentence splitting, highlighting, page
 * turns, the notification, the sleep timer - is engine-agnostic. If a
 * bundled engine ever becomes the right call, it implements this and
 * nothing else changes.
 */
interface SpeechEngine {

    /** Ready to speak. False while starting up or if no engine exists. */
    val isReady: Boolean

    suspend fun prepare(): Boolean

    /**
     * Speaks [utterance], calling back as it goes.
     *
     * @param onRange characters currently being spoken, RELATIVE to the
     *                utterance text. Not every engine reports this.
     */
    fun speak(
        utterance: Utterance,
        speed: Float,
        pitch: Float,
        onRange: (start: Int, end: Int) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    )

    fun stop()

    fun shutdown()

    /** Voices this engine offers for [locale], best first. */
    fun voices(locale: Locale): List<VoiceOption>

    fun selectVoice(id: String?)
}

/** A voice the user can pick, flattened out of whatever the engine exposes. */
data class VoiceOption(
    val id: String,
    val label: String,
    val localeTag: String,
    /** True if it works with no network. The whole point, so it is surfaced. */
    val offline: Boolean,
    /** Engine's own 100..500 rating; higher is better. */
    val quality: Int
)

/**
 * The platform engine.
 *
 * Speaks through whatever TTS engine the user has set in Android settings.
 * With the sherpa-onnx Piper engine installed that is genuine on-device
 * neural speech; with Google's it is Google's voices. Recto does not care,
 * which is the point.
 */
class SystemSpeechEngine(private val context: Context) : SpeechEngine {

    private var tts: TextToSpeech? = null
    private var preferredVoiceId: String? = null

    @Volatile
    private var ready = false

    override val isReady: Boolean get() = ready

    /**
     * TextToSpeech signals readiness through a callback, so this bridges it
     * to a suspend function. Everything downstream can then just await a
     * usable engine instead of polling a flag.
     */
    override suspend fun prepare(): Boolean = suspendCancellableCoroutine { cont ->
        // Guard against a double resume: onInit is documented to fire once,
        // but a misbehaving third-party engine calling it twice would crash
        // the coroutine machinery rather than the engine.
        var resumed = false

        val engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!resumed) {
                resumed = true
                cont.resume(ready)
            }
        }
        tts = engine

        cont.invokeOnCancellation {
            runCatching { engine.shutdown() }
            tts = null
            ready = false
        }
    }

    override fun speak(
        utterance: Utterance,
        speed: Float,
        pitch: Float,
        onRange: (Int, Int) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val engine = tts
        if (engine == null || !ready) {
            onError("No speech engine is available")
            return
        }

        engine.setSpeechRate(speed)
        engine.setPitch(pitch)

        preferredVoiceId?.let { wanted ->
            engine.voices?.firstOrNull { it.name == wanted }?.let { engine.voice = it }
        }

        val id = "recto-${utterance.start}"

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) onDone()
            }

            @Deprecated("Superseded by onError(String, Int)")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) onError("The voice stopped unexpectedly")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != id) return
                onError(
                    when (errorCode) {
                        TextToSpeech.ERROR_NETWORK,
                        TextToSpeech.ERROR_NETWORK_TIMEOUT ->
                            "That voice needs the internet. Pick an offline " +
                                "voice in the read-aloud settings."
                        TextToSpeech.ERROR_NOT_INSTALLED_YET ->
                            "The voice data is still downloading"
                        TextToSpeech.ERROR_SYNTHESIS ->
                            "The engine could not read that passage"
                        else -> "The voice stopped unexpectedly"
                    }
                )
            }

            // The reason this whole design uses the platform API: the engine
            // tells us which characters it is saying, so the page can
            // highlight them. Not every engine implements it, which is why
            // the caller falls back to highlighting the whole sentence.
            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int
            ) {
                if (utteranceId == id) onRange(start, end)
            }
        })

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        val spoken = Utterances.forSpeech(utterance.text)
        val result = engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result == TextToSpeech.ERROR) {
            onError("The engine refused that passage")
        }
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    override fun voices(locale: Locale): List<VoiceOption> {
        val engine = tts ?: return emptyList()
        val all = runCatching { engine.voices }.getOrNull() ?: return emptyList()

        return all
            .asSequence()
            .filter { it.locale.language == locale.language }
            // A voice the engine has not downloaded yet will fail at the
            // moment you press play, which is the worst time to find out.
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
            // Offline first, then by quality. An offline voice that works on
            // a plane beats a marginally nicer one that needs a signal.
            .sortedWith(compareByDescending<VoiceOption> { it.offline }
                .thenByDescending { it.quality })
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

/** Engines installed on this device, for the picker. */
fun installedEngines(context: Context): List<Pair<String, String>> {
    val probe = TextToSpeech(context.applicationContext) {}
    return try {
        probe.engines.orEmpty().map { it.name to it.label }
    } catch (_: Throwable) {
        emptyList()
    } finally {
        runCatching { probe.shutdown() }
    }
}

/** Build.VERSION guard kept in one place. */
internal val supportsRangeStart: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
