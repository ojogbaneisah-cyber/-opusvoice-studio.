package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.data.model.AuthProvider
import com.example.data.model.StudioSettingsConfig
import com.example.data.model.SyncedCharacterProfile
import com.example.data.model.UserAccountState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@IgnoreExtraProperties
data class CloudUserAccountDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val authProvider: String = "GOOGLE_EMAIL",
    val caiLinkedUsername: String = "",
    val activeCharacterId: String = "cai_aria_01",
    val defaultMuteWindowMs: Long = 950L,
    val defaultSampleRateHz: Long = 48000L,
    val defaultMonoChannel: Boolean = true,
    val caiIntegrationEnabled: Boolean = true,
    val whatsappIntegrationEnabled: Boolean = true,
    val telegramIntegrationEnabled: Boolean = true,
    val googleChatIntegrationEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudCharacterProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val characterId: String = "",
    val name: String = "",
    val handle: String = "",
    val tagline: String = "",
    val personaBio: String = "",
    val greeting: String = "",
    val voicePresetId: String = "Aoede",
    val voiceProfileName: String = "Aoede Velvet (48kHz c.ai Voice)",
    val recommendedHoldoffMs: Long = 950L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDomainModel(): SyncedCharacterProfile = SyncedCharacterProfile(
        characterId = characterId.ifBlank { id },
        name = name.ifBlank { "Aria Vance" },
        handle = handle.ifBlank { "@aria_vance" },
        tagline = tagline.ifBlank { "Synced Character.AI Profile" },
        personaBio = personaBio.ifBlank { "Natural phone call companion." },
        greeting = greeting.ifBlank { "Hey! I'm on the line." },
        voicePresetId = voicePresetId.ifBlank { "Aoede" },
        voiceProfileName = voiceProfileName.ifBlank { "Aoede Velvet (48kHz c.ai Voice)" },
        pitchMultiplier = 1.0f,
        speedMultiplier = 1.0f,
        recommendedHoldoffMs = recommendedHoldoffMs.toInt().coerceIn(300, 2200)
    )
}

class CloudSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeUserAccount(userId: String): Flow<CloudUserAccountDoc?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots().map { snapshot ->
            if (snapshot.exists()) {
                snapshot.toObject(
                    CloudUserAccountDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
        }
    }

    fun observeCharacterProfiles(userId: String): Flow<List<SyncedCharacterProfile>> {
        val colRef = db.collection("users").document(userId).collection("characterProfiles")
        return colRef
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    CloudCharacterProfileDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { it.toDomainModel() }
            }
    }

    suspend fun getUserAccount(userId: String): Result<CloudUserAccountDoc?> = runCatching {
        val docRef = db.collection("users").document(userId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(
                    CloudUserAccountDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun upsertUserAccountAndSettings(
        userId: String,
        accountState: UserAccountState,
        settings: StudioSettingsConfig
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(userId)
        try {
            val existingSnap = docRef.get().await()
            val safeDisplayName = accountState.displayName.trim().ifBlank { "Studio Creator" }.take(120)
            val safeEmail = accountState.emailOrUsername.trim().take(200)
            val safeCaiHandle = accountState.caiLinkedUsername.trim().take(100)
            val safeActiveCharId = accountState.activeCharacterId
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .ifBlank { "cai_aria_01" }
                .take(128)
            val validSampleRate = when (settings.defaultSampleRateHz) {
                24000, 44100, 48000 -> settings.defaultSampleRateHz
                else -> 48000
            }

            if (!existingSnap.exists()) {
                val createPayload = mapOf(
                    "userId" to userId,
                    "displayName" to safeDisplayName,
                    "email" to safeEmail,
                    "authProvider" to accountState.provider.name,
                    "caiLinkedUsername" to safeCaiHandle,
                    "activeCharacterId" to safeActiveCharId,
                    "defaultMuteWindowMs" to settings.defaultMuteWindowMs.coerceIn(300, 2200),
                    "defaultSampleRateHz" to validSampleRate,
                    "defaultMonoChannel" to settings.defaultMonoChannel,
                    "caiIntegrationEnabled" to settings.caiIntegrationEnabled,
                    "whatsappIntegrationEnabled" to settings.whatsappIntegrationEnabled,
                    "telegramIntegrationEnabled" to settings.telegramIntegrationEnabled,
                    "googleChatIntegrationEnabled" to settings.googleChatIntegrationEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "displayName" to safeDisplayName,
                    "email" to safeEmail,
                    "authProvider" to accountState.provider.name,
                    "caiLinkedUsername" to safeCaiHandle,
                    "activeCharacterId" to safeActiveCharId,
                    "defaultMuteWindowMs" to settings.defaultMuteWindowMs.coerceIn(300, 2200),
                    "defaultSampleRateHz" to validSampleRate,
                    "defaultMonoChannel" to settings.defaultMonoChannel,
                    "caiIntegrationEnabled" to settings.caiIntegrationEnabled,
                    "whatsappIntegrationEnabled" to settings.whatsappIntegrationEnabled,
                    "telegramIntegrationEnabled" to settings.telegramIntegrationEnabled,
                    "googleChatIntegrationEnabled" to settings.googleChatIntegrationEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun upsertCharacterProfile(
        userId: String,
        profile: SyncedCharacterProfile
    ): Result<String> = runCatching {
        val safeCharId = profile.characterId
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .ifBlank { "cai_custom" }
            .take(128)
        val docRef = db.collection("users")
            .document(userId)
            .collection("characterProfiles")
            .document(safeCharId)

        try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val createPayload = mapOf(
                    "userId" to userId,
                    "characterId" to safeCharId,
                    "name" to profile.name.trim().ifBlank { "Character" }.take(120),
                    "handle" to profile.handle.trim().ifBlank { "@character" }.take(100),
                    "tagline" to profile.tagline.trim().take(200),
                    "personaBio" to profile.personaBio.trim().take(1000),
                    "greeting" to profile.greeting.trim().take(500),
                    "voicePresetId" to profile.voicePresetId.trim().ifBlank { "Aoede" }.take(64),
                    "voiceProfileName" to profile.voiceProfileName.trim().ifBlank { "Aoede 48kHz" }.take(120),
                    "recommendedHoldoffMs" to profile.recommendedHoldoffMs.coerceIn(300, 2200),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "name" to profile.name.trim().ifBlank { "Character" }.take(120),
                    "handle" to profile.handle.trim().ifBlank { "@character" }.take(100),
                    "tagline" to profile.tagline.trim().take(200),
                    "personaBio" to profile.personaBio.trim().take(1000),
                    "greeting" to profile.greeting.trim().take(500),
                    "voicePresetId" to profile.voicePresetId.trim().ifBlank { "Aoede" }.take(64),
                    "voiceProfileName" to profile.voiceProfileName.trim().ifBlank { "Aoede 48kHz" }.take(120),
                    "recommendedHoldoffMs" to profile.recommendedHoldoffMs.coerceIn(300, 2200),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            safeCharId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getCharacterProfiles(userId: String): Result<List<SyncedCharacterProfile>> = runCatching {
        val colRef = db.collection("users").document(userId).collection("characterProfiles")
        try {
            val snap = colRef.whereEqualTo("userId", userId).get().await()
            snap.toObjects(
                CloudCharacterProfileDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.toDomainModel() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }
}
