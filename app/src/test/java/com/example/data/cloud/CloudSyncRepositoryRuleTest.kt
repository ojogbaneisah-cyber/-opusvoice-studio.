package com.example.data.cloud

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AuthProvider
import com.example.data.model.StudioSettingsConfig
import com.example.data.model.SyncedCharacterProfile
import com.example.data.model.UserAccountState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.UUID

@Config(sdk = [36])
class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndGetUserAccount_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)

        val accountState = UserAccountState(
            isSignedIn = true,
            provider = AuthProvider.GOOGLE_EMAIL,
            displayName = "Alice Vance",
            emailOrUsername = ALICE_EMAIL,
            caiLinkedUsername = "@aria_vance",
            activeCharacterId = "cai_aria_01"
        )
        val settings = StudioSettingsConfig(defaultMuteWindowMs = 950)

        val writeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserAccountAndSettings(aliceUid, accountState, settings)
        }
        assertTrue(writeResult.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserAccount(aliceUid).getOrThrow()
        }
        assertNotNull(fetched)
        assertEquals("Alice Vance", fetched?.displayName)
        assertEquals(950L, fetched?.defaultMuteWindowMs)
    }

    @Test
    fun upsertAndObserveCharacterProfiles_authenticatedOwner_emitsSyncedProfile() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)
        val uniqueCharId = "cai_test_${UUID.randomUUID().toString().take(8)}"

        val profile = SyncedCharacterProfile(
            characterId = uniqueCharId,
            name = "Aria Vance",
            handle = "@aria_vance",
            tagline = "Empathetic Call Companion",
            personaBio = "Warm, natural speaking voice on calls.",
            greeting = "Hey! I'm on the line.",
            voicePresetId = "Aoede",
            voiceProfileName = "Aoede Velvet (48kHz)",
            recommendedHoldoffMs = 950
        )

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertCharacterProfile(aliceUid, profile)
        }
        assertTrue(saveResult.isSuccess)

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCharacterProfiles(aliceUid).first { list ->
                list.any { it.characterId == uniqueCharId }
            }
        }
        assertTrue(emitted.any { it.characterId == uniqueCharId && it.name == "Aria Vance" })
    }

    @Test
    fun getUserAccount_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = CloudSyncRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserAccountAndSettings(
                aliceUid,
                UserAccountState(isSignedIn = true, displayName = "Alice"),
                StudioSettingsConfig()
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserAccount(aliceUid)
        }
        assertTrue(crossReadResult.isFailure)
        val firestoreEx = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeCharacterProfiles_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = CloudSyncRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCharacterProfiles(aliceUid).first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull(caughtException)
        val firestoreEx = caughtException?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@opusvoice.test"
        const val BOB_EMAIL = "bob@opusvoice.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
