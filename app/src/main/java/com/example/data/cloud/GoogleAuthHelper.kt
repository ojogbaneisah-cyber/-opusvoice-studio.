package com.example.data.cloud

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

sealed interface GoogleAuthOutcome {
    data class Success(val user: FirebaseUser) : GoogleAuthOutcome
    data class Cancelled(val message: String) : GoogleAuthOutcome
    data class Failure(val message: String) : GoogleAuthOutcome
}

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    /**
     * Interactive sign-in invoked when the user taps the Google Sign-In button.
     * Uses exclusively GetSignInWithGoogleOption in a single-option request.
     */
    suspend fun signInWithGoogleInteractive(activityContext: Context): GoogleAuthOutcome {
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val webClientId = activityContext.getString(R.string.default_web_client_id)

            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
                serverClientId = webClientId
            ).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user
                if (user != null) {
                    GoogleAuthOutcome.Success(user)
                } else {
                    GoogleAuthOutcome.Failure("Firebase authentication returned no user.")
                }
            } else {
                GoogleAuthOutcome.Failure("Unsupported credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(
                TAG,
                "Google Sign-In cancelled (verify SHA-1/SHA-256 fingerprint registration in google-services.json if unexpected)",
                e
            )
            GoogleAuthOutcome.Cancelled("Sign-in was cancelled. Please select a Google account to continue.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error during Google Sign-In", e)
            GoogleAuthOutcome.Failure("Google Sign-In failed: ${e.localizedMessage ?: "Credential error"}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In", e)
            GoogleAuthOutcome.Failure("Authentication error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Silent auto sign-in for returning users on cold launch.
     * Uses exclusively GetGoogleIdOption in a separate single-option request.
     */
    suspend fun attemptSilentGoogleSignIn(activityContext: Context): FirebaseUser? {
        if (Firebase.auth.currentUser != null) return Firebase.auth.currentUser
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val webClientId = activityContext.getString(R.string.default_web_client_id)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                Firebase.auth.signInWithCredential(firebaseCredential).await().user
            } else {
                null
            }
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorized Google credentials stored for silent sign-in")
            null
        } catch (e: GetCredentialException) {
            Log.d(TAG, "Silent sign-in skipped: ${e.localizedMessage}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Silent sign-in error", e)
            null
        }
    }

    suspend fun signOut(context: Context) {
        Firebase.auth.signOut()
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            Log.w(TAG, "Failed to clear CredentialManager state", e)
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing credential state", e)
        }
    }
}
