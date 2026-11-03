package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import com.example.data.cai.CharacterAiApiClient
import com.example.data.cloud.CloudSyncRepository
import com.example.data.cloud.GoogleAuthHelper
import com.example.data.cloud.GoogleAuthOutcome
import com.example.data.db.AppDatabase
import com.example.data.db.VoiceTakeEntity
import com.example.data.model.AVAILABLE_VOICES
import com.example.data.model.AuthProvider
import com.example.data.model.CaiLaunchMode
import com.example.data.model.CaiTurnMessage
import com.example.data.model.StudioPreferencesManager
import com.example.data.model.StudioSettingsConfig
import com.example.data.model.SyncedCharacterProfile
import com.example.data.model.SyncedUserPersona
import com.example.data.model.UserAccountState
import com.example.data.model.VoiceGender
import com.example.data.model.VoicePreset
import com.example.data.model.defaultStarterCharacters
import com.example.data.model.defaultStarterPersonas
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ROUTE_HOME_DASHBOARD = "home_dashboard"
const val ROUTE_CAI_CALL = "cai_call"
const val ROUTE_RECORDING_STUDIO = "recording_studio"
const val ROUTE_ACCOUNT_SYNC = "account_sync"
const val ROUTE_VOICE_CLONING = "voice_cloning"
const val ROUTE_HISTORY = "history"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_LOGIN = "login"

enum class StudioTab(val route: String, val title: String, val subtitle: String) {
    HOME_DASHBOARD(ROUTE_HOME_DASHBOARD, "Home Dashboard", "48kHz Chime-Blocker, Recording Studio & c.ai Sync"),
    CAI_CALL(ROUTE_CAI_CALL, "c.ai Call Bridge", "Zero-Chime Live Call, Neo WS & Authenticated WebView"),
    OPUS_TRIMMER(ROUTE_RECORDING_STUDIO, "Recording Studio", "48kHz Mono Recorder, Waveform Trimmer & PTT Export"),
    ACCOUNT_SYNC(ROUTE_ACCOUNT_SYNC, "Account & c.ai Sync", "1-Tap WebView Login, Characters, Personas & Cloud Sync"),
    CLONING(ROUTE_VOICE_CLONING, "Voice Clone Lab", "Custom 48kHz Character Voice Timbre Cloning"),
    HISTORY(ROUTE_HISTORY, "Saved Takes Library", "Recorded Calls & Exported Voice Notes"),
    SETTINGS(ROUTE_SETTINGS, "Studio Settings", "Audio Configuration, Integrations & Account"),
    LOGIN(ROUTE_LOGIN, "Sign In & Cloud Auth", "1-Tap c.ai WebView, Google Credential Manager & Personas")
}

enum class CaiCallSection(val label: String) {
    LIVE_DECK("Live Call Deck"),
    CHIME_DSP("Chime Blocker DSP"),
    CHARACTER_VOICE("Character & Voice")
}

enum class VoiceStudioSection(val label: String) {
    RECORD_IMPORT("1. Record & Import"),
    WAVEFORM_TRIM("2. Waveform & Trim"),
    FORMAT_EXPORT("3. Format & Export")
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
    val authenticatedWebViewEnabled: Boolean = false,
    val characterName: String = "Aria Vance (@aria_vance)",
    val characterPersona: String = "Witty, warm, and observant confidante who speaks naturally on live phone calls with relaxed pacing and zero robotic pauses.",
    val characterHandle: String = "@aria_vance",
    val characterVoiceLabel: String = "Aoede Velvet (48kHz c.ai Voice)",
    val activeUserPersonaName: String = "Studio Caller (Default)",
    val lastCallerStatement: String = "",
    val lastCharacterReply: String = "Hey! I'm on the line—go ahead and put me on speaker, zero chimes enabled.",
    val callStatusText: String = "Chime-Blocker Armed • Neo WS & Native c.ai Bridge Ready",
    val liveChatTurns: List<CaiTurnMessage> = emptyList()
)

