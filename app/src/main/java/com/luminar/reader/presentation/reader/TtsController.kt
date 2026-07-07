// app/src/main/java/com/luminar/reader/presentation/reader/TtsController.kt
package com.luminar.reader.presentation.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TtsState(
    val isAvailable: Boolean = false,
    val isSpeaking: Boolean = false,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val speed: Float = 1.0f
)

@Singleton
class TtsController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var chunks: List<String> = emptyList()
    private var currentIndex = 0

    private val _state = MutableStateFlow(TtsState())
    val state = _state.asStateFlow()

    init {
        tts = TextToSpeech(context) { status ->
            _state.update { it.copy(isAvailable = status == TextToSpeech.SUCCESS) }
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.update { it.copy(isSpeaking = true) }
                    }

                    override fun onDone(utteranceId: String?) {
                        currentIndex++
                        if (currentIndex < chunks.size) {
                            speakChunk(currentIndex)
                        } else {
                            _state.update { it.copy(isSpeaking = false, currentChunkIndex = 0) }
                        }
                    }

                    @Deprecated("Deprecated in API")
                    override fun onError(utteranceId: String?) {
                        _state.update { it.copy(isSpeaking = false) }
                    }
                })
            }
        }
    }

    fun startSpeaking(text: String, fromChunk: Int = 0) {
        if (tts == null || !_state.value.isAvailable) return

        // Split text into chunks at sentence boundaries (~500 chars max per chunk)
        chunks = splitIntoChunks(text)
        currentIndex = fromChunk.coerceIn(0, chunks.lastIndex.coerceAtLeast(0))

        _state.update {
            it.copy(
                totalChunks = chunks.size,
                currentChunkIndex = currentIndex
            )
        }

        if (chunks.isNotEmpty()) {
            speakChunk(currentIndex)
        }
    }

    fun pause() {
        tts?.stop()
        _state.update { it.copy(isSpeaking = false) }
    }

    fun resume() {
        if (chunks.isNotEmpty() && currentIndex < chunks.size) {
            speakChunk(currentIndex)
        }
    }

    fun stop() {
        tts?.stop()
        currentIndex = 0
        chunks = emptyList()
        _state.update { it.copy(isSpeaking = false, currentChunkIndex = 0, totalChunks = 0) }
    }

    fun skipForward() {
        if (currentIndex + 1 < chunks.size) {
            tts?.stop()
            currentIndex++
            speakChunk(currentIndex)
        }
    }

    fun skipBackward() {
        if (currentIndex > 0) {
            tts?.stop()
            currentIndex--
            speakChunk(currentIndex)
        }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        tts?.setSpeechRate(clamped)
        _state.update { it.copy(speed = clamped) }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun speakChunk(index: Int) {
        if (index !in chunks.indices) return
        _state.update { it.copy(currentChunkIndex = index) }
        tts?.speak(
            chunks[index],
            TextToSpeech.QUEUE_FLUSH,
            null,
            UUID.randomUUID().toString()
        )
    }

    private fun splitIntoChunks(text: String, maxLen: Int = 500): List<String> {
        if (text.length <= maxLen) return listOf(text)

        val result = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLen) {
                result.add(remaining.trim())
                break
            }

            // Find the best split point (sentence boundary)
            var splitAt = remaining.lastIndexOf(". ", maxLen)
            if (splitAt < maxLen / 2) splitAt = remaining.lastIndexOf("! ", maxLen)
            if (splitAt < maxLen / 2) splitAt = remaining.lastIndexOf("? ", maxLen)
            if (splitAt < maxLen / 2) splitAt = remaining.lastIndexOf("\n", maxLen)
            if (splitAt < maxLen / 2) splitAt = remaining.lastIndexOf(" ", maxLen)
            if (splitAt < maxLen / 4) splitAt = maxLen

            val chunk = remaining.substring(0, splitAt + 1).trim()
            if (chunk.isNotEmpty()) result.add(chunk)
            remaining = remaining.substring(splitAt + 1)
        }

        return result
    }
}
