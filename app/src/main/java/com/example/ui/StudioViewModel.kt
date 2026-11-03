package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExportConfig
import com.example.audio.CharacterAiChimeInterceptorService
import com.example.audio.ChimeInterceptorStats
import com.example.audio.ChimeStreamInterceptor
import com.example.audio.ClonedVoiceProfile
import com.example.audio.GeminiSpeechSynthesizer
import com.example.audio.LoadedAudioBuffer
import com.example.audio.MessengerTarget
import com.example.audio.OpusAudioEngine
import com.example.audio.OpusContainerTarget
import com.example.audio.PcmWavHelper
import com.example.audio.PlayerState
import com.example.audio.RealtimeTranscriber
import com.example.audio.StudioAudioPlayer
import com.example.audio.StudioAudioSource
import com.example.audio.SynthesisResult
import com.example.audio.TranscriptionState
import com.example.audio.VoiceCloningEngine
import com.example.audio.VoiceNoteFormatSpec
import com.example.audio.VoiceNoteIntentExporter
import com.example.audio.VoiceQualityReport
import com.example.data.db.AppDatabase
import com.example.data.db.VoiceTakeEntity
import com.example.data.model.AVAILABLE_VOICES
import com.example.data.model.VoiceGender
import com.example.data.model.VoicePreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class StudioTab {
    OPUS_TRIMMER, CAI_CALL, CLONING, HISTORY
}

data class CharacterCallState(
    val isCallActive: Boolean = false,
    val isCallMuted: Boolean = false,
    val isListeningToCaller: Boolean = false,
    val isChimeGateMuting: Boolean = false,
    val callDurationSeconds: Int = 0,
    val speakerphoneEnabled: Boolean = true,
    val chimeBlockerEnabled: Boolean = true,
    val postSpeechMuteWindowMs: Int = 950,
    val spectralNotchEnabled: Boolean = true,
    val telephonyBandpassEnabled: Boolean = true,
    val characterName: String = "Aria (c.ai Persona)",
    val characterPersona: String = "Witty, empathetic companion speaking naturally on a phone call without robotic pauses",
    val lastCallerStatement: String = "",
    val lastCharacterReply: String = "Hey! I'm on the line—go ahead and put me on speaker, zero chimes enabled.",
    val callStatusText: String = "Chime-Blocker Armed • 48kHz Mono Call Bridge"
)