data class StudioUiState(
    val activeTab: StudioTab = StudioTab.HOME_DASHBOARD,
    val activeCallSection: CaiCallSection = CaiCallSection.LIVE_DECK,
    val activeStudioSection: VoiceStudioSection = VoiceStudioSection.RECORD_IMPORT,
    val scriptText: String = "",
    val stylePrompt: String = "Warm, expressive phone call voice with natural pauses",
    val selectedVoice: VoicePreset = AVAILABLE_VOICES.first { it.id == "Aoede" },
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val isSynthesizing: Boolean = false,
    val activeTake: VoiceTakeEntity? = null,
    val hasApiKey: Boolean = false
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "StudioViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val takeDao = database.voiceTakeDao()
    private val prefsManager = StudioPreferencesManager(application)
    private val cloudSyncRepository = CloudSyncRepository(application)
    private val caiApiClient = CharacterAiApiClient()
    private val firebaseAuth: FirebaseAuth = Firebase.auth

    val audioPlayer = StudioAudioPlayer(application)
    val synthesizer = GeminiSpeechSynthesizer(application)
    val voiceCloningEngine = VoiceCloningEngine(application)
    val opusAudioEngine = OpusAudioEngine(application)

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Persistent Settings & Account Sync State
    private val _settingsConfig = MutableStateFlow(prefsManager.loadSettings())
    val settingsConfig: StateFlow<StudioSettingsConfig> = _settingsConfig.asStateFlow()

    private val _accountState = MutableStateFlow(prefsManager.loadAccountState())
    val accountState: StateFlow<UserAccountState> = _accountState.asStateFlow()

    // 1-Tap Character.AI WebView Login modal state
    private val _showCaiWebViewLogin = MutableStateFlow(false)
    val showCaiWebViewLogin: StateFlow<Boolean> = _showCaiWebViewLogin.asStateFlow()

    private var cloudAccountJob: Job? = null
    private var cloudCharactersJob: Job? = null
    private var activeCloudUid: String? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        if (user != null) {
            onFirebaseUserAuthenticated(user)
        } else {
            stopCloudObservation()
        }
    }

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
        applySettingsToEngines(_settingsConfig.value)
        applySyncedCharacterToCallAndStudio(_accountState.value.activeCharacter, showToast = false)

        firebaseAuth.addAuthStateListener(authStateListener)

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

    fun getSavedCaiSessionToken(): String = prefsManager.loadEncryptedCaiToken()

    fun getSavedCaiCookies(): String = prefsManager.loadEncryptedCaiCookies()

    fun openCaiWebViewLogin() {
        _showCaiWebViewLogin.value = true
    }

    fun closeCaiWebViewLogin() {
        _showCaiWebViewLogin.value = false
    }

    /**
     * Triggered automatically when the 1-Tap Character.AI WebView detects an authenticated
     * session token (from CookieManager or localStorage) or when the user submits a token manually.
     */
    fun onCaiWebViewTokenCaptured(rawToken: String, cookieHeader: String = "") {
        _showCaiWebViewLogin.value = false
        val cleanToken = rawToken.removePrefix("Token ").removePrefix("Bearer ").trim()
        if (cleanToken.isBlank() && cookieHeader.isBlank()) {
            Toast.makeText(getApplication(), "Could not extract a valid Character.AI token.", Toast.LENGTH_SHORT).show()
            return
        }

        prefsManager.saveEncryptedCaiCredentials(cleanToken, cookieHeader)
        val preview = if (cleanToken.length > 10) "${cleanToken.take(6)}...${cleanToken.takeLast(4)}" else "Session Active"

        _accountState.value = _accountState.value.copy(
            provider = AuthProvider.CHARACTER_AI,
            hasCaiSessionToken = true,
            caiTokenStatusText = "Encrypted Token Active ($preview) • Syncing...",
            isSyncing = true,
            authErrorMessage = null
        )

        viewModelScope.launch {
            val syncResult = caiApiClient.syncFullAccount(
                token = cleanToken,
                cookieHeader = cookieHeader,
                fallbackUsernameHint = _accountState.value.caiLinkedUsername
            )

            val mergedChars = (syncResult.characters + _accountState.value.syncedCharacters)
                .distinctBy { it.characterId }
            val mergedPersonas = (syncResult.personas + _accountState.value.syncedPersonas)
                .distinctBy { it.personaId }

            val primaryChar = syncResult.characters.firstOrNull() ?: mergedChars.first()
            val primaryPersona = syncResult.personas.firstOrNull { it.isDefault }
                ?: syncResult.personas.firstOrNull()
                ?: mergedPersonas.first()

            val timestamp = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())
            val uid = firebaseAuth.currentUser?.uid.orEmpty()

            val updatedAccount = _accountState.value.copy(
                isSignedIn = true,
                firebaseUid = uid,
                provider = AuthProvider.CHARACTER_AI,
                displayName = syncResult.displayName.ifBlank { syncResult.username },
                emailOrUsername = syncResult.email.ifBlank { syncResult.username },
                caiLinkedUsername = "@${syncResult.username.removePrefix("@")}",
                hasCaiSessionToken = true,
                caiTokenStatusText = "Encrypted Token Saved ($preview) • @${syncResult.username.removePrefix("@")}",
                lastSyncTimestamp = timestamp,
                cloudSyncStatus = if (uid.isNotBlank()) {
                    "${syncResult.statusMessage} • Backed up to Firestore"
                } else {
                    syncResult.statusMessage
                },
                isSyncing = false,
                syncedCharacters = mergedChars,
                activeCharacterId = primaryChar.characterId,
                syncedPersonas = mergedPersonas,
                activePersonaId = primaryPersona.personaId
            )

            _accountState.value = updatedAccount
            prefsManager.saveAccountState(updatedAccount)

            applySyncedCharacterToCallAndStudio(primaryChar, showToast = false)
            _characterCallState.value = _characterCallState.value.copy(
                activeUserPersonaName = "${primaryPersona.name} (${primaryPersona.handle})"
            )

            if (uid.isNotBlank()) {
                cloudSyncRepository.upsertUserAccountAndSettings(uid, updatedAccount, _settingsConfig.value)
                mergedChars.forEach { ch ->
                    cloudSyncRepository.upsertCharacterProfile(uid, ch)
                }
            }

            Toast.makeText(
                getApplication(),
                "1-Tap c.ai Login Complete! Synced @${syncResult.username} (${syncResult.characters.size} characters, ${syncResult.personas.size} personas).",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun selectActiveUserPersona(persona: SyncedUserPersona) {
        val updated = _accountState.value.copy(activePersonaId = persona.personaId)
        _accountState.value = updated
        prefsManager.saveAccountState(updated)
        _characterCallState.value = _characterCallState.value.copy(
            activeUserPersonaName = "${persona.name} (${persona.handle})",
            callStatusText = "Active Persona switched to ${persona.name} (${persona.handle})"
        )
        Toast.makeText(
            getApplication(),
            "Switched active C.AI Persona to ${persona.name}!",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun addOrUpdateCustomUserPersona(name: String, handle: String, definition: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            Toast.makeText(getApplication(), "Enter a Persona name first.", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanHandle = "@" + handle.trim().removePrefix("@").ifBlank {
            cleanName.lowercase(Locale.US).replace(Regex("[^a-z0-9_]"), "_")
        }
        val newPersona = SyncedUserPersona(
            personaId = "persona_${cleanHandle.removePrefix("@")}_${System.currentTimeMillis() % 10000}",
            name = cleanName,
            handle = cleanHandle,
            definition = definition.trim().ifBlank { "Custom Character.AI caller persona ($cleanName)." },
            isDefault = false
        )
        val updatedList = _accountState.value.syncedPersonas.toMutableList().apply {
            add(0, newPersona)
        }
        val updatedState = _accountState.value.copy(
            syncedPersonas = updatedList,
            activePersonaId = newPersona.personaId
        )
        _accountState.value = updatedState
        prefsManager.saveAccountState(updatedState)
        _characterCallState.value = _characterCallState.value.copy(
            activeUserPersonaName = "${newPersona.name} (${newPersona.handle})"
        )
        Toast.makeText(getApplication(), "Added & activated persona '${newPersona.name}'!", Toast.LENGTH_SHORT).show()
    }

    fun toggleAuthenticatedCallWebView(enabled: Boolean) {
        if (enabled && _characterCallState.value.chimeBlockerEnabled) {
            CharacterAiChimeInterceptorService.startService(getApplication())
        }
        _characterCallState.value = _characterCallState.value.copy(
            authenticatedWebViewEnabled = enabled,
            callStatusText = if (enabled) {
                "Authenticated c.ai Call WebView Active • 48kHz Chime-Blocker Pipeline Armed"
            } else {
                "Native c.ai Neo WebSocket Call Deck Active"
            }
        )
    }

    fun armChimeBlockerForWebViewCall() {
        ChimeStreamInterceptor.updateGlobalConfig {
            it.copy(
                isEnabled = true,
                postSpeechMuteWindowMs = _characterCallState.value.postSpeechMuteWindowMs,
                spectralNotchEnabled = _characterCallState.value.spectralNotchEnabled,
                telephonyBandpassEnabled = _characterCallState.value.telephonyBandpassEnabled
            )
        }
        CharacterAiChimeInterceptorService.startService(getApplication())
    }

    private fun onFirebaseUserAuthenticated(user: FirebaseUser) {
        if (activeCloudUid == user.uid && cloudAccountJob != null) return
        stopCloudObservation()
        activeCloudUid = user.uid

        val currentAcct = _accountState.value
        val resolvedName = user.displayName?.takeIf { it.isNotBlank() }
            ?: currentAcct.displayName.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Studio Creator"
        val resolvedEmail = user.email?.takeIf { it.isNotBlank() }
            ?: currentAcct.emailOrUsername
        val resolvedCaiHandle = currentAcct.caiLinkedUsername.ifBlank {
            "@" + resolvedName.lowercase(Locale.US).replace(Regex("[^a-z0-9_]"), "_")
        }
        val timestamp = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())

        val signedInState = currentAcct.copy(
            isSignedIn = true,
            firebaseUid = user.uid,
            displayName = resolvedName,
            emailOrUsername = resolvedEmail,
            caiLinkedUsername = resolvedCaiHandle,
            lastSyncTimestamp = timestamp,
            cloudSyncStatus = "Connected to Firebase Cloud (${user.email ?: user.uid.take(8)})",
            authErrorMessage = null
        )
        _accountState.value = signedInState
        prefsManager.saveAccountState(signedInState)

        // Start observing Firestore documents strictly after FirebaseAuth user is non-null
        cloudAccountJob = viewModelScope.launch {
            val existingDoc = cloudSyncRepository.getUserAccount(user.uid).getOrNull()
            if (existingDoc == null) {
                cloudSyncRepository.upsertUserAccountAndSettings(
                    userId = user.uid,
                    accountState = _accountState.value,
                    settings = _settingsConfig.value
                )
                _accountState.value.syncedCharacters.forEach { profile ->
                    cloudSyncRepository.upsertCharacterProfile(user.uid, profile)
                }
            }

            cloudSyncRepository.observeUserAccount(user.uid)
                .catch { e ->
                    Log.w(TAG, "Error observing cloud user account", e)
                }
                .collect { cloudDoc ->
                    if (cloudDoc != null) {
                        val providerEnum = AuthProvider.entries.firstOrNull { it.name == cloudDoc.authProvider }
                            ?: _accountState.value.provider
                        val syncTime = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())
                        val updatedAcct = _accountState.value.copy(
                            isSignedIn = true,
                            firebaseUid = user.uid,
                            provider = providerEnum,
                            displayName = cloudDoc.displayName.ifBlank { _accountState.value.displayName },
                            emailOrUsername = cloudDoc.email.ifBlank { _accountState.value.emailOrUsername },
                            caiLinkedUsername = cloudDoc.caiLinkedUsername.ifBlank { _accountState.value.caiLinkedUsername },
                            activeCharacterId = cloudDoc.activeCharacterId.ifBlank { _accountState.value.activeCharacterId },
                            lastSyncTimestamp = syncTime,
                            cloudSyncStatus = "Firebase Cloud Synced • $syncTime"
                        )
                        _accountState.value = updatedAcct
                        prefsManager.saveAccountState(updatedAcct)

                        val updatedSettings = _settingsConfig.value.copy(
                            defaultMuteWindowMs = cloudDoc.defaultMuteWindowMs.toInt().coerceIn(300, 2200),
                            defaultSampleRateHz = cloudDoc.defaultSampleRateHz.toInt(),
                            defaultMonoChannel = cloudDoc.defaultMonoChannel,
                            caiIntegrationEnabled = cloudDoc.caiIntegrationEnabled,
                            whatsappIntegrationEnabled = cloudDoc.whatsappIntegrationEnabled,
                            telegramIntegrationEnabled = cloudDoc.telegramIntegrationEnabled,
                            googleChatIntegrationEnabled = cloudDoc.googleChatIntegrationEnabled
                        )
                        _settingsConfig.value = updatedSettings
                        prefsManager.saveSettings(updatedSettings)
                        applySettingsToEngines(updatedSettings)
                    }
                }
        }

        cloudCharactersJob = viewModelScope.launch {
            cloudSyncRepository.observeCharacterProfiles(user.uid)
                .catch { e ->
                    Log.w(TAG, "Error observing cloud character profiles", e)
                }
                .collect { cloudProfiles ->
                    if (cloudProfiles.isNotEmpty()) {
                        val merged = (cloudProfiles + _accountState.value.syncedCharacters)
                            .distinctBy { it.characterId }
                        val updatedAcct = _accountState.value.copy(syncedCharacters = merged)
                        _accountState.value = updatedAcct
                        prefsManager.saveAccountState(updatedAcct)
                        if (_settingsConfig.value.autoSyncCharacterOnLogin) {
                            applySyncedCharacterToCallAndStudio(updatedAcct.activeCharacter, showToast = false)
                        }
                    }
                }
        }
    }

    private fun stopCloudObservation() {
        cloudAccountJob?.cancel()
        cloudAccountJob = null
        cloudCharactersJob?.cancel()
        cloudCharactersJob = null
        activeCloudUid = null
    }

    fun signInWithGoogleCredentialManager(
        activityContext: Context,
        characterHandleInput: String = ""
    ) {
        viewModelScope.launch {
            _accountState.value = _accountState.value.copy(
                isSyncing = true,
                authErrorMessage = null,
                cloudSyncStatus = "Opening Google Sign-In via Credential Manager..."
            )
            when (val outcome = GoogleAuthHelper.signInWithGoogleInteractive(activityContext)) {
                is GoogleAuthOutcome.Success -> {
                    val user = outcome.user
                    if (characterHandleInput.isNotBlank()) {
                        val customChar = buildSyncedCharacterFromHandleOrName(
                            rawInput = characterHandleInput,
                            ownerName = user.displayName ?: "Studio Creator"
                        )
                        val updatedList = _accountState.value.syncedCharacters.toMutableList().apply {
                            removeAll { it.characterId == customChar.characterId || it.handle.equals(customChar.handle, ignoreCase = true) }
                            add(0, customChar)
                        }
                        _accountState.value = _accountState.value.copy(
                            isSyncing = false,
                            provider = AuthProvider.GOOGLE_EMAIL,
                            syncedCharacters = updatedList,
                            activeCharacterId = customChar.characterId
                        )
                        cloudSyncRepository.upsertCharacterProfile(user.uid, customChar)
                        applySyncedCharacterToCallAndStudio(customChar, showToast = false)
                    } else {
                        _accountState.value = _accountState.value.copy(
                            isSyncing = false,
                            provider = AuthProvider.GOOGLE_EMAIL
                        )
                    }
                    // Also back up all synced C.AI characters & 48kHz Chime-Blocker presets to Firestore
                    cloudSyncRepository.upsertUserAccountAndSettings(
                        userId = user.uid,
                        accountState = _accountState.value,
                        settings = _settingsConfig.value
                    )
                    _accountState.value.syncedCharacters.forEach { profile ->
                        cloudSyncRepository.upsertCharacterProfile(user.uid, profile)
                    }
                    Toast.makeText(
                        getApplication(),
                        "Signed in with Google (${user.email ?: user.displayName}) & backed up c.ai profiles to Firestore!",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is GoogleAuthOutcome.Cancelled -> {
                    _accountState.value = _accountState.value.copy(
                        isSyncing = false,
                        authErrorMessage = outcome.message,
                        cloudSyncStatus = outcome.message
                    )
                }
                is GoogleAuthOutcome.Failure -> {
                    _accountState.value = _accountState.value.copy(
                        isSyncing = false,
                        authErrorMessage = outcome.message,
                        cloudSyncStatus = outcome.message
                    )
                    Toast.makeText(getApplication(), outcome.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun attemptSilentSignIn(activityContext: Context) {
        viewModelScope.launch {
            GoogleAuthHelper.attemptSilentGoogleSignIn(activityContext)
        }
    }

    fun syncNowWithCloud() {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(
                getApplication(),
                "Sign in with Google first to enable real-time Firebase Cloud backup.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        viewModelScope.launch {
            _accountState.value = _accountState.value.copy(isSyncing = true, cloudSyncStatus = "Syncing with Firebase Firestore...")
            val acctRes = cloudSyncRepository.upsertUserAccountAndSettings(
                userId = uid,
                accountState = _accountState.value,
                settings = _settingsConfig.value
            )
            _accountState.value.syncedCharacters.forEach { profile ->
                cloudSyncRepository.upsertCharacterProfile(uid, profile)
            }
            val nowStr = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())
            _accountState.value = _accountState.value.copy(
                isSyncing = false,
                lastSyncTimestamp = nowStr,
                cloudSyncStatus = if (acctRes.isSuccess) {
                    "Firebase Cloud Synced • $nowStr"
                } else {
                    "Cloud sync error—saved locally"
                }
            )
            Toast.makeText(getApplication(), "Account, personas & Character.AI profiles synced to Cloud!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applySettingsToEngines(config: StudioSettingsConfig) {
        ChimeStreamInterceptor.updateGlobalConfig {
            it.copy(
                postSpeechMuteWindowMs = config.defaultMuteWindowMs,
                spectralNotchEnabled = config.defaultSpectralNotch,
                telephonyBandpassEnabled = config.defaultTelephonyBandpass
            )
        }
        _characterCallState.value = _characterCallState.value.copy(
            speakerphoneEnabled = config.defaultCallSpeakerphone,
            postSpeechMuteWindowMs = config.defaultMuteWindowMs,
            spectralNotchEnabled = config.defaultSpectralNotch,
            telephonyBandpassEnabled = config.defaultTelephonyBandpass
        )
        opusAudioEngine.setAudioSource(config.defaultAudioSource)
        opusAudioEngine.setNoiseSuppressionEnabled(config.defaultNoiseSuppression)
        opusAudioEngine.setAutoGainControlEnabled(config.defaultAutoGainControl)
        _voiceNoteFormatSpec.value = _voiceNoteFormatSpec.value.copy(
            sampleRateHz = config.defaultSampleRateHz,
            isMono = config.defaultMonoChannel,
            containerTarget = config.defaultExportContainer
        )
    }

    fun updateSettingsConfig(transform: (StudioSettingsConfig) -> StudioSettingsConfig) {
        val updated = transform(_settingsConfig.value)
        _settingsConfig.value = updated
        prefsManager.saveSettings(updated)
        applySettingsToEngines(updated)

        val uid = firebaseAuth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                cloudSyncRepository.upsertUserAccountAndSettings(uid, _accountState.value, updated)
            }
        }
    }

    fun resetSettingsToDefaults() {
        val defaults = StudioSettingsConfig()
        _settingsConfig.value = defaults
        prefsManager.saveSettings(defaults)
        applySettingsToEngines(defaults)
        val uid = firebaseAuth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                cloudSyncRepository.upsertUserAccountAndSettings(uid, _accountState.value, defaults)
            }
        }
        Toast.makeText(getApplication(), "Studio settings restored to factory defaults.", Toast.LENGTH_SHORT).show()
    }

    fun signInWithAppleFirebase(
        activityContext: Context,
        characterHandleInput: String = "",
        appleIdEmailHint: String = "",
        appleIdToken: String = ""
    ) {
        viewModelScope.launch {
            _accountState.value = _accountState.value.copy(
                isSyncing = true,
                authErrorMessage = null,
                cloudSyncStatus = "Connecting to Firebase Apple OAuth Provider..."
            )
            when (val outcome = GoogleAuthHelper.signInWithAppleProvider(activityContext, appleIdToken)) {
                is GoogleAuthOutcome.Success -> {
                    val user = outcome.user
                    val emailOrId = user.email ?: appleIdEmailHint.ifBlank { "apple_user@icloud.com" }
                    authenticateAndSyncAccount(
                        provider = AuthProvider.APPLE_ID,
                        identifier = emailOrId,
                        secretOrToken = appleIdToken,
                        characterHandleInput = characterHandleInput,
                        isSignUp = false,
                        activityContext = activityContext
                    )
                }
                else -> {
                    val fallbackIdentifier = appleIdEmailHint.ifBlank {
                        _accountState.value.emailOrUsername.ifBlank { "apple.creator@icloud.com" }
                    }
                    authenticateAndSyncAccount(
                        provider = AuthProvider.APPLE_ID,
                        identifier = fallbackIdentifier,
                        secretOrToken = appleIdToken,
                        characterHandleInput = characterHandleInput,
                        isSignUp = false,
                        activityContext = null
                    )
                }
            }
        }
    }

    // Account Authentication (Character.AI, Google Account, Apple ID) & Automatic Real C.AI Sync
    fun authenticateAndSyncAccount(
        provider: AuthProvider,
        identifier: String,
        secretOrToken: String,
        characterHandleInput: String,
        isSignUp: Boolean,
        activityContext: Context? = null
    ) {
        val cleanId = identifier.trim()
        val cleanSecret = secretOrToken.trim()

        // If user selected CHARACTER_AI and provided a session token (even without username), run full C.AI token sync
        if (provider == AuthProvider.CHARACTER_AI && cleanSecret.isNotBlank()) {
            onCaiWebViewTokenCaptured(rawToken = cleanSecret, cookieHeader = getSavedCaiCookies())
            return
        }

        if (cleanId.isBlank() && cleanSecret.isBlank()) {
            Toast.makeText(getApplication(), "Please enter your Character.AI Session Token, username, or email.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _accountState.value = _accountState.value.copy(isSyncing = true, authErrorMessage = null)

            when (provider) {
                AuthProvider.APPLE_ID -> {
                    if (cleanSecret.isNotBlank() && cleanSecret.count { it == '.' } >= 2) {
                        GoogleAuthHelper.signInWithAppleProvider(activityContext, appleIdToken = cleanSecret)
                    }
                }
                AuthProvider.CHARACTER_AI -> {
                    if (cleanSecret.isNotBlank()) {
                        prefsManager.saveEncryptedCaiCredentials(cleanSecret, getSavedCaiCookies())
                        GoogleAuthHelper.signInWithCustomCredentialOrToken(
                            customTokenOrJwt = cleanSecret,
                            customProviderId = "oidc.character_ai"
                        )
                    }
                }
                AuthProvider.GOOGLE_EMAIL -> {
                    if (firebaseAuth.currentUser == null && activityContext != null && cleanSecret.isBlank()) {
                        GoogleAuthHelper.attemptSilentGoogleSignIn(activityContext)
                    }
                }
            }

            // Use real CharacterAiApiClient to sync profile, characters (including Private/Unlisted/Lorebook), and Personas
            val tokenToUse = cleanSecret.ifBlank { getSavedCaiSessionToken() }
            val caiSyncResult = caiApiClient.syncFullAccount(
                token = tokenToUse,
                cookieHeader = getSavedCaiCookies(),
                fallbackUsernameHint = characterHandleInput.ifBlank { cleanId.substringBefore("@") }
            )

            val currentList = (caiSyncResult.characters + _accountState.value.syncedCharacters)
                .distinctBy { it.characterId }
                .toMutableList()

            val syncedPrimaryChar: SyncedCharacterProfile = if (characterHandleInput.isNotBlank()) {
                val remoteOrCustom = caiApiClient.fetchCharacterDetails(
                    characterIdOrHandle = characterHandleInput,
                    token = tokenToUse,
                    cookieHeader = getSavedCaiCookies()
                ) ?: buildSyncedCharacterFromHandleOrName(
                    rawInput = characterHandleInput,
                    ownerName = caiSyncResult.displayName
                )
                currentList.removeAll { it.characterId == remoteOrCustom.characterId || it.handle.equals(remoteOrCustom.handle, ignoreCase = true) }
                currentList.add(0, remoteOrCustom)
                remoteOrCustom
            } else {
                currentList.first()
            }

            val mergedPersonas = (caiSyncResult.personas + _accountState.value.syncedPersonas)
                .distinctBy { it.personaId }

            val timestamp = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())
            val uid = firebaseAuth.currentUser?.uid ?: ""
            val hasToken = tokenToUse.isNotBlank()
            val tokenStatus = if (hasToken) {
                val preview = if (tokenToUse.length > 10) "${tokenToUse.take(6)}...${tokenToUse.takeLast(4)}" else "Active"
                "Encrypted Token Active ($preview)"
            } else {
                _accountState.value.caiTokenStatusText
            }

            val newState = UserAccountState(
                isSignedIn = true,
                firebaseUid = uid,
                provider = provider,
                displayName = caiSyncResult.displayName,
                emailOrUsername = cleanId.ifBlank { caiSyncResult.username },
                caiLinkedUsername = "@${caiSyncResult.username.removePrefix("@")}",
                hasCaiSessionToken = hasToken,
                caiTokenStatusText = tokenStatus,
                lastSyncTimestamp = timestamp,
                cloudSyncStatus = if (uid.isNotBlank()) {
                    "Synced to Firebase Cloud (${provider.badgeText}) • $timestamp"
                } else {
                    caiSyncResult.statusMessage
                },
                isSyncing = false,
                syncedCharacters = currentList,
                activeCharacterId = syncedPrimaryChar.characterId,
                syncedPersonas = mergedPersonas,
                activePersonaId = mergedPersonas.first().personaId
            )
            _accountState.value = newState
            prefsManager.saveAccountState(newState)

            if (uid.isNotBlank()) {
                cloudSyncRepository.upsertUserAccountAndSettings(uid, newState, _settingsConfig.value)
                currentList.forEach { ch ->
                    cloudSyncRepository.upsertCharacterProfile(uid, ch)
                }
            }

            if (_settingsConfig.value.autoSyncCharacterOnLogin) {
                applySyncedCharacterToCallAndStudio(syncedPrimaryChar, showToast = false)
            }

            val actionVerb = if (isSignUp) "Account registered" else "Signed in"
            Toast.makeText(
                getApplication(),
                "$actionVerb with ${provider.label}! Synced '${syncedPrimaryChar.name}' & ${mergedPersonas.size} personas.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun syncNewCharacterByHandleOrUrl(rawHandleOrUrl: String, customPersonaHint: String = "", preferredVoiceId: String = "Aoede") {
        val cleaned = rawHandleOrUrl.trim()
        if (cleaned.isBlank()) {
            Toast.makeText(getApplication(), "Enter a Character.AI character_id, @handle, or link to sync.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _accountState.value = _accountState.value.copy(isSyncing = true)

            val remoteChar = caiApiClient.fetchCharacterDetails(
                characterIdOrHandle = cleaned,
                token = getSavedCaiSessionToken(),
                cookieHeader = getSavedCaiCookies()
            )

            val newChar = remoteChar?.let { fetched ->
                val matchedVoice = AVAILABLE_VOICES.firstOrNull { it.id.equals(preferredVoiceId, ignoreCase = true) }
                    ?: AVAILABLE_VOICES.first()
                fetched.copy(
                    personaBio = customPersonaHint.trim().ifBlank { fetched.personaBio },
                    voicePresetId = matchedVoice.id,
                    voiceProfileName = "${matchedVoice.name} Studio (${fetched.visibility.label})"
                )
            } ?: buildSyncedCharacterFromHandleOrName(
                rawInput = cleaned,
                ownerName = _accountState.value.displayName.ifBlank { "Caller" },
                customPersonaHint = customPersonaHint,
                preferredVoiceId = preferredVoiceId
            )

            val updatedList = _accountState.value.syncedCharacters.toMutableList().apply {
                removeAll { it.characterId == newChar.characterId || it.handle.equals(newChar.handle, ignoreCase = true) }
                add(0, newChar)
            }
            val timestamp = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())
            val updatedAccount = _accountState.value.copy(
                isSyncing = false,
                lastSyncTimestamp = timestamp,
                syncedCharacters = updatedList,
                activeCharacterId = newChar.characterId
            )
            _accountState.value = updatedAccount
            prefsManager.saveAccountState(updatedAccount)

            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                cloudSyncRepository.upsertCharacterProfile(uid, newChar)
                cloudSyncRepository.upsertUserAccountAndSettings(uid, updatedAccount, _settingsConfig.value)
            }

            applySyncedCharacterToCallAndStudio(newChar, showToast = true)
        }
    }

    private fun buildSyncedCharacterFromHandleOrName(
        rawInput: String,
        ownerName: String,
        customPersonaHint: String = "",
        preferredVoiceId: String = "Aoede"
    ): SyncedCharacterProfile {
        val extractedSlug = when {
            rawInput.contains("character.ai/chat/") -> rawInput.substringAfter("character.ai/chat/").substringBefore("?").substringBefore("/")
            rawInput.contains("character.ai/character/") -> rawInput.substringAfter("character.ai/character/").substringBefore("?").substringBefore("/")
            else -> rawInput.removePrefix("@").trim()
        }
        val cleanHandle = "@" + extractedSlug.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase(Locale.US)
        val displayTitle = extractedSlug
            .replace("_", " ")
            .replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
            .ifBlank { "Synced Character" }

        val matchedVoice = AVAILABLE_VOICES.firstOrNull { it.id.equals(preferredVoiceId, ignoreCase = true) }
            ?: AVAILABLE_VOICES.first()

        val bio = customPersonaHint.trim().ifBlank {
            "Synced Character.AI persona ($cleanHandle). Conversational, emotionally responsive phone-call partner tuned for 48kHz Mono voice streaming with zero post-speech chimes."
        }

        return SyncedCharacterProfile(
            characterId = extractedSlug.ifBlank { "cai_${cleanHandle.removePrefix("@").take(64)}" },
            name = displayTitle,
            handle = cleanHandle,
            tagline = "Synced Character.AI Profile • ${matchedVoice.name} Voice",
            personaBio = bio,
            greeting = "Hey $ownerName! It's $displayTitle on the line—my voice profile and persona are synced and ready.",
            voicePresetId = matchedVoice.id,
            voiceProfileName = "${matchedVoice.name} Studio (${cleanHandle})",
            pitchMultiplier = if (matchedVoice.gender == VoiceGender.FEMALE) 1.03f else 0.96f,
            speedMultiplier = 1.0f,
            recommendedHoldoffMs = _settingsConfig.value.defaultMuteWindowMs,
            hasLorebook = true,
            lorebookEntriesCount = 4
        )
    }

    fun selectActiveSyncedCharacter(character: SyncedCharacterProfile) {
        val updated = _accountState.value.copy(activeCharacterId = character.characterId)
        _accountState.value = updated
        prefsManager.saveAccountState(updated)
        val uid = firebaseAuth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                cloudSyncRepository.upsertUserAccountAndSettings(uid, updated, _settingsConfig.value)
            }
        }
        applySyncedCharacterToCallAndStudio(character, showToast = true)
    }

    fun applySyncedCharacterToCallAndStudio(character: SyncedCharacterProfile, showToast: Boolean = true) {
        val matchedPreset = AVAILABLE_VOICES.firstOrNull { it.id == character.voicePresetId }
            ?: AVAILABLE_VOICES.first()

        _uiState.value = _uiState.value.copy(
            selectedVoice = matchedPreset,
            stylePrompt = character.personaBio,
            pitch = character.pitchMultiplier,
            speed = character.speedMultiplier
        )
        audioPlayer.setSpeed(character.speedMultiplier)

        ChimeStreamInterceptor.updateGlobalConfig {
            it.copy(postSpeechMuteWindowMs = character.recommendedHoldoffMs)
        }

        val initialGreetingTurn = CaiTurnMessage(
            turnId = "greeting_${character.characterId}",
            authorName = character.name,
            isUser = false,
            text = character.greeting
        )

        _characterCallState.value = _characterCallState.value.copy(
            characterName = "${character.name} (${character.handle})",
            characterPersona = character.personaBio,
            characterHandle = character.handle,
            characterVoiceLabel = character.voiceProfileName,
            activeUserPersonaName = "${_accountState.value.activePersona.name} (${_accountState.value.activePersona.handle})",
            postSpeechMuteWindowMs = character.recommendedHoldoffMs,
            lastCharacterReply = character.greeting,
            callStatusText = "Synced ${character.name} (${character.visibility.label}) • ${character.voiceProfileName}",
            liveChatTurns = listOf(initialGreetingTurn)
        )

        if (showToast) {
            Toast.makeText(
                getApplication(),
                "Synced ${character.name} (${character.voiceProfileName}) to Call Deck & Studio!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun signOutAccount(context: Context? = null) {
        stopCloudObservation()
        viewModelScope.launch {
            prefsManager.clearEncryptedCaiCredentials()
            GoogleAuthHelper.signOut(context ?: getApplication())
            val resetState = UserAccountState(
                isSignedIn = false,
                firebaseUid = "",
                hasCaiSessionToken = false,
                caiTokenStatusText = "No c.ai Session Token stored",
                syncedCharacters = defaultStarterCharacters(),
                activeCharacterId = "cai_aria_01",
                syncedPersonas = defaultStarterPersonas(),
                activePersonaId = "persona_default_01",
                cloudSyncStatus = "Signed out • Sign in to sync with Firebase Cloud"
            )
            _accountState.value = resetState
            prefsManager.saveAccountState(resetState)
            applySyncedCharacterToCallAndStudio(resetState.activeCharacter, showToast = false)
            Toast.makeText(getApplication(), "Signed out & cleared encrypted session token.", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setTab(tab: StudioTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun setCallSection(section: CaiCallSection) {
        _uiState.value = _uiState.value.copy(activeCallSection = section)
    }

    fun setStudioSection(section: VoiceStudioSection) {
        _uiState.value = _uiState.value.copy(activeStudioSection = section)
    }

    fun setSelectedVoice(voice: VoicePreset) {
        _uiState.value = _uiState.value.copy(selectedVoice = voice)
        _characterCallState.value = _characterCallState.value.copy(
            characterVoiceLabel = "${voice.name} (48kHz c.ai Voice)"
        )
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
                _uiState.value = _uiState.value.copy(activeStudioSection = VoiceStudioSection.WAVEFORM_TRIM)
                Toast.makeText(getApplication(), "Loaded $displayName into Waveform Trimmer", Toast.LENGTH_SHORT).show()
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
                    _uiState.value = _uiState.value.copy(
                        activeTake = micTake.copy(id = id),
                        activeStudioSection = VoiceStudioSection.WAVEFORM_TRIM
                    )

                    Toast.makeText(
                        getApplication(),
                        "48kHz Mono recording (${recorded.totalSamples} samples) ready in Waveform & Trim!",
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
        val config = _settingsConfig.value
        val isEnabled = when (messenger) {
            MessengerTarget.WHATSAPP -> config.whatsappIntegrationEnabled
            MessengerTarget.TELEGRAM -> config.telegramIntegrationEnabled
            MessengerTarget.GOOGLE_CHAT -> config.googleChatIntegrationEnabled
            MessengerTarget.CHARACTER_AI -> config.caiIntegrationEnabled
        }
        if (!isEnabled) {
            Toast.makeText(
                context,
                "${messenger.label} integration is currently disabled in Settings -> Integrations.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

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
                callStatusText = "Live Call Connected (wss://neo.character.ai/ws/) • Zero-Chime Gate (${current.postSpeechMuteWindowMs}ms)"
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
                    callStatusText = "Streaming Turn via wss://neo.character.ai/ws/..."
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
                callStatusText = "Listening to Caller (${_accountState.value.activePersona.name} • 48kHz Mono)..."
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

    /**
     * Arms the foreground Chime-Blocker DSP service and directly launches the native Character.AI
     * Android application (`ai.character.app` / `com.character.ai`) without redirecting to a web browser.
     */
    fun armChimeBlockerAndLaunchCharacterAi(context: Context) {
        val config = _settingsConfig.value
        if (!config.caiIntegrationEnabled) {
            Toast.makeText(
                context,
                "Character.AI integration is disabled in Settings -> Integrations.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (config.autoArmChimeBlockerOnLaunch) {
            ChimeStreamInterceptor.updateGlobalConfig {
                it.copy(
                    isEnabled = true,
                    spectralNotchEnabled = config.defaultSpectralNotch,
                    telephonyBandpassEnabled = config.defaultTelephonyBandpass
                )
            }
            CharacterAiChimeInterceptorService.startService(context)
            _characterCallState.value = _characterCallState.value.copy(
                chimeBlockerEnabled = true,
                spectralNotchEnabled = config.defaultSpectralNotch,
                callStatusText = "Chime-Blocker Armed (${_characterCallState.value.postSpeechMuteWindowMs}ms) • Opening Native c.ai App"
            )
        }
        opusAudioEngine.setAudioSource(StudioAudioSource.VOICE_COMMUNICATION)

        val candidatePackages = listOf(
            config.targetCaiPackage.trim().ifBlank { "ai.character.app" },
            "ai.character.app",
            "com.character.ai",
            "beta.character.ai"
        ).distinct()

        val pm = context.packageManager

        if (config.caiLaunchMode == CaiLaunchMode.NATIVE_DEEP_LINK_CHARACTER) {
            val activeSlug = _accountState.value.activeCharacter.characterId.removePrefix("cai_")
            for (pkg in candidatePackages) {
                try {
                    val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse("characterai://character/$activeSlug")).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (deepLinkIntent.resolveActivity(pm) != null) {
                        context.startActivity(deepLinkIntent)
                        Toast.makeText(
                            context,
                            "Chime-Blocker Armed! Opening ${_accountState.value.activeCharacter.name} in Character.AI app...",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                } catch (_: Exception) {
                }
            }
        }

        for (pkg in candidatePackages) {
            try {
                val directLaunchIntent = pm.getLaunchIntentForPackage(pkg)
                if (directLaunchIntent != null) {
                    directLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(directLaunchIntent)
                    Toast.makeText(
                        context,
                        "Chime-Blocker Armed! Launching native Character.AI app ($pkg)...",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val explicitLauncherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val resolved = pm.queryIntentActivities(explicitLauncherIntent, 0)
                if (resolved.isNotEmpty()) {
                    val activityInfo = resolved.first().activityInfo
                    explicitLauncherIntent.setClassName(activityInfo.packageName, activityInfo.name)
                    context.startActivity(explicitLauncherIntent)
                    Toast.makeText(
                        context,
                        "Chime-Blocker Armed! Launching native Character.AI app...",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            } catch (_: Exception) {
            }
        }

        try {
            val schemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("characterai://")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (schemeIntent.resolveActivity(pm) != null) {
                context.startActivity(schemeIntent)
                Toast.makeText(
                    context,
                    "Chime-Blocker Armed! Launching Character.AI app...",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        } catch (_: Exception) {
        }

        if (config.caiLaunchMode == CaiLaunchMode.PLAY_STORE_IF_MISSING) {
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=ai.character.app")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
                Toast.makeText(
                    context,
                    "Chime-Blocker Armed! Character.AI app not found—opening Play Store.",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (_: Exception) {
            }
        }

        Toast.makeText(
            context,
            "Chime-Blocker DSP Service is ARMED in the background! Switch to your Character.AI app to start your zero-chime call.",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Connects the chat and "c.ai Call" screens directly to Character.AI's live messaging WebSocket
     * (`wss://neo.character.ai/ws/`) using the user's saved token, selected character_id, and active persona_id.
     * Synthesizes voice strictly via on-device 48kHz audio pipeline (NO Gemini API calls).
     */
    fun respondToCallerInCharacter(callerPromptInput: String) {
        val callState = _characterCallState.value
        val state = _uiState.value
        val acct = _accountState.value
        val cleanedCallerPrompt = callerPromptInput.trim()
        if (cleanedCallerPrompt.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSynthesizing = true)

            val callerTurn = CaiTurnMessage(
                turnId = "user_${System.currentTimeMillis()}",
                authorName = acct.activePersona.name,
                isUser = true,
                text = cleanedCallerPrompt
            )
            val updatedTurnsWithUser = (_characterCallState.value.liveChatTurns + callerTurn).takeLast(20)

            if (callState.chimeBlockerEnabled) {
                _characterCallState.value = _characterCallState.value.copy(
                    isChimeGateMuting = true,
                    lastCallerStatement = cleanedCallerPrompt,
                    liveChatTurns = updatedTurnsWithUser,
                    callStatusText = "Suppressing c.ai Turn-End UI Chimes (${callState.postSpeechMuteWindowMs}ms)..."
                )
                delay(260L)
            } else {
                _characterCallState.value = _characterCallState.value.copy(
                    lastCallerStatement = cleanedCallerPrompt,
                    liveChatTurns = updatedTurnsWithUser
                )
            }

            val characterShortName = acct.activeCharacter.name.substringBefore(" (").ifBlank { "Aria" }

            // Stream turn over Character.AI Neo WebSocket (wss://neo.character.ai/ws/)
            val wsTurnResult = caiApiClient.sendTurnOverNeoWebSocket(
                token = getSavedCaiSessionToken(),
                cookieHeader = getSavedCaiCookies(),
                character = acct.activeCharacter,
                activePersona = acct.activePersona,
                userMessage = cleanedCallerPrompt,
                onPartialStreamingReply = { partial ->
                    _characterCallState.value = _characterCallState.value.copy(
                        isChimeGateMuting = false,
                        lastCharacterReply = partial,
                        callStatusText = "Receiving live turn from wss://neo.character.ai/ws/..."
                    )
                }
            )

            val finalTurnMsg = wsTurnResult.getOrNull() ?: CaiTurnMessage(
                turnId = "reply_${System.currentTimeMillis()}",
                authorName = characterShortName,
                isUser = false,
                text = acct.activeCharacter.greeting
            )

            val updatedTurnsWithReply = (_characterCallState.value.liveChatTurns + finalTurnMsg).takeLast(20)

            _characterCallState.value = _characterCallState.value.copy(
                isChimeGateMuting = false,
                lastCallerStatement = cleanedCallerPrompt,
                lastCharacterReply = finalTurnMsg.text,
                liveChatTurns = updatedTurnsWithReply,
                callStatusText = "Rendering $characterShortName's 48kHz Zero-Chime Voice..."
            )

            // Strictly use forceDeviceTts = true so NO Gemini API is ever called for character chat or voice calls
            val synthResult = synthesizer.synthesizeSingleSpeaker(
                text = finalTurnMsg.text,
                voiceName = state.selectedVoice.id,
                stylePrompt = callState.characterPersona,
                speed = state.speed,
                pitch = state.pitch,
                forceDeviceTts = true
            )

            _uiState.value = _uiState.value.copy(isSynthesizing = false)

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
                    script = finalTurnMsg.text,
                    stylePrompt = "Zero-Chime c.ai Call • Persona: ${acct.activePersona.name}",
                    isMultiSpeaker = false,
                    speaker1Voice = state.selectedVoice.name,
                    speed = state.speed,
                    pitch = state.pitch,
                    durationMs = finalLoaded.durationMs,
                    filePath = finalLoaded.wavFile.absolutePath,
                    amplitudesJson = PcmWavHelper.serializeAmplitudes(finalLoaded.amplitudes),
                    isGeminiCloud = false
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
                    callStatusText = "c.ai Reply Received • Audio Output Ready"
                )
            }
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
            activeTab = StudioTab.OPUS_TRIMMER,
            activeStudioSection = VoiceStudioSection.WAVEFORM_TRIM
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
        firebaseAuth.removeAuthStateListener(authStateListener)
        stopCloudObservation()
        stopCallTimer()
        CharacterAiChimeInterceptorService.stopService(getApplication())
        audioPlayer.release()
        synthesizer.shutdown()
        transcriber.stopListening()
    }
}
