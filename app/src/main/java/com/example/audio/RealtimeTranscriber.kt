package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TranscriptionState(
    val isRecording: Boolean = false,
    val liveText: String = "",
    val soundLevelRms: Float = 0f,
    val statusText: String = "Idle"
)

class RealtimeTranscriber(
    private val context: Context,
    private val onLiveTextUpdated: (String) -> Unit,
    private val onFinalTextReceived: (String) -> Unit
) {
    private val TAG = "RealtimeTranscriber"
    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(TranscriptionState())
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening() {
        if (!isAvailable()) {
            _state.value = _state.value.copy(
                statusText = "Speech recognition service not available on device"
            )
            return
        }

        try {
            stopListening()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = _state.value.copy(
                            isRecording = true,
                            statusText = "Listening... Speak clearly into the microphone"
                        )
                    }

                    override fun onBeginningOfSpeech() {
                        _state.value = _state.value.copy(statusText = "Transcribing voice in real-time...")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _state.value = _state.value.copy(
                            soundLevelRms = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                        )
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _state.value = _state.value.copy(
                            statusText = "Processing speech...",
                            soundLevelRms = 0f
                        )
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "RECORD_AUDIO permission missing"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Transcription stopped"
                        }
                        Log.w(TAG, "SpeechRecognizer error: $error ($message)")
                        _state.value = _state.value.copy(
                            isRecording = false,
                            statusText = message,
                            soundLevelRms = 0f
                        )
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestResult = matches?.firstOrNull() ?: ""
                        if (bestResult.isNotBlank()) {
                            onFinalTextReceived(bestResult)
                        }
                        _state.value = _state.value.copy(
                            isRecording = false,
                            liveText = "",
                            statusText = "Transcribed successfully"
                        )
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = partials?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _state.value = _state.value.copy(liveText = text)
                            onLiveTextUpdated(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            _state.value = _state.value.copy(isRecording = true, statusText = "Initializing microphone...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            _state.value = _state.value.copy(
                isRecording = false,
                statusText = "Error starting microphone: ${e.localizedMessage}"
            )
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer", e)
        }
        speechRecognizer = null
        _state.value = _state.value.copy(isRecording = false, soundLevelRms = 0f)
    }
}
