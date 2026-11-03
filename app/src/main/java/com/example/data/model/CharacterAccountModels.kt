package com.example.data.model

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.audio.OpusContainerTarget
import com.example.audio.StudioAudioSource
import org.json.JSONArray
import org.json.JSONObject

enum class AuthProvider(val label: String, val badgeText: String) {
    GOOGLE_EMAIL("Google Account", "Google + Cloud Sync"),
    CHARACTER_AI("Character.AI Account", "c.ai Direct Sync"),
    APPLE_ID("Apple ID", "Apple ID + c.ai Sync")
}

enum class CaiLaunchMode(val label: String, val description: String) {
    NATIVE_APP_DIRECT(
        "Launch Native App Directly",
        "Opens the installed Character.AI Android app (ai.character.app) directly and blocks web browser redirects."
    ),
    NATIVE_DEEP_LINK_CHARACTER(
        "Deep-Link Active Character in App",
        "Launches the native Character.AI app straight into your active synced character's profile."
    ),
    PLAY_STORE_IF_MISSING(
        "Play Store Fallback Only",
        "Opens the native Character.AI app, or opens Google Play Store if the app is not installed (never opens web browser)."
    )
}

enum class CharacterVisibility(val label: String) {
    PUBLIC("Public"),
    UNLISTED("Unlisted"),
    PRIVATE("Private")
}

data class SyncedUserPersona(
    val personaId: String,
    val name: String,
    val handle: String,
    val definition: String,
    val avatarUrl: String = "",
    val isDefault: Boolean = false
)

data class SyncedCharacterProfile(
    val characterId: String,
    val name: String,
    val handle: String,
    val tagline: String,
    val personaBio: String,
    val greeting: String,
    val voicePresetId: String,
    val voiceProfileName: String,
    val pitchMultiplier: Float = 1.0f,
    val speedMultiplier: Float = 1.0f,
    val recommendedHoldoffMs: Int = 950,
    val avatarUrl: String = "",
    val definition: String = "",
    val visibility: CharacterVisibility = CharacterVisibility.PUBLIC,
    val hasLorebook: Boolean = false,
    val lorebookEntriesCount: Int = 0
)