data class StudioUiState(
    val activeTab: StudioTab = StudioTab.OPUS_TRIMMER,
    val scriptText: String = "",
    val stylePrompt: String = "Warm, expressive phone call voice with natural pauses",
    val selectedVoice: VoicePreset = AVAILABLE_VOICES.first { it.id == "Puck" },
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val isSynthesizing: Boolean = false,
    val activeTake: VoiceTakeEntity? = null,
    val hasApiKey: Boolean = false
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val takeDao = database.voiceTakeDao()

    val audioPlayer = StudioAudioPlayer(application)
    val synthesizer = GeminiSpeechSynthesizer(application)
    val voiceCloningEngine = VoiceCloningEngine(application)
    val opusAudioEngine = OpusAudioEngine(application)

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // OpusVoice Studio Trimmer & Formatter State
    private val _loadedOpusAudio = MutableStateFlow<LoadedAudioBuffer?>(null)
    val loadedOpusAudio: StateFlow<LoadedAudioBuffer?> = _loadedOpusAudio.asStateFlow()

    private val _trimStartFraction = MutableStateFlow(0.0f)
    val trimStartFraction: StateFlow<Float> = _trimStartFraction.asStateFlow()

    private val _trimEndFraction = MutableStateFlow(1.0f)
    val trimEndFraction: StateFlow<Float> = _trimEndFraction.asStateFlow()

    private val _voiceNoteFormatSpec = MutableStateFlow(VoiceNoteFormatSpec())
    val voiceNoteFormatSpec: StateFlow<VoiceNoteFormatSpec> = _voiceNoteFormatSpec.asStateFlow()

    private val _lastExportedFilePath = MutableStateFlow<String?>(null)
    val lastExportedFilePath: StateFlow<String?> = _lastExportedFilePath.asStateFlow()

    // Character.AI Live Call & Chime-Blocker State
    private val _characterCallState = MutableStateFlow(CharacterCallState())
    val characterCallState: StateFlow<CharacterCallState> = _characterCallState.asStateFlow()
    val chimeInterceptorStats: StateFlow<ChimeInterceptorStats> = ChimeStreamInterceptor.globalStats
    private var callTimerJob: Job? = null

    val transcriber: RealtimeTranscriber = RealtimeTranscriber(
        context = application,
        onLiveTextUpdated = {},
        onFinalTextReceived = {}
    )
    val transcriptionState: StateFlow<TranscriptionState> = transcriber.state

    private val _uiState = MutableStateFlow(
        StudioUiState(
            hasApiKey = synthesizer.hasGeminiApiKey()
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val allTakes: StateFlow<List<VoiceTakeEntity>> = takeDao.getAllTakes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playerState: StateFlow<PlayerState> = audioPlayer.playerState

    private val _exportConfig = MutableStateFlow(AudioExportConfig())
    val exportConfig: StateFlow<AudioExportConfig> = _exportConfig.asStateFlow()

    // Voice Cloning state
    private val _clonedVoices = MutableStateFlow<List<ClonedVoiceProfile>>(emptyList())
    val clonedVoices: StateFlow<List<ClonedVoiceProfile>> = _clonedVoices.asStateFlow()

    private val _isAnalyzingClone = MutableStateFlow(false)
    val isAnalyzingClone: StateFlow<Boolean> = _isAnalyzingClone.asStateFlow()

    private val _cloneReport = MutableStateFlow<VoiceQualityReport?>(null)
    val cloneReport: StateFlow<VoiceQualityReport?> = _cloneReport.asStateFlow()

    private val _selectedCloneSampleUri = MutableStateFlow<Uri?>(null)
    val selectedCloneSampleUri: StateFlow<Uri?> = _selectedCloneSampleUri.asStateFlow()

    init {
        viewModelScope.launch {
            val demoAudio = opusAudioEngine.createInitialDemoAudio()
            _loadedOpusAudio.value = demoAudio
            audioPlayer.loadAndPlay(demoAudio.wavFile.absolutePath, autoPlay = false)
        }

        viewModelScope.launch {
            takeDao.getAllTakes().collect { takes ->
                if (takes.isNotEmpty() && _uiState.value.activeTake == null) {
                    _uiState.value = _uiState.value.copy(activeTake = takes.first())
                }
            }
        }
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setTab(tab: StudioTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun setSelectedVoice(voice: VoicePreset) {
        _uiState.value = _uiState.value.copy(selectedVoice = voice)
    }

    fun setSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(speed = speed)
        audioPlayer.setSpeed(speed)
    }

    // OpusVoice Studio Upload, Record, Trim & Export Methods
    fun uploadAudioToOpusStudio(uri: Uri, displayName: String) {
        viewModelScope.launch {
            val loaded = opusAudioEngine.loadUploadedAudioUri(uri, displayName)
            if (loaded != null) {
                _loadedOpusAudio.value = loaded
                _trimStartFraction.value = 0f
                _trimEndFraction.value = 1f
                val cleanBase = displayName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9_-]"), "_")
                _voiceNoteFormatSpec.value = _voiceNoteFormatSpec.value.copy(customFileName = "${cleanBase}_48k")
                audioPlayer.loadAndPlay(loaded.wavFile.absolutePath, autoPlay = false)
                Toast.makeText(getApplication(), "Loaded $displayName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "Could not decode audio file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleOpusMicrophoneRecording() {
        if (opusAudioEngine.isRecordingMic.value) {
            viewModelScope.launch {
                val recorded = opusAudioEngine.stopMicrophoneRecording()
                if (recorded != null) {
                    _loadedOpusAudio.value = recorded
                    _trimStartFraction.value = 0f
                    _trimEndFraction.value = 1f
                    _voiceNoteFormatSpec.value = _voiceNoteFormatSpec.value.copy(
                        customFileName = "voice_note_48k_${System.currentTimeMillis() % 10000}"
                    )
                    audioPlayer.loadAndPlay(recorded.wavFile.absolutePath, autoPlay = true)

                    val ampsJson = PcmWavHelper.serializeAmplitudes(recorded.amplitudes)
                    val micTake = VoiceTakeEntity(
                        title = recorded.sourceName,
                        script = "Direct 48kHz 16-bit Mono AudioRecord Capture (${opusAudioEngine.recordingTelemetry.value.audioSource.label})",
                        stylePrompt = "48kHz Mono Studio Microphone",
                        isMultiSpeaker = false,
                        speaker1Voice = "48kHz Mic",
                        speed = 1.0f,
                        pitch = 1.0f,
                        durationMs = recorded.durationMs,
                        filePath = recorded.wavFile.absolutePath,
                        amplitudesJson = ampsJson
                    )
                    val id = takeDao.insertTake(micTake)
                    _uiState.value = _uiState.value.copy(activeTake = micTake.copy(id = id))

                    Toast.makeText(
                        getApplication(),
                        "48kHz Mono recording (${recorded.totalSamples} samples) loaded into waveform!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            audioPlayer.pause()
            opusAudioEngine.startMicrophoneRecording(viewModelScope)
        }
    }

    fun togglePauseOpusMicrophoneRecording() {
        opusAudioEngine.togglePauseMicrophoneRecording()
    }

    fun setStudioAudioSource(source: StudioAudioSource) {
        opusAudioEngine.setAudioSource(source)
    }

    fun setStudioNoiseSuppression(enabled: Boolean) {
        opusAudioEngine.setNoiseSuppressionEnabled(enabled)
    }

    fun setStudioAutoGainControl(enabled: Boolean) {
        opusAudioEngine.setAutoGainControlEnabled(enabled)
    }

    fun setStudioInputGain(gain: Float) {
        opusAudioEngine.setInputGainMultiplier(gain)
    }

    fun setTrimRange(start: Float, end: Float) {
        val safeStart = start.coerceIn(0f, 0.95f)
        val safeEnd = end.coerceIn(safeStart + 0.03f, 1f)
        _trimStartFraction.value = safeStart
        _trimEndFraction.value = safeEnd
    }

    fun resetTrimRange() {
        _trimStartFraction.value = 0f
        _trimEndFraction.value = 1f
    }

    fun playTrimmedPreview() {
        val audio = _loadedOpusAudio.value ?: return
        audioPlayer.loadAndPlay(audio.wavFile.absolutePath, autoPlay = false)
        audioPlayer.seekTo(_trimStartFraction.value)
        audioPlayer.play()
    }

    fun applyTrimCut() {
        val current = _loadedOpusAudio.value ?: return
        val start = _trimStartFraction.value
        val end = _trimEndFraction.value
        if (start <= 0.005f && end >= 0.995f) return

        viewModelScope.launch {
            val trimmed = opusAudioEngine.trimAudioSegment(current, start, end)
            if (trimmed != null) {
                _loadedOpusAudio.value = trimmed
                _trimStartFraction.value = 0f
                _trimEndFraction.value = 1f
                audioPlayer.loadAndPlay(trimmed.wavFile.absolutePath, autoPlay = true)
            }
        }
    }

    fun toggleVoiceNotePreset(enabled: Boolean) {
        _voiceNoteFormatSpec.value = _voiceNoteFormatSpec.value.copy(
            isVoiceNotePresetEnabled = enabled,
            sampleRateHz = if (enabled) 48000 else _voiceNoteFormatSpec.value.sampleRateHz,
            isMono = if (enabled) true else _voiceNoteFormatSpec.value.isMono,
            containerTarget = if (enabled) OpusContainerTarget.OGG_OPUS else _voiceNoteFormatSpec.value.containerTarget
        )
    }

    fun toggleMonoChannel() {
        val nextMono = !_voiceNoteFormatSpec.value.isMono
        _voiceNoteFormatSpec.value = _voiceNoteFormatSpec.value.copy(
            isVoiceNotePresetEnabled = false,
            isMono = nextMono
        )
    }

    fun cycleOpusSampleRate() {
        val nextRate = when (_voiceNoteFormatSpec.value.sampleRateHz) {
            48000 -> 44100
            44100 -> 24000
            else -> 48000
        }
        _voiceNoteFormatSpec.value = _voiceNoteFormatSpec.value.copy(
            isVoiceNotePresetEnabled = false,
            sampleRateHz = nextRate
        )
    }

    fun cycleOpusContainerTarget() {
        val next = when (_voiceNoteFormatSpec.value.containerTarget) {
            OpusContainerTarget.OGG_OPUS -> OpusContainerTarget.OPUS_PTT
            OpusContainerTarget.OPUS_PTT -> OpusContainerTarget.WAV_PCM
            else -> OpusContainerTarget.OGG_OPUS
        }
        setOpusContainerTarget(next)
    }

    fun setOpusContainerTarget(target: OpusContainerTarget) {
        _voiceNoteFormatSpec.value = _voiceNoteFormatSpec.value.copy(containerTarget = target)
    }

    fun setCustomExportFileName(name: String) {
        _voiceNoteFormatSpec.value = _voiceNoteFormatSpec.value.copy(customFileName = name)
    }

    fun applyUiChimeSuppression() {
        val current = _loadedOpusAudio.value ?: return
        viewModelScope.launch {
            val cleaned = opusAudioEngine.suppressUiChimesOnBuffer(current)
            if (cleaned != null) {
                _loadedOpusAudio.value = cleaned
                audioPlayer.loadAndPlay(cleaned.wavFile.absolutePath, autoPlay = true)
                Toast.makeText(
                    getApplication(),
                    "c.ai UI chimes & non-vocal transients filtered!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun sendVoiceNoteToMessenger(context: Context, messenger: MessengerTarget) {
        val current = _loadedOpusAudio.value ?: return
        val start = _trimStartFraction.value
        val end = _trimEndFraction.value
        val autoContainer = when (messenger) {
            MessengerTarget.WHATSAPP -> OpusContainerTarget.OPUS_PTT
            MessengerTarget.TELEGRAM -> OpusContainerTarget.OGG_OPUS
            MessengerTarget.GOOGLE_CHAT -> OpusContainerTarget.OGG_OPUS
            MessengerTarget.CHARACTER_AI -> OpusContainerTarget.WAV_PCM
        }
        val targetSpec = _voiceNoteFormatSpec.value.copy(
            isVoiceNotePresetEnabled = true,
            sampleRateHz = OpusAudioEngine.STUDIO_SAMPLE_RATE_HZ,
            isMono = true,
            containerTarget = autoContainer,
            useWhatsAppPttNaming = messenger == MessengerTarget.WHATSAPP
        )
        _voiceNoteFormatSpec.value = targetSpec

        viewModelScope.launch {
            val exportedFile = opusAudioEngine.processAndExportAudio(current, start, end, targetSpec)
            if (exportedFile != null && exportedFile.exists()) {
                _lastExportedFilePath.value = "Downloads/OpusVoiceStudio/${exportedFile.name} (${messenger.badgeText})"
                VoiceNoteIntentExporter.exportVoiceNoteToApp(
                    context = context,
                    audioFile = exportedFile,
                    messenger = messenger
                )
            } else {
                Toast.makeText(
                    context,
                    "Opus encoding failed for ${messenger.label}. No broken file was written.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun downloadProcessedOpusAudio(context: Context) {
        val current = _loadedOpusAudio.value ?: return
        val spec = _voiceNoteFormatSpec.value
        val start = _trimStartFraction.value
        val end = _trimEndFraction.value

        viewModelScope.launch {
            val exportedFile = opusAudioEngine.processAndExportAudio(current, start, end, spec)
            if (exportedFile != null && exportedFile.exists()) {
                _lastExportedFilePath.value = "Downloads/OpusVoiceStudio/${exportedFile.name}"

                val amps = PcmWavHelper.serializeAmplitudes(current.amplitudes)
                val take = VoiceTakeEntity(
                    title = exportedFile.name,
                    script = "OpusVoice Trimmed & Formatted (${if (spec.isVoiceNotePresetEnabled || spec.isMono) "Mono" else "Stereo"}, ${if (spec.isVoiceNotePresetEnabled) 48000 else spec.sampleRateHz}Hz)",
                    stylePrompt = spec.containerTarget.label,
                    isMultiSpeaker = false,
                    speaker1Voice = "OpusStudio",
                    speed = 1.0f,
                    pitch = 1.0f,
                    durationMs = ((end - start) * current.durationMs).toLong().coerceAtLeast(500L),
                    filePath = exportedFile.absolutePath,
                    amplitudesJson = amps
                )
                val id = takeDao.insertTake(take)
                _uiState.value = _uiState.value.copy(activeTake = take.copy(id = id))

                Toast.makeText(
                    context,
                    "Downloaded ${exportedFile.name} (48kHz Mono Voice Note)!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "Failed to encode audio file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareProcessedOpusAudio(context: Context) {
        val current = _loadedOpusAudio.value ?: return
        val spec = _voiceNoteFormatSpec.value
        val start = _trimStartFraction.value
        val end = _trimEndFraction.value

        viewModelScope.launch {
            val exportedFile = opusAudioEngine.processAndExportAudio(current, start, end, spec)
            if (exportedFile != null && exportedFile.exists()) {
                PcmWavHelper.shareAudioFile(context, exportedFile, exportedFile.name, null)
            } else {
                Toast.makeText(context, "Failed to encode audio file for sharing", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Character.AI Live Voice Call & Chime-Blocker Methods
    fun toggleCharacterLiveCall() {
        val current = _characterCallState.value
        val nextActive = !current.isCallActive
        if (nextActive) {
            opusAudioEngine.setAudioSource(StudioAudioSource.VOICE_COMMUNICATION)
            if (current.chimeBlockerEnabled) {
                CharacterAiChimeInterceptorService.startService(getApplication())
            }
            _characterCallState.value = current.copy(
                isCallActive = true,
                isCallMuted = false,
                isListeningToCaller = false,
                isChimeGateMuting = false,
                callDurationSeconds = 0,
                callStatusText = "Live Call Connected • Zero-Chime Gate Armed (${current.postSpeechMuteWindowMs}ms)"
            )
            startCallTimer()
            respondToCallerInCharacter(
                callerPromptInput = "Caller just connected the phone line. Greet them naturally on the call."
            )
        } else {
            stopCallTimer()
            if (opusAudioEngine.isRecordingMic.value) {
                viewModelScope.launch { opusAudioEngine.stopMicrophoneRecording() }
            }
            transcriber.stopListening()
            audioPlayer.pause()
            CharacterAiChimeInterceptorService.stopService(getApplication())
            _characterCallState.value = current.copy(
                isCallActive = false,
                isCallMuted = false,
                isListeningToCaller = false,
                isChimeGateMuting = false,
                callStatusText = "Call Ended • Chime-Blocker Standby"
            )
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (isActive && _characterCallState.value.isCallActive) {
                delay(1000L)
                if (_characterCallState.value.isCallActive) {
                    _characterCallState.value = _characterCallState.value.copy(
                        callDurationSeconds = _characterCallState.value.callDurationSeconds + 1
                    )
                }
            }
        }
    }

    private fun stopCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = null
    }

    fun toggleCallMute() {
        val current = _characterCallState.value
        val nextMuted = !current.isCallMuted
        if (nextMuted) {
            if (current.isListeningToCaller) {
                transcriber.stopListening()
                if (opusAudioEngine.isRecordingMic.value && !opusAudioEngine.isRecordingPaused.value) {
                    opusAudioEngine.togglePauseMicrophoneRecording()
                }
            }
            _characterCallState.value = current.copy(
                isCallMuted = true,
                isListeningToCaller = false,
                callStatusText = "CALL MUTED • Zero-Chime Gate Armed"
            )
        } else {
            if (opusAudioEngine.isRecordingMic.value && opusAudioEngine.isRecordingPaused.value) {
                opusAudioEngine.togglePauseMicrophoneRecording()
            }
            _characterCallState.value = current.copy(
                isCallMuted = false,
                callStatusText = if (current.isCallActive) {
                    "Live Call Connected • Mic Unmuted (48kHz Mono)"
                } else {
                    "Mic Ready • Chime-Blocker Armed"
                }
            )
        }
    }

    fun toggleChimeInterceptorService() {
        val isRunning = chimeInterceptorStats.value.isServiceRunning
        if (isRunning) {
            CharacterAiChimeInterceptorService.stopService(getApplication())
        } else {
            CharacterAiChimeInterceptorService.startService(getApplication())
        }
    }

    fun toggleCallerMicInCall() {
        val current = _characterCallState.value
        if (current.isListeningToCaller) {
            transcriber.stopListening()
            viewModelScope.launch {
                val recorded = if (opusAudioEngine.isRecordingMic.value) {
                    opusAudioEngine.stopMicrophoneRecording()
                } else null

                val capturedTranscript = transcriptionState.value.liveText.ifBlank {
                    "Hey ${current.characterName.substringBefore(" ")}, can you hear me clearly on the line?"
                }

                if (current.chimeBlockerEnabled) {
                    _characterCallState.value = _characterCallState.value.copy(
                        isListeningToCaller = false,
                        isChimeGateMuting = true,
                        lastCallerStatement = capturedTranscript,
                        callStatusText = "MUTING c.ai POST-SPEECH CHIME (${current.postSpeechMuteWindowMs}ms holdoff)..."
                    )
                    if (recorded != null) {
                        val cleaned = opusAudioEngine.suppressUiChimesOnBuffer(recorded)
                        if (cleaned != null) {
                            _loadedOpusAudio.value = cleaned
                        }
                    }
                    delay(current.postSpeechMuteWindowMs.toLong().coerceIn(300L, 2500L))
                }

                _characterCallState.value = _characterCallState.value.copy(
                    isListeningToCaller = false,
                    isChimeGateMuting = false,
                    lastCallerStatement = capturedTranscript,
                    callStatusText = "Character Speaking (Zero-Chime 48kHz Output)"
                )
                respondToCallerInCharacter(capturedTranscript)
            }
        } else {
            audioPlayer.pause()
            if (!_characterCallState.value.isCallActive) {
                if (_characterCallState.value.chimeBlockerEnabled) {
                    CharacterAiChimeInterceptorService.startService(getApplication())
                }
                startCallTimer()
            }
            opusAudioEngine.setAudioSource(StudioAudioSource.VOICE_COMMUNICATION)
            opusAudioEngine.startMicrophoneRecording(viewModelScope)
            transcriber.startListening()
            _characterCallState.value = current.copy(
                isCallActive = true,
                isCallMuted = false,
                isListeningToCaller = true,
                isChimeGateMuting = false,
                callStatusText = "Listening to Other Caller (48kHz Mono VOICE_COMMUNICATION)..."
            )
        }
    }

    fun toggleCallSpeakerphone() {
        val current = _characterCallState.value
        val nextSpeaker = !current.speakerphoneEnabled
        _characterCallState.value = current.copy(
            speakerphoneEnabled = nextSpeaker,
            callStatusText = if (nextSpeaker) {
                "Speakerphone Bridge Active • Acoustic Coupling Ready"
            } else {
                "Earpiece Routing Active • Direct Call Bridge"
            }
        )
    }

    fun setChimeBlockerEnabled(enabled: Boolean) {
        ChimeStreamInterceptor.updateGlobalConfig { it.copy(isEnabled = enabled) }
        if (enabled && _characterCallState.value.isCallActive) {
            CharacterAiChimeInterceptorService.startService(getApplication())
        } else if (!enabled) {
            CharacterAiChimeInterceptorService.stopService(getApplication())
        }
        _characterCallState.value = _characterCallState.value.copy(
            chimeBlockerEnabled = enabled,
            callStatusText = if (enabled) {
                "c.ai Post-Speech Chime Blocker Armed (${_characterCallState.value.postSpeechMuteWindowMs}ms)"
            } else {
                "Chime Blocker Bypassed (Unfiltered Audio)"
            }
        )
    }

    fun setPostSpeechMuteWindowMs(windowMs: Int) {
        val clamped = windowMs.coerceIn(300, 2200)
        ChimeStreamInterceptor.updateGlobalConfig { it.copy(postSpeechMuteWindowMs = clamped) }
        _characterCallState.value = _characterCallState.value.copy(
            postSpeechMuteWindowMs = clamped,
            callStatusText = "Post-Speech Chime Gate Holdoff set to ${clamped}ms"
        )
    }

    fun toggleSpectralNotchFilter() {
        val next = !_characterCallState.value.spectralNotchEnabled
        ChimeStreamInterceptor.updateGlobalConfig { it.copy(spectralNotchEnabled = next) }
        _characterCallState.value = _characterCallState.value.copy(
            spectralNotchEnabled = next
        )
    }

    fun toggleTelephonyBandpassFilter() {
        val next = !_characterCallState.value.telephonyBandpassEnabled
        ChimeStreamInterceptor.updateGlobalConfig { it.copy(telephonyBandpassEnabled = next) }
        _characterCallState.value = _characterCallState.value.copy(
            telephonyBandpassEnabled = next
        )
    }

    fun updateCharacterCallPersona(name: String? = null, persona: String? = null) {
        val current = _characterCallState.value
        _characterCallState.value = current.copy(
            characterName = name ?: current.characterName,
            characterPersona = persona ?: current.characterPersona
        )
    }

    fun armChimeBlockerAndLaunchCharacterAi(context: Context) {
        ChimeStreamInterceptor.updateGlobalConfig {
            it.copy(isEnabled = true, spectralNotchEnabled = true)
        }
        CharacterAiChimeInterceptorService.startService(context)
        _characterCallState.value = _characterCallState.value.copy(
            chimeBlockerEnabled = true,
            spectralNotchEnabled = true,
            callStatusText = "Chime-Blocker Service Armed (${_characterCallState.value.postSpeechMuteWindowMs}ms) • Launching Character.AI"
        )
        opusAudioEngine.setAudioSource(StudioAudioSource.VOICE_COMMUNICATION)
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("ai.character.app")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Toast.makeText(
                    context,
                    "Chime-Blocker Service Armed! Launching Character.AI app...",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://character.ai")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                Toast.makeText(
                    context,
                    "Chime-Blocker Service Armed! Opening Character.AI...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Chime-Blocker Service Armed in background for Character.AI calls!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun respondToCallerInCharacter(callerPromptInput: String) {
        val callState = _characterCallState.value
        val state = _uiState.value
        val cleanedCallerPrompt = callerPromptInput.trim()

        viewModelScope.launch {
            if (callState.chimeBlockerEnabled) {
                _characterCallState.value = _characterCallState.value.copy(
                    isChimeGateMuting = true,
                    lastCallerStatement = cleanedCallerPrompt,
                    callStatusText = "Suppressing c.ai Turn-End UI Chimes (${callState.postSpeechMuteWindowMs}ms)..."
                )
                delay(280L)
            }

            val characterShortName = callState.characterName.substringBefore(" (").ifBlank { "Aria" }
            val contextualReply = buildCharacterCallReply(
                characterName = characterShortName,
                callerStatement = cleanedCallerPrompt
            )

            _characterCallState.value = _characterCallState.value.copy(
                isChimeGateMuting = false,
                lastCallerStatement = cleanedCallerPrompt,
                lastCharacterReply = contextualReply,
                callStatusText = "Synthesizing $characterShortName's Zero-Chime Call Voice..."
            )

            val synthResult = synthesizer.synthesizeSingleSpeaker(
                text = contextualReply,
                voiceName = state.selectedVoice.id,
                stylePrompt = "${callState.characterPersona}. Speak naturally on a live phone call with zero sound effects.",
                speed = state.speed,
                pitch = state.pitch
            )

            if (synthResult is SynthesisResult.Success) {
                val bytes = synthResult.wavFile.readBytes()
                val pcmBytes = if (bytes.size > 44) bytes.copyOfRange(44, bytes.size) else bytes
                val rawAmplitudes = PcmWavHelper.extractAmplitudesFromPcm(pcmBytes)

                val rawLoaded = LoadedAudioBuffer(
                    sourceName = "${characterShortName}_Call_Reply",
                    wavFile = synthResult.wavFile,
                    sampleRate = 24000,
                    channels = 1,
                    durationMs = synthResult.durationMs,
                    amplitudes = rawAmplitudes,
                    totalSamples = pcmBytes.size / 2
                )

                val finalLoaded = if (callState.chimeBlockerEnabled) {
                    opusAudioEngine.suppressUiChimesOnBuffer(rawLoaded) ?: rawLoaded
                } else {
                    rawLoaded
                }

                _loadedOpusAudio.value = finalLoaded
                _trimStartFraction.value = 0f
                _trimEndFraction.value = 1f

                val callTake = VoiceTakeEntity(
                    title = "$characterShortName (c.ai Call)",
                    script = contextualReply,
                    stylePrompt = "Zero-Chime c.ai Call • ${callState.characterPersona}",
                    isMultiSpeaker = false,
                    speaker1Voice = state.selectedVoice.name,
                    speed = state.speed,
                    pitch = state.pitch,
                    durationMs = finalLoaded.durationMs,
                    filePath = finalLoaded.wavFile.absolutePath,
                    amplitudesJson = PcmWavHelper.serializeAmplitudes(finalLoaded.amplitudes),
                    isGeminiCloud = synthResult.isGeminiCloud
                )
                val insertedId = takeDao.insertTake(callTake)
                _uiState.value = _uiState.value.copy(activeTake = callTake.copy(id = insertedId))

                _characterCallState.value = _characterCallState.value.copy(
                    isChimeGateMuting = false,
                    callStatusText = "$characterShortName Speaking on Line • Zero Chimes • Ready for PTT"
                )

                audioPlayer.loadAndPlay(finalLoaded.wavFile.absolutePath, autoPlay = true)
            } else if (synthResult is SynthesisResult.Error) {
                _characterCallState.value = _characterCallState.value.copy(
                    isChimeGateMuting = false,
                    callStatusText = "Call Audio Ready • Tap Speak Reply to retry"
                )
            }
        }
    }

    private fun buildCharacterCallReply(
        characterName: String,
        callerStatement: String
    ): String {
        val lower = callerStatement.lowercase()
        return when {
            lower.contains("just connected") || lower.contains("hello") || lower.contains("hey") ->
                "Hey there! It's $characterName on the line. I can hear you loud and clear with zero background chimes—what's on your mind?"
            lower.contains("hear me") || lower.contains("line") ->
                "Yeah, I hear every word crystal clear! Go ahead and keep talking, I'm right here with you on the call."
            lower.contains("plan") || lower.contains("today") || lower.contains("schedule") ->
                "Honestly, let's keep things simple and focused today. Tell me the top thing you want to tackle first and we'll figure it out together on the call."
            lower.contains("joke") || lower.contains("funny") ->
                "Alright, here's one for the call—at least with our chime blocker running, nobody thinks I'm a microwave dinging after every sentence!"
            else ->
                "I totally hear what you're saying about \"$callerStatement\". Since it's $characterName here on the line, tell me a bit more so we can talk it through."
        }
    }

    // Export & History Methods
    fun updateExportConfig(config: AudioExportConfig) {
        _exportConfig.value = config
    }

    fun exportTakeWithConfig(context: Context, take: VoiceTakeEntity, config: AudioExportConfig) {
        viewModelScope.launch {
            val file = File(take.filePath)
            if (file.exists()) {
                val uri = PcmWavHelper.exportToPublicStorage(context, file, take.title, config)
                if (uri != null) {
                    Toast.makeText(
                        context,
                        "Exported (${config.sampleRate.label}, ${config.bitDepth.label}) to Music/VoxStudio",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playTake(take: VoiceTakeEntity) {
        _uiState.value = _uiState.value.copy(activeTake = take)
        audioPlayer.loadAndPlay(take.filePath, autoPlay = true)
    }

    fun shareTake(context: Context, take: VoiceTakeEntity, config: AudioExportConfig? = _exportConfig.value) {
        val file = File(take.filePath)
        if (file.exists()) {
            PcmWavHelper.shareAudioFile(context, file, take.title, config)
        } else {
            Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteTake(take: VoiceTakeEntity) {
        viewModelScope.launch {
            takeDao.deleteTake(take)
            File(take.filePath).delete()
            if (_uiState.value.activeTake?.id == take.id) {
                audioPlayer.pause()
                val remaining = allTakes.value.filterNot { it.id == take.id }
                val next = remaining.firstOrNull()
                _uiState.value = _uiState.value.copy(activeTake = next)
                if (next != null) {
                    audioPlayer.loadAndPlay(next.filePath, autoPlay = false)
                }
            }
        }
    }

    fun loadTakeIntoEditor(take: VoiceTakeEntity) {
        val voice = AVAILABLE_VOICES.firstOrNull { it.id == take.speaker1Voice } ?: _uiState.value.selectedVoice
        val file = File(take.filePath)
        if (file.exists()) {
            val amps = PcmWavHelper.parseAmplitudes(take.amplitudesJson)
            _loadedOpusAudio.value = LoadedAudioBuffer(
                sourceName = take.title,
                wavFile = file,
                sampleRate = OpusAudioEngine.STUDIO_SAMPLE_RATE_HZ,
                channels = 1,
                durationMs = take.durationMs,
                amplitudes = amps,
                totalSamples = ((take.durationMs * 48).toInt()).coerceAtLeast(960)
            )
            _trimStartFraction.value = 0f
            _trimEndFraction.value = 1f
        }
        _uiState.value = _uiState.value.copy(
            scriptText = take.script,
            stylePrompt = take.stylePrompt,
            selectedVoice = voice,
            speed = take.speed,
            pitch = take.pitch,
            activeTab = StudioTab.OPUS_TRIMMER
        )
    }

    // Voice Cloning Methods
    fun onAudioSampleSelected(uri: Uri) {
        _selectedCloneSampleUri.value = uri
        viewModelScope.launch {
            _isAnalyzingClone.value = true
            val report = voiceCloningEngine.analyzeAudioSample(uri)
            _cloneReport.value = report
            _isAnalyzingClone.value = false
        }
    }

    fun toggleCloneMicrophoneRecording() {
        if (opusAudioEngine.isRecordingMic.value) {
            viewModelScope.launch {
                val recorded = opusAudioEngine.stopMicrophoneRecording()
                if (recorded != null) {
                    val fileUri = Uri.fromFile(recorded.wavFile)
                    _selectedCloneSampleUri.value = fileUri
                    _isAnalyzingClone.value = true
                    val report = voiceCloningEngine.analyzeAudioSample(fileUri)
                    _cloneReport.value = report
                    _isAnalyzingClone.value = false
                    Toast.makeText(
                        getApplication(),
                        "48kHz Mono voice sample analyzed for cloning!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            audioPlayer.pause()
            opusAudioEngine.startMicrophoneRecording(viewModelScope)
        }
    }

    fun useDemoVoiceSample() {
        viewModelScope.launch {
            _isAnalyzingClone.value = true
            val report = VoiceQualityReport(
                durationSeconds = 85,
                averageRms = 0.32f,
                estimatedPitchHz = 145,
                estimatedGender = VoiceGender.MALE,
                qualityScore = 92,
                qualityAssessment = "Studio Grade • Balanced dynamic range and crystal-clear pronunciation",
                warnings = emptyList()
            )
            _cloneReport.value = report
            _isAnalyzingClone.value = false
        }
    }

    fun createVoiceClone(name: String) {
        val report = _cloneReport.value ?: return
        viewModelScope.launch {
            val dummyUri = _selectedCloneSampleUri.value ?: Uri.parse("android.resource://${getApplication<Application>().packageName}/raw/sample")
            val clone = voiceCloningEngine.createVoiceClone(name, dummyUri, report)
            val updated = _clonedVoices.value.toMutableList().apply { add(clone) }
            _clonedVoices.value = updated

            val customPreset = VoicePreset(
                id = clone.baseVoiceId,
                name = "${clone.name} (Clone)",
                gender = clone.gender,
                description = clone.styleDescription,
                iconEmoji = "🎙️",
                sampleTone = "Personalized custom voice clone"
            )
            setSelectedVoice(customPreset)
            _uiState.value = _uiState.value.copy(
                stylePrompt = "Speak with the characteristic timber, rhythm and warmth of ${clone.name}",
                pitch = clone.pitchMultiplier
            )
            updateCharacterCallPersona(
                name = "${clone.name} (c.ai Clone)",
                persona = "Speak with the characteristic timber, rhythm and warmth of ${clone.name}"
            )
            Toast.makeText(getApplication(), "Voice clone '${clone.name}' activated for c.ai Call!", Toast.LENGTH_SHORT).show()
            setTab(StudioTab.CAI_CALL)
        }
    }

    fun selectClonedVoiceForStudio(clone: ClonedVoiceProfile) {
        val customPreset = VoicePreset(
            id = clone.baseVoiceId,
            name = "${clone.name} (Clone)",
            gender = clone.gender,
            description = clone.styleDescription,
            iconEmoji = "🎙️",
            sampleTone = "Personalized custom voice clone"
        )
        setSelectedVoice(customPreset)
        _uiState.value = _uiState.value.copy(
            stylePrompt = "Speak with the characteristic timber, rhythm and warmth of ${clone.name}",
            pitch = clone.pitchMultiplier
        )
        updateCharacterCallPersona(
            name = "${clone.name} (c.ai Clone)",
            persona = "Speak with the characteristic timber, rhythm and warmth of ${clone.name}"
        )
        Toast.makeText(getApplication(), "Selected '${clone.name}' for c.ai Call & Voice Notes", Toast.LENGTH_SHORT).show()
        setTab(StudioTab.CAI_CALL)
    }

    override fun onCleared() {
        super.onCleared()
        stopCallTimer()
        CharacterAiChimeInterceptorService.stopService(getApplication())
        audioPlayer.release()
        synthesizer.shutdown()
        transcriber.stopListening()
    }
}
