package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

sealed interface GoogleAuthOutcome {
    data class Success(val user: FirebaseUser) : GoogleAuthOutcome
    data class Cancelled(val message: String) : GoogleAuthOutcome
    data class Failure(val message: String) : GoogleAuthOutcome
}

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

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

    /**
     * Apple ID Sign-In via Firebase OAuthProvider ("apple.com").
     * Supports either an explicit Apple ID JWT token or interactive OAuth flow when Activity context is provided.
     */
    suspend fun signInWithAppleProvider(
        activityContext: Context?,
        appleIdToken: String = "",
        rawNonce: String = ""
    ): GoogleAuthOutcome {
        return try {
            if (appleIdToken.isNotBlank() && appleIdToken.count { it == '.' } >= 2) {
                val credentialBuilder = OAuthProvider.newCredentialBuilder("apple.com")
                if (rawNonce.isNotBlank()) {
                    credentialBuilder.setIdTokenWithRawNonce(appleIdToken.trim(), rawNonce.trim())
                } else {
                    credentialBuilder.setIdToken(appleIdToken.trim())
                }
                val authResult = Firebase.auth.signInWithCredential(credentialBuilder.build()).await()
                val user = authResult.user
                if (user != null) {
                    return GoogleAuthOutcome.Success(user)
                }
            }

            val activity = activityContext?.findActivity()
            if (activity != null) {
                val providerBuilder = OAuthProvider.newBuilder("apple.com")
                    .setScopes(listOf("email", "name"))
                val pendingTask = Firebase.auth.pendingAuthResult
                val authResult = if (pendingTask != null) {
                    pendingTask.await()
                } else {
                    Firebase.auth.startActivityForSignInWithProvider(activity, providerBuilder.build()).await()
                }
                val user = authResult?.user
                if (user != null) {
                    GoogleAuthOutcome.Success(user)
                } else {
                    GoogleAuthOutcome.Failure("Apple ID sign-in returned no Firebase user.")
                }
            } else {
                GoogleAuthOutcome.Failure("Apple OAuth requires an active Activity or Apple ID JWT token.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Apple Firebase OAuth flow note: ${e.localizedMessage}")
            GoogleAuthOutcome.Failure("Apple OAuth: ${e.localizedMessage ?: "Provider not enabled in Firebase Console"}")
        }
    }

    /**
     * Custom Credential & Token Sign-In via Firebase Auth (`signInWithCustomToken` or custom `OAuthProvider`).
     * Supports Character.AI custom JWT tokens and OIDC credentials.
     */
    suspend fun signInWithCustomCredentialOrToken(
        customTokenOrJwt: String,
        customProviderId: String = "oidc.character_ai"
    ): GoogleAuthOutcome {
        val cleanToken = customTokenOrJwt.trim()
        if (cleanToken.isBlank()) {
            return GoogleAuthOutcome.Failure("No custom credential token provided.")
        }
        return try {
            // 1. If token is a Firebase Custom JWT (3-segment JWT), attempt signInWithCustomToken first
            if (cleanToken.count { it == '.' } == 2) {
                try {
                    val customAuthResult = Firebase.auth.signInWithCustomToken(cleanToken).await()
                    val user = customAuthResult.user
                    if (user != null) {
                        return GoogleAuthOutcome.Success(user)
                    }
                } catch (inner: Exception) {
                    Log.d(TAG, "Custom token not a direct Firebase custom token, attempting OIDC credential", inner)
                }
            }

            // 2. Attempt OAuthProvider credential with customProviderId
            val credential = OAuthProvider.newCredentialBuilder(customProviderId)
                .setIdToken(cleanToken)
                .build()
            val result = Firebase.auth.signInWithCredential(credential).await()
            val user = result.user
            if (user != null) {
                GoogleAuthOutcome.Success(user)
            } else {
                GoogleAuthOutcome.Failure("Custom credential authentication returned no user.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Custom credential Firebase auth note: ${e.localizedMessage}")
            GoogleAuthOutcome.Failure("Custom credential note: ${e.localizedMessage ?: "Verified locally"}")
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
