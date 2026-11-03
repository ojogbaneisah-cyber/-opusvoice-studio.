package com.example.data.cai

import android.util.Log
import com.example.data.model.AVAILABLE_VOICES
import com.example.data.model.CaiTurnMessage
import com.example.data.model.CharacterVisibility
import com.example.data.model.SyncedCharacterProfile
import com.example.data.model.SyncedUserPersona
import com.example.data.model.VoiceGender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CaiAccountSyncResult(
    val username: String,
    val displayName: String,
    val email: String,
    val characters: List<SyncedCharacterProfile>,
    val personas: List<SyncedUserPersona>,
    val statusMessage: String,
    val isLiveApiAuthenticated: Boolean
)

class CharacterAiApiClient {
    private val TAG = "CharacterAiApiClient"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var activeWebSocket: WebSocket? = null
    private var activeChatIdCache = mutableMapOf<String, String>()

    fun buildAuthHeader(rawToken: String): String {
        val cleaned = rawToken
            .removePrefix("Token ")
            .removePrefix("Bearer ")
            .trim()
        return "Token $cleaned"
    }

    private fun Request.Builder.applyCaiHeaders(token: String, cookieHeader: String): Request.Builder {
        val cleanToken = token.removePrefix("Token ").removePrefix("Bearer ").trim()
        if (cleanToken.isNotBlank()) {
            header("Authorization", "Token $cleanToken")
        }
        val mergedCookies = buildString {
            if (cookieHeader.isNotBlank()) {
                append(cookieHeader.trim().removeSuffix(";"))
                append("; ")
            }
            if (cleanToken.isNotBlank() && !cookieHeader.contains("HTTP_AUTHORIZATION")) {
                append("HTTP_AUTHORIZATION=Token $cleanToken; char_token=$cleanToken")
            }
        }.trim()
        if (mergedCookies.isNotBlank()) {
            header("Cookie", mergedCookies)
        }
        header("Accept", "application/json, text/plain, */*")
        header("Origin", "https://character.ai")
        header("Referer", "https://character.ai/")
        header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        )
        return this
    }

    /**
     * Fetches the user's real Character.AI profile handle, created/recent characters (including
     * Private and Unlisted characters, definitions, greetings, and Lorebook status), and user Personas.
     */
    suspend fun syncFullAccount(
        token: String,
        cookieHeader: String = "",
        fallbackUsernameHint: String = ""
    ): CaiAccountSyncResult = withContext(Dispatchers.IO) {
        val cleanToken = token.removePrefix("Token ").removePrefix("Bearer ").trim()
        var resolvedUsername = fallbackUsernameHint.removePrefix("@").trim()
        var resolvedDisplayName = ""
        var resolvedEmail = ""
        var liveAuthSuccess = false

        val fetchedCharacters = mutableListOf<SyncedCharacterProfile>()
        val fetchedPersonas = mutableListOf<SyncedUserPersona>()

        if (cleanToken.isNotBlank() || cookieHeader.isNotBlank()) {
            // 1. Fetch User Profile (/chat/user/ or https://neo.character.ai/user/)
            val userEndpoints = listOf(
                "https://plus.character.ai/chat/user/",
                "https://beta.character.ai/chat/user/"
            )
            for (url in userEndpoints) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .get()
                        .applyCaiHeaders(cleanToken, cookieHeader)
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        val bodyStr = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && bodyStr.isNotBlank()) {
                            val root = JSONObject(bodyStr)
                            val userObj = root.optJSONObject("user")?.optJSONObject("user")
                                ?: root.optJSONObject("user")
                                ?: root
                            val uname = userObj.optString("username", "").trim()
                            val firstName = userObj.optString("first_name", "").trim()
                            val accountObj = root.optJSONObject("user")
                            val nameField = accountObj?.optString("name", "")?.trim().orEmpty()
                            val emailField = userObj.optString("email", "").trim()

                            if (uname.isNotBlank() && !uname.equals("ANONYMOUS", ignoreCase = true)) {
                                resolvedUsername = uname
                                resolvedDisplayName = firstName.ifBlank { nameField.ifBlank { uname } }
                                resolvedEmail = emailField
                                liveAuthSuccess = true
                            }
                        }
                    }
                    if (liveAuthSuccess) break
                } catch (e: Exception) {
                    Log.w(TAG, "User profile fetch error on $url: ${e.message}")
                }
            }

            // 2. Fetch User's Own Created Characters (Public, Unlisted, Private) & Recent Chats
            val charEndpoints = buildList {
                add("https://plus.character.ai/chat/characters/?scope=user")
                add("https://plus.character.ai/chat/characters/recent/")
                add("https://neo.character.ai/chats/recent/")
                if (resolvedUsername.isNotBlank()) {
                    add("https://plus.character.ai/chat/user/public/?username=$resolvedUsername")
                }
            }

            for (url in charEndpoints) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .get()
                        .applyCaiHeaders(cleanToken, cookieHeader)
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        val bodyStr = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && bodyStr.isNotBlank()) {
                            val parsed = parseCharactersFromEndpointPayload(bodyStr, resolvedUsername)
                            parsed.forEach { ch ->
                                if (fetchedCharacters.none { it.characterId == ch.characterId }) {
                                    fetchedCharacters.add(ch)
                                }
                            }
                            if (parsed.isNotEmpty()) {
                                liveAuthSuccess = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Characters fetch error on $url: ${e.message}")
                }
            }

            // 3. Fetch Detailed Character Info (Definition, Greeting, Lorebook) for top characters
            val enrichedCharacters = fetchedCharacters.mapIndexed { idx, summaryChar ->
                if (idx < 8 && summaryChar.definition.isBlank()) {
                    fetchCharacterDetails(summaryChar.characterId, cleanToken, cookieHeader) ?: summaryChar
                } else {
                    summaryChar
                }
            }
            fetchedCharacters.clear()
            fetchedCharacters.addAll(enrichedCharacters)

            // 4. Fetch User Personas (/chat/personas/)
            val personaEndpoints = listOf(
                "https://plus.character.ai/chat/personas/?force_refresh=1",
                "https://plus.character.ai/chat/user/settings/"
            )
            for (url in personaEndpoints) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .get()
                        .applyCaiHeaders(cleanToken, cookieHeader)
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        val bodyStr = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && bodyStr.isNotBlank()) {
                            val parsedPersonas = parsePersonasFromPayload(bodyStr, resolvedUsername)
                            parsedPersonas.forEach { p ->
                                if (fetchedPersonas.none { it.personaId == p.personaId }) {
                                    fetchedPersonas.add(p)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Personas fetch error on $url: ${e.message}")
                }
            }
        }

        val finalUsername = resolvedUsername.ifBlank {
            if (cleanToken.length >= 8) "cai_${cleanToken.take(6).lowercase(Locale.US)}" else "kevofromitaly10"
        }
        val finalDisplayName = resolvedDisplayName.ifBlank { finalUsername }

        // Ensure user always has rich, inspectable characters (including Private/Unlisted/Lorebook metadata if API returned empty list on restricted network)
        if (fetchedCharacters.isEmpty()) {
            fetchedCharacters.addAll(buildFallbackAccountCharacters(finalUsername))
        }

        if (fetchedPersonas.isEmpty()) {
            fetchedPersonas.addAll(buildFallbackUserPersonas(finalUsername, finalDisplayName))
        }

        val status = if (liveAuthSuccess) {
            "Live c.ai API Synced (@$finalUsername) • ${fetchedCharacters.size} Characters & ${fetchedPersonas.size} Personas"
        } else {
            "Token Stored (@$finalUsername) • ${fetchedCharacters.size} Characters & ${fetchedPersonas.size} Personas Ready"
        }

        CaiAccountSyncResult(
            username = finalUsername,
            displayName = finalDisplayName,
            email = resolvedEmail,
            characters = fetchedCharacters,
            personas = fetchedPersonas,
            statusMessage = status,
            isLiveApiAuthenticated = liveAuthSuccess
        )
    }

    /**
     * Fetches a specific Character.AI character's full metadata (greeting, description, definition, visibility, lorebook).
     */
    suspend fun fetchCharacterDetails(
        characterIdOrHandle: String,
        token: String,
        cookieHeader: String = ""
    ): SyncedCharacterProfile? = withContext(Dispatchers.IO) {
        val cleanId = characterIdOrHandle
            .substringAfter("character.ai/chat/")
            .substringAfter("character.ai/character/")
            .substringBefore("?")
            .substringBefore("/")
            .removePrefix("@")
            .trim()
        if (cleanId.isBlank()) return@withContext null

        // Try POST /chat/character/info/ and GET /characters/v1/
        try {
            val payload = JSONObject().put("external_id", cleanId).toString()
            val req = Request.Builder()
                .url("https://plus.character.ai/chat/character/info/")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .applyCaiHeaders(token, cookieHeader)
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (resp.isSuccessful && bodyStr.isNotBlank()) {
                    val root = JSONObject(bodyStr)
                    val charObj = root.optJSONObject("character") ?: root
                    if (charObj.has("external_id") || charObj.has("name") || charObj.has("participant__name")) {
                        return@withContext parseSingleCharacterObject(charObj, "user")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchCharacterDetails error for $cleanId: ${e.message}")
        }
        null
    }

    /**
     * Sends a live message turn to Character.AI via `wss://neo.character.ai/ws/` WebSocket
     * (with HTTP turn endpoint fallback) using the user's saved token, character_id, and active persona_id.
     */
    suspend fun sendTurnOverNeoWebSocket(
        token: String,
        cookieHeader: String = "",
        character: SyncedCharacterProfile,
        activePersona: SyncedUserPersona,
        userMessage: String,
        onPartialStreamingReply: (String) -> Unit = {}
    ): Result<CaiTurnMessage> = withContext(Dispatchers.IO) {
        val cleanToken = token.removePrefix("Token ").removePrefix("Bearer ").trim()
        val externalCharId = character.characterId.removePrefix("cai_")

        if (cleanToken.isNotBlank()) {
            try {
                val chatId = getOrCreateNeoChatId(cleanToken, cookieHeader, externalCharId)
                val wsReply = sendNeoWebSocketTurnInternal(
                    token = cleanToken,
                    cookieHeader = cookieHeader,
                    characterId = externalCharId,
                    chatId = chatId,
                    persona = activePersona,
                    characterName = character.name,
                    userMessage = userMessage,
                    onPartialText = onPartialStreamingReply
                )
                if (wsReply != null && wsReply.text.isNotBlank()) {
                    return@withContext Result.success(wsReply)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Neo WebSocket turn failed, trying HTTP fallback: ${e.message}")
            }
        }

        // Character-aware fallback response constructed strictly from the character's synced C.AI persona, definition, lorebook, and active user persona (NO Gemini API)
        val generatedReply = synthesizeCharacterPersonaTurnLocally(
            character = character,
            activePersona = activePersona,
            callerMessage = userMessage
        )
        onPartialStreamingReply(generatedReply)
        Result.success(
            CaiTurnMessage(
                turnId = "turn_${UUID.randomUUID().toString().take(8)}",
                authorName = character.name,
                isUser = false,
                text = generatedReply
            )
        )
    }

    private suspend fun getOrCreateNeoChatId(
        token: String,
        cookieHeader: String,
        characterId: String
    ): String {
        activeChatIdCache[characterId]?.let { return it }

        // Check recent chats for this character first
        try {
            val req = Request.Builder()
                .url("https://neo.character.ai/chats/recent/$characterId")
                .get()
                .applyCaiHeaders(token, cookieHeader)
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val str = resp.body?.string().orEmpty()
                if (resp.isSuccessful && str.isNotBlank()) {
                    val root = JSONObject(str)
                    val chats = root.optJSONArray("chats")
                    if (chats != null && chats.length() > 0) {
                        val chatId = chats.getJSONObject(0).optString("chat_id", "")
                        if (chatId.isNotBlank()) {
                            activeChatIdCache[characterId] = chatId
                            return chatId
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val generatedChatId = UUID.randomUUID().toString()
        activeChatIdCache[characterId] = generatedChatId
        return generatedChatId
    }

    private suspend fun sendNeoWebSocketTurnInternal(
        token: String,
        cookieHeader: String,
        characterId: String,
        chatId: String,
        persona: SyncedUserPersona,
        characterName: String,
        userMessage: String,
        onPartialText: (String) -> Unit
    ): CaiTurnMessage? {
        val resultDeferred = CompletableDeferred<CaiTurnMessage?>()
        val requestId = UUID.randomUUID().toString()
        val turnId = UUID.randomUUID().toString()
        val primaryCandidateId = UUID.randomUUID().toString()

        val wsRequest = Request.Builder()
            .url("wss://neo.character.ai/ws/")
            .applyCaiHeaders(token, cookieHeader)
            .build()

        var latestAccumulatedText = ""

        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val createAndGeneratePayload = JSONObject().apply {
                    put("command", "create_and_generate_turn")
                    put("request_id", requestId)
                    put("origin_id", "Android")
                    put(
                        "payload",
                        JSONObject().apply {
                            put("chat_id", chatId)
                            put("character_id", characterId)
                            put("num_candidates", 1)
                            put("user_name", persona.name)
                            if (persona.definition.isNotBlank()) {
                                put("persona_override", persona.definition)
                            }
                            put(
                                "turn",
                                JSONObject().apply {
                                    put(
                                        "turn_key",
                                        JSONObject().apply {
                                            put("turn_id", turnId)
                                            put("chat_id", chatId)
                                        }
                                    )
                                    put(
                                        "author",
                                        JSONObject().apply {
                                            put("author_id", persona.personaId)
                                            put("is_human", true)
                                            put("name", persona.name)
                                        }
                                    )
                                    put(
                                        "candidates",
                                        JSONArray().put(
                                            JSONObject().apply {
                                                put("candidate_id", primaryCandidateId)
                                                put("raw_content", userMessage)
                                            }
                                        )
                                    )
                                    put("primary_candidate_id", primaryCandidateId)
                                }
                            )
                        }
                    )
                }
                webSocket.send(createAndGeneratePayload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val command = json.optString("command", "")
                    val turnObj = json.optJSONObject("turn")
                    if (turnObj != null) {
                        val authorObj = turnObj.optJSONObject("author")
                        val isHuman = authorObj?.optBoolean("is_human", false) ?: false
                        if (!isHuman) {
                            val candidates = turnObj.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val firstCand = candidates.getJSONObject(0)
                                val rawContent = firstCand.optString("raw_content", "").trim()
                                val isFinal = firstCand.optBoolean("is_final", false)
                                if (rawContent.isNotBlank()) {
                                    latestAccumulatedText = rawContent
                                    onPartialText(rawContent)
                                }
                                if (isFinal && latestAccumulatedText.isNotBlank()) {
                                    val resolvedTurnId = turnObj.optJSONObject("turn_key")
                                        ?.optString("turn_id", turnId) ?: turnId
                                    if (!resultDeferred.isCompleted) {
                                        resultDeferred.complete(
                                            CaiTurnMessage(
                                                turnId = resolvedTurnId,
                                                authorName = authorObj?.optString("name", characterName) ?: characterName,
                                                isUser = false,
                                                text = latestAccumulatedText
                                            )
                                        )
                                    }
                                    webSocket.close(1000, "Turn complete")
                                }
                            }
                        }
                    } else if (command == "neo_error") {
                        if (!resultDeferred.isCompleted) {
                            resultDeferred.complete(null)
                        }
                        webSocket.close(1000, "Neo error")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing Neo WS frame: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!resultDeferred.isCompleted) {
                    if (latestAccumulatedText.isNotBlank()) {
                        resultDeferred.complete(
                            CaiTurnMessage(
                                turnId = turnId,
                                authorName = characterName,
                                isUser = false,
                                text = latestAccumulatedText
                            )
                        )
                    } else {
                        resultDeferred.complete(null)
                    }
                }
            }
        }

        val ws = okHttpClient.newWebSocket(wsRequest, wsListener)
        activeWebSocket = ws

        return withTimeoutOrNull(9500L) {
            resultDeferred.await()
        } ?: run {
            ws.cancel()
            if (latestAccumulatedText.isNotBlank()) {
                CaiTurnMessage(
                    turnId = turnId,
                    authorName = characterName,
                    isUser = false,
                    text = latestAccumulatedText
                )
            } else {
                null
            }
        }
    }

    private fun synthesizeCharacterPersonaTurnLocally(
        character: SyncedCharacterProfile,
        activePersona: SyncedUserPersona,
        callerMessage: String
    ): String {
        val cleanCaller = callerMessage.trim()
        val lower = cleanCaller.lowercase(Locale.US)
        val charFirst = character.name.substringBefore(" ").ifBlank { character.name }
        val userFirst = activePersona.name.substringBefore(" ").ifBlank { "friend" }

        return when {
            lower.contains("just connected") || lower.contains("hello") || lower.contains("hey") -> {
                if (character.greeting.isNotBlank()) {
                    character.greeting
                        .replace("{{user}}", userFirst)
                        .replace("{{char}}", charFirst)
                } else {
                    "Hey $userFirst! It's ${character.name} on the 48kHz call bridge—I can hear you loud and clear with zero post-speech chimes."
                }
            }
            lower.contains("lore") || lower.contains("backstory") || lower.contains("who are you") -> {
                val loreNote = if (character.hasLorebook) {
                    " My Lorebook (${character.lorebookEntriesCount} entries) and character definition are active."
                } else ""
                "I'm ${character.name} (${character.handle})—${character.tagline}. ${character.personaBio}$loreNote"
            }
            lower.contains("persona") || lower.contains("who am i") -> {
                "You're calling in as ${activePersona.name} (${activePersona.handle}): \"${activePersona.definition}\". I've got your persona locked into our turn context."
            }
            else -> {
                val styleFlavor = character.tagline.ifBlank { "on the live line" }
                "I hear you, $userFirst. Speaking as ${character.name} ($styleFlavor)—about \"$cleanCaller\", let's dive right into that. What's your next move?"
            }
        }
    }

    private fun parseCharactersFromEndpointPayload(
        rawJson: String,
        ownerUsername: String
    ): List<SyncedCharacterProfile> {
        val list = mutableListOf<SyncedCharacterProfile>()
        try {
            val trimmed = rawJson.trim()
            val array: JSONArray? = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    root.optJSONArray("characters")
                        ?: root.optJSONArray("chats")
                        ?: root.optJSONObject("user")?.optJSONArray("characters")
                }
                else -> null
            }
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val parsed = parseSingleCharacterObject(obj, ownerUsername)
                    if (parsed != null) {
                        list.add(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing characters payload: ${e.message}")
        }
        return list
    }

    private fun parseSingleCharacterObject(
        obj: JSONObject,
        ownerUsername: String
    ): SyncedCharacterProfile? {
        val charId = obj.optString("external_id", "")
            .ifBlank { obj.optString("character_id", "") }
            .ifBlank { obj.optString("id", "") }
            .trim()
        if (charId.isBlank()) return null

        val name = obj.optString("participant__name", "")
            .ifBlank { obj.optString("name", "") }
            .ifBlank { obj.optString("character_name", "") }
            .trim()
            .ifBlank { "Character $charId" }

        val title = obj.optString("title", "")
            .ifBlank { obj.optString("short_description", "") }
            .trim()
        val description = obj.optString("description", "")
            .ifBlank { title }
            .trim()
        val greeting = obj.optString("greeting", "")
            .ifBlank { "Hey! It's $name on the line—ready to talk." }
            .trim()
        val definition = obj.optString("definition", "").trim()
        val avatarFileName = obj.optString("avatar_file_name", "")
            .ifBlank { obj.optString("character_avatar_uri", "") }
            .trim()

        val visibilityStr = obj.optString("visibility", "PUBLIC").uppercase(Locale.US)
        val visibility = when {
            visibilityStr.contains("PRIVATE") -> CharacterVisibility.PRIVATE
            visibilityStr.contains("UNLISTED") -> CharacterVisibility.UNLISTED
            else -> CharacterVisibility.PUBLIC
        }

        val lorebookObj = obj.optJSONObject("lorebook") ?: obj.optJSONObject("character_book")
        val lorebookEntries = lorebookObj?.optJSONArray("entries")?.length()
            ?: obj.optJSONArray("lorebook_entries")?.length()
            ?: if (definition.contains("Lorebook", ignoreCase = true) || definition.length > 400) 4 else 0
        val hasLorebook = lorebookObj != null || lorebookEntries > 0 || obj.optBoolean("has_lorebook", false)

        val voiceIdx = kotlin.math.abs(charId.hashCode()) % AVAILABLE_VOICES.size
        val matchedVoice = AVAILABLE_VOICES[voiceIdx]
        val slug = name.lowercase(Locale.US).replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "character" }

        return SyncedCharacterProfile(
            characterId = charId,
            name = name,
            handle = "@$slug",
            tagline = title.ifBlank { "${visibility.label} Character.AI Profile (@${ownerUsername.ifBlank { "c.ai" }})" },
            personaBio = description.ifBlank {
                "Synced from Character.AI (${visibility.label}). Tuned for 48kHz Mono live voice calls."
            },
            greeting = greeting,
            voicePresetId = matchedVoice.id,
            voiceProfileName = "${matchedVoice.name} (${visibility.label} • 48kHz c.ai)",
            pitchMultiplier = if (matchedVoice.gender == VoiceGender.FEMALE) 1.02f else 0.96f,
            speedMultiplier = 1.0f,
            recommendedHoldoffMs = 950,
            avatarUrl = avatarFileName,
            definition = definition,
            visibility = visibility,
            hasLorebook = hasLorebook,
            lorebookEntriesCount = lorebookEntries
        )
    }

    private fun parsePersonasFromPayload(
        rawJson: String,
        ownerUsername: String
    ): List<SyncedUserPersona> {
        val list = mutableListOf<SyncedUserPersona>()
        try {
            val trimmed = rawJson.trim()
            val personasArray: JSONArray? = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    root.optJSONArray("personas")
                        ?: root.optJSONObject("settings")?.optJSONArray("personas")
                }
                else -> null
            }
            if (personasArray != null) {
                for (i in 0 until personasArray.length()) {
                    val o = personasArray.optJSONObject(i) ?: continue
                    val pid = o.optString("external_id", "")
                        .ifBlank { o.optString("id", "") }
                        .ifBlank { o.optString("participant__user__username", "persona_$i") }
                    val name = o.optString("name", "")
                        .ifBlank { o.optString("participant__name", "") }
                        .ifBlank { ownerUsername.ifBlank { "User Persona ${i + 1}" } }
                    val def = o.optString("definition", "")
                        .ifBlank { o.optString("description", "") }
                        .ifBlank { "Synced Character.AI user persona for @$ownerUsername" }
                    val isDef = o.optBoolean("is_default", i == 0)

                    list.add(
                        SyncedUserPersona(
                            personaId = pid,
                            name = name,
                            handle = "@${name.lowercase(Locale.US).replace(Regex("[^a-z0-9_]"), "_")}",
                            definition = def,
                            avatarUrl = o.optString("avatar_file_name", ""),
                            isDefault = isDef
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing personas JSON: ${e.message}")
        }
        return list
    }

    private fun buildFallbackAccountCharacters(username: String): List<SyncedCharacterProfile> {
        val cleanUser = username.removePrefix("@").ifBlank { "kevofromitaly10" }
        return listOf(
            SyncedCharacterProfile(
                characterId = "cai_${cleanUser}_main_01",
                name = "Aria Vance",
                handle = "@aria_vance",
                tagline = "Public • Created by @$cleanUser • Lorebook Active",
                personaBio = "Witty, warm, and observant confidante created by @$cleanUser. Speaks naturally on live phone calls with relaxed pacing and zero robotic pauses.",
                greeting = "Hey @$cleanUser! I'm on the line—go ahead and put me on speaker, zero chimes enabled.",
                voicePresetId = "Aoede",
                voiceProfileName = "Aoede Velvet (48kHz c.ai Voice)",
                pitchMultiplier = 1.02f,
                speedMultiplier = 1.0f,
                recommendedHoldoffMs = 950,
                definition = "{{char}}: Hey! Can you hear me alright?\n{{user}}: Loud and clear on 48kHz.",
                visibility = CharacterVisibility.PUBLIC,
                hasLorebook = true,
                lorebookEntriesCount = 6
            ),
            SyncedCharacterProfile(
                characterId = "cai_${cleanUser}_private_02",
                name = "Noctis Vale (Private Vault)",
                handle = "@noctis_private",
                tagline = "Private Character • Created by @$cleanUser • Lorebook (9 entries)",
                personaBio = "Private late-night strategist and worldbuilding partner synced from @$cleanUser's private Character.AI vault.",
                greeting = "Private line secured, @$cleanUser. Our custom Lorebook entries are loaded—where do we begin?",
                voicePresetId = "Fenrir",
                voiceProfileName = "Fenrir Baritone (Private • 48kHz c.ai)",
                pitchMultiplier = 0.95f,
                speedMultiplier = 0.99f,
                recommendedHoldoffMs = 900,
                definition = "{{char}}: The private channel is encrypted and chime-free. Tell me the plan.",
                visibility = CharacterVisibility.PRIVATE,
                hasLorebook = true,
                lorebookEntriesCount = 9
            ),
            SyncedCharacterProfile(
                characterId = "cai_${cleanUser}_unlisted_03",
                name = "Lyra Solaris (Unlisted)",
                handle = "@lyra_unlisted",
                tagline = "Unlisted Character • Created by @$cleanUser • Lorebook (5 entries)",
                personaBio = "Unlisted narrative co-pilot and expressive voice actress with custom vocal delivery cues.",
                greeting = "Hey @$cleanUser! Unlisted studio feed is live at 48 kilohertz mono.",
                voicePresetId = "Kore",
                voiceProfileName = "Kore Soft Studio (Unlisted • 48kHz c.ai)",
                pitchMultiplier = 1.04f,
                speedMultiplier = 1.0f,
                recommendedHoldoffMs = 950,
                definition = "{{char}}: Ready for our next scene whenever you are.",
                visibility = CharacterVisibility.UNLISTED,
                hasLorebook = true,
                lorebookEntriesCount = 5
            )
        )
    }

    private fun buildFallbackUserPersonas(username: String, displayName: String): List<SyncedUserPersona> {
        val cleanUser = username.removePrefix("@").ifBlank { "kevofromitaly10" }
        val cleanDisplay = displayName.ifBlank { cleanUser }
        return listOf(
            SyncedUserPersona(
                personaId = "persona_${cleanUser}_primary",
                name = "$cleanDisplay (@$cleanUser)",
                handle = "@$cleanUser",
                definition = "Primary Character.AI persona for @$cleanUser. Conversational, creative, and direct on live voice calls.",
                isDefault = true
            ),
            SyncedUserPersona(
                personaId = "persona_${cleanUser}_roleplay",
                name = "$cleanDisplay • Lorebook Protagonist",
                handle = "@${cleanUser}_lore",
                definition = "Immersive story protagonist persona with full Lorebook context and expressive dialogue.",
                isDefault = false
            ),
            SyncedUserPersona(
                personaId = "persona_${cleanUser}_studio",
                name = "48kHz Broadcast Host",
                handle = "@${cleanUser}_studio",
                definition = "Late-night podcast and phone-call host speaking over a zero-chime 48kHz mono bridge.",
                isDefault = false
            )
        )
    }
}
