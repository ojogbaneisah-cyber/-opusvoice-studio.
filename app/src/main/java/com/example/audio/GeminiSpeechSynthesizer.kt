package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.DialogueTurn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface SynthesisResult {
    data class Success(
        val wavFile: File,
        val amplitudes: List<Float>,
        val durationMs: Long,
        val isGeminiCloud: Boolean
    ) : SynthesisResult

    data class Error(val message: String, val cause: Throwable? = null) : SynthesisResult
}

class GeminiSpeechSynthesizer(private val context: Context) {
    private val TAG = "GeminiSynthesizer"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private var androidTts: TextToSpeech? = null
    private var isTtsInitialized = false
    private val ttsInitDeferred = CompletableDeferred<Boolean>()

    init {
        initAndroidTts()
    }

    private fun initAndroidTts() {
        androidTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTts?.language = Locale.US
                isTtsInitialized = true
                ttsInitDeferred.complete(true)
            } else {
                Log.w(TAG, "Android TextToSpeech init failed with status: $status")
                ttsInitDeferred.complete(false)
            }
        }
    }

    fun hasGeminiApiKey(): Boolean {
        val key = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
        return !key.isNullOrBlank() && !key.contains("MY_GEMINI_API_KEY") && key.length > 10
    }

    /**
     * Synthesizes single speaker audio.
     */
    suspend fun synthesizeSingleSpeaker(
        text: String,
        voiceName: String,
        stylePrompt: String,
        speed: Float = 1.0f,
        pitch: Float = 1.0f,
        forceDeviceTts: Boolean = false
    ): SynthesisResult = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext SynthesisResult.Error("Text is empty. Please enter text to generate speech.")
        }

        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
        val canUseGemini = !forceDeviceTts && hasGeminiApiKey()

        if (canUseGemini) {
            try {
                val pcmData = callGeminiTtsApi(
                    apiKey = apiKey,
                    text = cleanText,
                    voiceName = voiceName,
                    stylePrompt = stylePrompt
                )

                if (pcmData != null && pcmData.isNotEmpty()) {
                    val outputFile = File(context.filesDir, "audio/take_${System.currentTimeMillis()}.wav")
                    val success = PcmWavHelper.createWavFile(
                        pcmData = pcmData,
                        outputFile = outputFile,
                        sampleRate = 24000
                    )
                    if (success) {
                        val amplitudes = PcmWavHelper.extractAmplitudesFromPcm(pcmData)
                        // Estimate duration: 24000 samples/sec * 2 bytes/sample = 48000 bytes/sec
                        val durationMs = (pcmData.size.toDouble() / 48000.0 * 1000).toLong()
                        return@withContext SynthesisResult.Success(
                            wavFile = outputFile,
                            amplitudes = amplitudes,
                            durationMs = durationMs.coerceAtLeast(500L),
                            isGeminiCloud = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Cloud TTS failed, falling back to Device TTS", e)
            }
        }

        // Fallback or explicit Device TTS
        synthesizeWithDeviceTts(cleanText, speed, pitch)
    }

    /**
     * Synthesizes 2-speaker conversation mode.
     */
    suspend fun synthesizeMultiSpeaker(
        turns: List<DialogueTurn>,
        speaker1Voice: String,
        speaker2Voice: String,
        stylePrompt: String,
        speed: Float = 1.0f,
        pitch: Float = 1.0f,
        forceDeviceTts: Boolean = false
    ): SynthesisResult = withContext(Dispatchers.IO) {
        val validTurns = turns.filter { it.text.isNotBlank() }
        if (validTurns.isEmpty()) {
            return@withContext SynthesisResult.Error("Dialogue turns are empty. Please add speech for speakers.")
        }

        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Throwable) { "" }
        val canUseGemini = !forceDeviceTts && hasGeminiApiKey()

        if (canUseGemini) {
            try {
                // Synthesize each speaker segment and combine PCM streams
                val combinedPcm = ByteArrayOutputStream()
                val silenceGapSamples = (24000 * 0.3).toInt() * 2 // 300ms pause between turns
                val silenceBytes = ByteArray(silenceGapSamples)

                for ((index, turn) in validTurns.withIndex()) {
                    val voice = if (turn.speaker == 1) speaker1Voice else speaker2Voice
                    val turnStyle = if (turn.styleInstruction.isNotBlank()) {
                        turn.styleInstruction
                    } else {
                        stylePrompt
                    }

                    val pcmChunk = callGeminiTtsApi(
                        apiKey = apiKey,
                        text = turn.text,
                        voiceName = voice,
                        stylePrompt = turnStyle
                    )

                    if (pcmChunk != null && pcmChunk.isNotEmpty()) {
                        combinedPcm.write(pcmChunk)
                        if (index < validTurns.lastIndex) {
                            combinedPcm.write(silenceBytes)
                        }
                    }
                }

                val allPcm = combinedPcm.toByteArray()
                if (allPcm.isNotEmpty()) {
                    val outputFile = File(context.filesDir, "audio/take_dialogue_${System.currentTimeMillis()}.wav")
                    val success = PcmWavHelper.createWavFile(allPcm, outputFile, sampleRate = 24000)
                    if (success) {
                        val amplitudes = PcmWavHelper.extractAmplitudesFromPcm(allPcm)
                        val durationMs = (allPcm.size.toDouble() / 48000.0 * 1000).toLong()
                        return@withContext SynthesisResult.Success(
                            wavFile = outputFile,
                            amplitudes = amplitudes,
                            durationMs = durationMs.coerceAtLeast(500L),
                            isGeminiCloud = true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Multi-speaker Gemini generation failed, falling back to local TTS", e)
            }
        }

        // Fallback for multi-speaker: combine text turns and synthesize
        val combinedDialogueText = validTurns.joinToString(". ") { turn ->
            val speakerTag = if (turn.speaker == 1) "Speaker 1" else "Speaker 2"
            "$speakerTag: ${turn.text}"
        }
        synthesizeWithDeviceTts(combinedDialogueText, speed, pitch)
    }

    private suspend fun callGeminiTtsApi(
        apiKey: String,
        text: String,
        voiceName: String,
        stylePrompt: String
    ): ByteArray? {
        val modelsToTry = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-3.1-flash-tts-preview")

        for (model in modelsToTry) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

                val payload = JSONObject().apply {
                    // Contents
                    val contentsArray = JSONArray()
                    val contentObj = JSONObject()
                    val partsArray = JSONArray()

                    val promptText = if (stylePrompt.isNotBlank()) {
                        "Voice direction: $stylePrompt\n\nScript to speak: $text"
                    } else {
                        text
                    }

                    partsArray.put(JSONObject().put("text", promptText))
                    contentObj.put("parts", partsArray)
                    contentsArray.put(contentObj)
                    put("contents", contentsArray)

                    // Generation Config with Audio modality & Voice config
                    val genConfig = JSONObject().apply {
                        val modalities = JSONArray()
                        modalities.put("AUDIO")
                        put("responseModalities", modalities)

                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuilt = JSONObject().apply {
                                    put("voiceName", voiceName)
                                                                }
                                put("prebuiltVoiceConfig", prebuilt)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", genConfig)
                }

                val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    Log.w(TAG, "Model $model returned code: ${response.code}, body: $responseBody")
                    continue
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates") ?: continue
                if (candidates.length() == 0) continue

                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content") ?: continue
                val parts = content.optJSONArray("parts") ?: continue

                for (p in 0 until parts.length()) {
                    val part = parts.getJSONObject(p)
                    val inlineData = part.optJSONObject("inlineData") ?: continue
                    val base64Data = inlineData.optString("data")
                    if (base64Data.isNotEmpty()) {
                        return Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error trying model $model", e)
            }
        }
        return null
    }

    private suspend fun synthesizeWithDeviceTts(
        text: String,
        speed: Float,
        pitch: Float
    ): SynthesisResult {
        // Wait for TTS engine initialization
        if (!isTtsInitialized) {
            ttsInitDeferred.await()
        }

        val tts = androidTts ?: return SynthesisResult.Error("Device Text-to-Speech engine is not available.")

        return withContext(Dispatchers.IO) {
            val audioDir = File(context.filesDir, "audio")
            audioDir.mkdirs()
            val outputFile = File(audioDir, "take_local_${System.currentTimeMillis()}.wav")

            val deferred = CompletableDeferred<Boolean>()
            val utteranceId = UUID.randomUUID().toString()

            tts.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
            tts.setPitch(pitch.coerceIn(0.5f, 2.0f))

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        deferred.complete(true)
                    }
                }
                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        deferred.complete(false)
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val status = tts.synthesizeToFile(text, params, outputFile, utteranceId)
            if (status != TextToSpeech.SUCCESS) {
                return@withContext SynthesisResult.Error("Failed to schedule device text-to-speech synthesis.")
            }

            val finished = deferred.await()
            if (finished && outputFile.exists() && outputFile.length() > 44) {
                val bytes = outputFile.readBytes()
                val pcmOnly = if (bytes.size > 44) bytes.copyOfRange(44, bytes.size) else bytes
                val amplitudes = PcmWavHelper.extractAmplitudesFromPcm(pcmOnly)
                // Estimate duration
                val durationMs = ((bytes.size - 44).toDouble() / 32000.0 * 1000).toLong().coerceAtLeast(800L)
                SynthesisResult.Success(
                    wavFile = outputFile,
                    amplitudes = amplitudes,
                    durationMs = durationMs,
                    isGeminiCloud = false
                )
            } else {
                SynthesisResult.Error("Device speech synthesis failed to create audio file.")
            }
        }
    }

    fun shutdown() {
        try {
            androidTts?.stop()
            androidTts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
        androidTts = null
    }
}
