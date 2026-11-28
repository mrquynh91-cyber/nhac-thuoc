package com.medlens.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Headless in-app Vietnamese speech-to-text dictator using Android's SpeechRecognizer API.
 * - Shows NO full-screen Google popup.
 * - Streams live partial & final text straight into the chat input box.
 * - Never auto-sends so the user can review, edit, or attach an image before pressing Send.
 */
class InAppSpeechDictator(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isActive = false

    fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(context)
    }.getOrDefault(false)

    fun startListening(
        baseText: String,
        onTextChanged: (String) -> Unit,
        onListeningChanged: (Boolean) -> Unit,
        onInfoMessage: (String) -> Unit,
        onFallbackNeeded: () -> Unit
    ) {
        if (!isAvailable()) {
            onFallbackNeeded()
            return
        }

        stopAndRelease()

        val prefix = baseText.trimEnd()
        val recognizer = runCatching {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrElse {
            onFallbackNeeded()
            return
        }
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isActive = true
                onListeningChanged(true)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isActive = false
                onListeningChanged(false)
            }

            override fun onError(error: Int) {
                isActive = false
                onListeningChanged(false)
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        onInfoMessage("Chưa nghe rõ giọng nói, hãy bấm Micro và nói lại nhé.")
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        onInfoMessage("Cần cấp quyền Micro để nhập giọng nói.")
                    }
                    SpeechRecognizer.ERROR_CLIENT -> Unit
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_AUDIO,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        onFallbackNeeded()
                    }
                    else -> {
                        onFallbackNeeded()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isActive = false
                onListeningChanged(false)
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (spoken.isNotBlank()) {
                    onTextChanged(mergeText(prefix, spoken))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    onTextChanged(mergeText(prefix, partial))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        runCatching {
            isActive = true
            onListeningChanged(true)
            recognizer.startListening(intent)
        }.onFailure {
            isActive = false
            onListeningChanged(false)
            onFallbackNeeded()
        }
    }

    fun stopListening(onListeningChanged: (Boolean) -> Unit) {
        if (isActive) {
            isActive = false
            runCatching { speechRecognizer?.stopListening() }
            onListeningChanged(false)
        }
    }

    fun stopAndRelease() {
        isActive = false
        val rec = speechRecognizer
        speechRecognizer = null
        if (rec != null) {
            runCatching { rec.cancel() }
            runCatching { rec.destroy() }
        }
    }

    private fun mergeText(prefix: String, spoken: String): String {
        return if (prefix.isBlank()) spoken else "$prefix $spoken"
    }
}