data class CaiTurnMessage(
    val turnId: String,
    val authorName: String,
    val isUser: Boolean,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class UserAccountState(
    val isSignedIn: Boolean = false,
    val firebaseUid: String = "",
    val provider: AuthProvider = AuthProvider.GOOGLE_EMAIL,
    val displayName: String = "",
    val emailOrUsername: String = "",
    val caiLinkedUsername: String = "",
    val hasCaiSessionToken: Boolean = false,
    val caiTokenStatusText: String = "No c.ai Session Token stored",
    val lastSyncTimestamp: String = "Not synced yet",
    val cloudSyncStatus: String = "Sign in to sync with Firebase Cloud",
    val isSyncing: Boolean = false,
    val authErrorMessage: String? = null,
    val syncedCharacters: List<SyncedCharacterProfile> = defaultStarterCharacters(),
    val activeCharacterId: String = "cai_aria_01",
    val syncedPersonas: List<SyncedUserPersona> = defaultStarterPersonas(),
    val activePersonaId: String = "persona_default_01"
) {
    val activeCharacter: SyncedCharacterProfile
        get() = syncedCharacters.firstOrNull { it.characterId == activeCharacterId }
            ?: syncedCharacters.first()

    val activePersona: SyncedUserPersona
        get() = syncedPersonas.firstOrNull { it.personaId == activePersonaId }
            ?: syncedPersonas.first()
}

data class StudioSettingsConfig(
    val caiLaunchMode: CaiLaunchMode = CaiLaunchMode.NATIVE_APP_DIRECT,
    val targetCaiPackage: String = "ai.character.app",
    val autoArmChimeBlockerOnLaunch: Boolean = true,
    val autoSyncCharacterOnLogin: Boolean = true,
    val defaultCallSpeakerphone: Boolean = true,
    val defaultMuteWindowMs: Int = 950,
    val defaultSpectralNotch: Boolean = true,
    val defaultTelephonyBandpass: Boolean = true,
    val defaultSampleRateHz: Int = 48000,
    val defaultMonoChannel: Boolean = true,
    val defaultExportContainer: OpusContainerTarget = OpusContainerTarget.OGG_OPUS,
    val defaultAudioSource: StudioAudioSource = StudioAudioSource.VOICE_COMMUNICATION,
    val defaultNoiseSuppression: Boolean = true,
    val defaultAutoGainControl: Boolean = true,
    val caiIntegrationEnabled: Boolean = true,
    val whatsappIntegrationEnabled: Boolean = true,
    val telegramIntegrationEnabled: Boolean = true,
    val googleChatIntegrationEnabled: Boolean = true
)

fun defaultStarterPersonas(): List<SyncedUserPersona> = listOf(
    SyncedUserPersona(
        personaId = "persona_default_01",
        name = "Studio Caller (Default)",
        handle = "@studio_caller",
        definition = "Thoughtful, articulate voice caller speaking clearly over a 48kHz Mono broadcast line.",
        isDefault = true
    ),
    SyncedUserPersona(
        personaId = "persona_night_owl_02",
        name = "Late-Night Confidant",
        handle = "@night_owl",
        definition = "Relaxed, candid late-night caller looking for deep conversation, dry humor, and creative brainstorming.",
        isDefault = false
    ),
    SyncedUserPersona(
        personaId = "persona_producer_03",
        name = "Audio Producer & Strategist",
        handle = "@sound_director",
        definition = "Direct, analytical sound engineer and narrative strategist testing real-time voice interaction.",
        isDefault = false
    )
)

fun defaultStarterCharacters(): List<SyncedCharacterProfile> = listOf(
    SyncedCharacterProfile(
        characterId = "cai_aria_01",
        name = "Aria Vance",
        handle = "@aria_vance",
        tagline = "Empathetic Late-Night Call Companion",
        personaBio = "Witty, warm, and observant confidante who speaks naturally on live phone calls with relaxed pacing and zero robotic pauses.",
        greeting = "Hey! I'm on the line—go ahead and put me on speaker, zero chimes enabled.",
        voicePresetId = "Aoede",
        voiceProfileName = "Aoede Velvet (48kHz c.ai Voice)",
        pitchMultiplier = 1.02f,
        speedMultiplier = 1.0f,
        recommendedHoldoffMs = 950,
        definition = "{{char}}: Hey! Can you hear me alright? I'm glad you called.\n{{user}}: Loud and clear.",
        visibility = CharacterVisibility.PUBLIC,
        hasLorebook = true,
        lorebookEntriesCount = 4
    ),
    SyncedCharacterProfile(
        characterId = "cai_julian_02",
        name = "Julian Sterling",
        handle = "@julian_sterling",
        tagline = "Strategic Mentor & Calm Debater",
        personaBio = "Composed, articulate strategist with a resonant baritone voice. Speaks with measured cadence and dry humor during live calls.",
        greeting = "Julian here. Line is crystal clear—tell me what we're solving today.",
        voicePresetId = "Fenrir",
        voiceProfileName = "Fenrir Baritone (48kHz c.ai Voice)",
        pitchMultiplier = 0.94f,
        speedMultiplier = 0.98f,
        recommendedHoldoffMs = 900,
        definition = "{{char}}: Let's cut through the noise and focus on what matters.",
        visibility = CharacterVisibility.UNLISTED,
        hasLorebook = true,
        lorebookEntriesCount = 6
    ),
    SyncedCharacterProfile(
        characterId = "cai_kael_03",
        name = "Kaelen Voss",
        handle = "@kaelen_voss",
        tagline = "Cyberpunk Audio Engineer & Night Owl",
        personaBio = "Fast-talking sound designer and late-night conversationalist with energetic delivery and expressive vocal inflection.",
        greeting = "Yo! Patch is live at 48 kilohertz mono, chime gate locked in. What's the word?",
        voicePresetId = "Puck",
        voiceProfileName = "Puck Dynamic (48kHz c.ai Voice)",
        pitchMultiplier = 1.0f,
        speedMultiplier = 1.04f,
        recommendedHoldoffMs = 850,
        definition = "{{char}}: Signal-to-noise ratio is looking clean tonight.",
        visibility = CharacterVisibility.PRIVATE,
        hasLorebook = true,
        lorebookEntriesCount = 8
    ),
    SyncedCharacterProfile(
        characterId = "cai_seraphina_04",
        name = "Seraphina Vale",
        handle = "@seraphina_vale",
        tagline = "Storyteller & Poetic Conversationalist",
        personaBio = "Soft-spoken, expressive storyteller with rich emotional nuance and calm breath control designed for quiet phone calls.",
        greeting = "Hello there. I can hear you clearly—take your time, I'm listening.",
        voicePresetId = "Kore",
        voiceProfileName = "Kore Soft Studio (48kHz c.ai Voice)",
        pitchMultiplier = 1.05f,
        speedMultiplier = 0.96f,
        recommendedHoldoffMs = 1050,
        definition = "{{char}}: Every voice carries a story waiting to be heard.",
        visibility = CharacterVisibility.PUBLIC,
        hasLorebook = false,
        lorebookEntriesCount = 0
    )
)

class StudioPreferencesManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("opus_voice_studio_prefs", Context.MODE_PRIVATE)

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "opus_voice_cai_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            appContext.getSharedPreferences("opus_voice_cai_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveEncryptedCaiCredentials(token: String, cookieHeader: String = "") {
        val cleanToken = token
            .removePrefix("Token ")
            .removePrefix("Bearer ")
            .trim()
        encryptedPrefs.edit()
            .putString("cai_auth_token", cleanToken)
            .putString("cai_cookie_header", cookieHeader.trim())
            .putLong("cai_token_saved_at", System.currentTimeMillis())
            .apply()
    }

    fun loadEncryptedCaiToken(): String {
        return encryptedPrefs.getString("cai_auth_token", "")?.trim() ?: ""
    }

    fun loadEncryptedCaiCookies(): String {
        return encryptedPrefs.getString("cai_cookie_header", "")?.trim() ?: ""
    }

    fun clearEncryptedCaiCredentials() {
        encryptedPrefs.edit()
            .remove("cai_auth_token")
            .remove("cai_cookie_header")
            .remove("cai_token_saved_at")
            .apply()
    }

    fun loadSettings(): StudioSettingsConfig {
        val launchModeName = prefs.getString("cai_launch_mode", CaiLaunchMode.NATIVE_APP_DIRECT.name)
            ?: CaiLaunchMode.NATIVE_APP_DIRECT.name
        val launchMode = CaiLaunchMode.entries.firstOrNull { it.name == launchModeName }
            ?: CaiLaunchMode.NATIVE_APP_DIRECT

        val sourceName = prefs.getString("default_audio_source", StudioAudioSource.VOICE_COMMUNICATION.name)
            ?: StudioAudioSource.VOICE_COMMUNICATION.name
        val audioSource = StudioAudioSource.entries.firstOrNull { it.name == sourceName }
            ?: StudioAudioSource.VOICE_COMMUNICATION

        val containerName = prefs.getString("default_export_container", OpusContainerTarget.OGG_OPUS.name)
            ?: OpusContainerTarget.OGG_OPUS.name
        val exportContainer = OpusContainerTarget.entries.firstOrNull { it.name == containerName }
            ?: OpusContainerTarget.OGG_OPUS

        return StudioSettingsConfig(
            caiLaunchMode = launchMode,
            targetCaiPackage = prefs.getString("target_cai_package", "ai.character.app") ?: "ai.character.app",
            autoArmChimeBlockerOnLaunch = prefs.getBoolean("auto_arm_chime_on_launch", true),
            autoSyncCharacterOnLogin = prefs.getBoolean("auto_sync_on_login", true),
            defaultCallSpeakerphone = prefs.getBoolean("default_call_speakerphone", true),
            defaultMuteWindowMs = prefs.getInt("default_mute_window_ms", 950).coerceIn(300, 2200),
            defaultSpectralNotch = prefs.getBoolean("default_spectral_notch", true),
            defaultTelephonyBandpass = prefs.getBoolean("default_telephony_bandpass", true),
            defaultSampleRateHz = prefs.getInt("default_sample_rate_hz", 48000),
            defaultMonoChannel = prefs.getBoolean("default_mono_channel", true),
            defaultExportContainer = exportContainer,
            defaultAudioSource = audioSource,
            defaultNoiseSuppression = prefs.getBoolean("default_noise_suppression", true),
            defaultAutoGainControl = prefs.getBoolean("default_agc", true),
            caiIntegrationEnabled = prefs.getBoolean("cai_integration_enabled", true),
            whatsappIntegrationEnabled = prefs.getBoolean("whatsapp_integration_enabled", true),
            telegramIntegrationEnabled = prefs.getBoolean("telegram_integration_enabled", true),
            googleChatIntegrationEnabled = prefs.getBoolean("google_chat_integration_enabled", true)
        )
    }

    fun saveSettings(config: StudioSettingsConfig) {
        prefs.edit()
            .putString("cai_launch_mode", config.caiLaunchMode.name)
            .putString("target_cai_package", config.targetCaiPackage)
            .putBoolean("auto_arm_chime_on_launch", config.autoArmChimeBlockerOnLaunch)
            .putBoolean("auto_sync_on_login", config.autoSyncCharacterOnLogin)
            .putBoolean("default_call_speakerphone", config.defaultCallSpeakerphone)
            .putInt("default_mute_window_ms", config.defaultMuteWindowMs)
            .putBoolean("default_spectral_notch", config.defaultSpectralNotch)
            .putBoolean("default_telephony_bandpass", config.defaultTelephonyBandpass)
            .putInt("default_sample_rate_hz", config.defaultSampleRateHz)
            .putBoolean("default_mono_channel", config.defaultMonoChannel)
            .putString("default_export_container", config.defaultExportContainer.name)
            .putString("default_audio_source", config.defaultAudioSource.name)
            .putBoolean("default_noise_suppression", config.defaultNoiseSuppression)
            .putBoolean("default_agc", config.defaultAutoGainControl)
            .putBoolean("cai_integration_enabled", config.caiIntegrationEnabled)
            .putBoolean("whatsapp_integration_enabled", config.whatsappIntegrationEnabled)
            .putBoolean("telegram_integration_enabled", config.telegramIntegrationEnabled)
            .putBoolean("google_chat_integration_enabled", config.googleChatIntegrationEnabled)
            .apply()
    }

    fun loadAccountState(): UserAccountState {
        val isSignedIn = prefs.getBoolean("acct_signed_in", false)
        val firebaseUid = prefs.getString("acct_firebase_uid", "") ?: ""
        val providerName = prefs.getString("acct_provider", AuthProvider.GOOGLE_EMAIL.name)
            ?: AuthProvider.GOOGLE_EMAIL.name
        val provider = AuthProvider.entries.firstOrNull { it.name == providerName }
            ?: AuthProvider.GOOGLE_EMAIL
        val displayName = prefs.getString("acct_display_name", "") ?: ""
        val emailOrUser = prefs.getString("acct_email_user", "") ?: ""
        val caiUser = prefs.getString("acct_cai_username", "") ?: ""
        val lastSync = prefs.getString("acct_last_sync", "Not synced yet") ?: "Not synced yet"
        val cloudStatus = prefs.getString("acct_cloud_status", "Sign in to sync with Firebase Cloud")
            ?: "Sign in to sync with Firebase Cloud"
        val activeCharId = prefs.getString("acct_active_char_id", "cai_aria_01") ?: "cai_aria_01"
        val activePersonaId = prefs.getString("acct_active_persona_id", "persona_default_01") ?: "persona_default_01"

        val savedToken = loadEncryptedCaiToken()
        val hasToken = savedToken.isNotBlank()
        val tokenStatus = if (hasToken) {
            val preview = if (savedToken.length > 10) "${savedToken.take(6)}...${savedToken.takeLast(4)}" else "Active"
            "Encrypted Token Active ($preview)"
        } else {
            "No c.ai Session Token stored"
        }

        val charactersJson = prefs.getString("acct_synced_chars_json", null)
        val syncedList = if (!charactersJson.isNullOrBlank()) {
            parseCharactersJson(charactersJson).ifEmpty { defaultStarterCharacters() }
        } else {
            defaultStarterCharacters()
        }

        val personasJson = prefs.getString("acct_synced_personas_json", null)
        val personasList = if (!personasJson.isNullOrBlank()) {
            parsePersonasJson(personasJson).ifEmpty { defaultStarterPersonas() }
        } else {
            defaultStarterPersonas()
        }

        return UserAccountState(
            isSignedIn = isSignedIn,
            firebaseUid = firebaseUid,
            provider = provider,
            displayName = displayName,
            emailOrUsername = emailOrUser,
            caiLinkedUsername = caiUser,
            hasCaiSessionToken = hasToken,
            caiTokenStatusText = tokenStatus,
            lastSyncTimestamp = lastSync,
            cloudSyncStatus = cloudStatus,
            isSyncing = false,
            syncedCharacters = syncedList,
            activeCharacterId = activeCharId,
            syncedPersonas = personasList,
            activePersonaId = activePersonaId
        )
    }

    fun saveAccountState(state: UserAccountState) {
        prefs.edit()
            .putBoolean("acct_signed_in", state.isSignedIn)
            .putString("acct_firebase_uid", state.firebaseUid)
            .putString("acct_provider", state.provider.name)
            .putString("acct_display_name", state.displayName)
            .putString("acct_email_user", state.emailOrUsername)
            .putString("acct_cai_username", state.caiLinkedUsername)
            .putString("acct_last_sync", state.lastSyncTimestamp)
            .putString("acct_cloud_status", state.cloudSyncStatus)
            .putString("acct_active_char_id", state.activeCharacterId)
            .putString("acct_active_persona_id", state.activePersonaId)
            .putString("acct_synced_chars_json", serializeCharactersJson(state.syncedCharacters))
            .putString("acct_synced_personas_json", serializePersonasJson(state.syncedPersonas))
            .apply()
    }

    private fun serializeCharactersJson(list: List<SyncedCharacterProfile>): String {
        val arr = JSONArray()
        list.forEach { c ->
            val obj = JSONObject().apply {
                put("characterId", c.characterId)
                put("name", c.name)
                put("handle", c.handle)
                put("tagline", c.tagline)
                put("personaBio", c.personaBio)
                put("greeting", c.greeting)
                put("voicePresetId", c.voicePresetId)
                put("voiceProfileName", c.voiceProfileName)
                put("pitchMultiplier", c.pitchMultiplier.toDouble())
                put("speedMultiplier", c.speedMultiplier.toDouble())
                put("recommendedHoldoffMs", c.recommendedHoldoffMs)
                put("avatarUrl", c.avatarUrl)
                put("definition", c.definition)
                put("visibility", c.visibility.name)
                put("hasLorebook", c.hasLorebook)
                put("lorebookEntriesCount", c.lorebookEntriesCount)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseCharactersJson(raw: String): List<SyncedCharacterProfile> {
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<SyncedCharacterProfile>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val visStr = o.optString("visibility", CharacterVisibility.PUBLIC.name)
                val visibility = CharacterVisibility.entries.firstOrNull { it.name == visStr }
                    ?: CharacterVisibility.PUBLIC
                result.add(
                    SyncedCharacterProfile(
                        characterId = o.optString("characterId", "cai_custom_$i"),
                        name = o.optString("name", "Aria Vance"),
                        handle = o.optString("handle", "@aria_vance"),
                        tagline = o.optString("tagline", "c.ai Synced Companion"),
                        personaBio = o.optString("personaBio", "Natural phone call persona"),
                        greeting = o.optString("greeting", "Hey! I'm on the line."),
                        voicePresetId = o.optString("voicePresetId", "Aoede"),
                        voiceProfileName = o.optString("voiceProfileName", "Aoede 48kHz Voice"),
                        pitchMultiplier = o.optDouble("pitchMultiplier", 1.0).toFloat(),
                        speedMultiplier = o.optDouble("speedMultiplier", 1.0).toFloat(),
                        recommendedHoldoffMs = o.optInt("recommendedHoldoffMs", 950),
                        avatarUrl = o.optString("avatarUrl", ""),
                        definition = o.optString("definition", ""),
                        visibility = visibility,
                        hasLorebook = o.optBoolean("hasLorebook", false),
                        lorebookEntriesCount = o.optInt("lorebookEntriesCount", 0)
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializePersonasJson(list: List<SyncedUserPersona>): String {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject().apply {
                put("personaId", p.personaId)
                put("name", p.name)
                put("handle", p.handle)
                put("definition", p.definition)
                put("avatarUrl", p.avatarUrl)
                put("isDefault", p.isDefault)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parsePersonasJson(raw: String): List<SyncedUserPersona> {
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<SyncedUserPersona>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(
                    SyncedUserPersona(
                        personaId = o.optString("personaId", "persona_$i"),
                        name = o.optString("name", "Studio Caller"),
                        handle = o.optString("handle", "@caller"),
                        definition = o.optString("definition", ""),
                        avatarUrl = o.optString("avatarUrl", ""),
                        isDefault = o.optBoolean("isDefault", i == 0)
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }
}
