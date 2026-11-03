package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthProvider
import com.example.ui.StudioViewModel
import com.example.ui.components.CharacterAiWebViewLoginDialog
import com.example.ui.theme.OpusLightBorder
import com.example.ui.theme.OpusLightTextPrimary
import com.example.ui.theme.OpusLightTextSecondary
import com.example.ui.theme.OpusRed
import com.example.ui.theme.OpusWhiteBackground
import com.example.ui.theme.OpusWhiteSurface
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioViolet

@Composable
fun LoginScreen(
    viewModel: StudioViewModel,
    paddingValues: PaddingValues,
    onNavigateToHome: () -> Unit = {},
    onNavigateToAccountSync: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val showWebViewLogin by viewModel.showCaiWebViewLogin.collectAsState()

    var selectedProvider by rememberSaveable { mutableStateOf(AuthProvider.CHARACTER_AI) }
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
    var emailOrUsernameInput by rememberSaveable {
        mutableStateOf(accountState.emailOrUsername.ifBlank { "" })
    }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var characterHandleInput by rememberSaveable {
        mutableStateOf(accountState.caiLinkedUsername.ifBlank { "@aria_vance" })
    }
    var showPassword by remember { mutableStateOf(false) }

    val bgMain = if (isDarkMode) StudioBackground else OpusWhiteSurface
    val cardBg = if (isDarkMode) StudioSurface else OpusWhiteBackground
    val cardInnerBg = if (isDarkMode) Color(0xFF222634) else OpusWhiteSurface
    val borderCol = if (isDarkMode) StudioCardBorder else OpusLightBorder
    val textMain = if (isDarkMode) Color(0xFFF8FAFC) else OpusLightTextPrimary
    val textSub = if (isDarkMode) Color(0xFF94A3B8) else OpusLightTextSecondary

    if (showWebViewLogin) {
        CharacterAiWebViewLoginDialog(
            onDismiss = { viewModel.closeCaiWebViewLogin() },
            onTokenExtracted = { token, cookies ->
                passwordInput = token
                viewModel.onCaiWebViewTokenCaptured(token, cookies)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgMain)
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("login_screen_root"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Banner Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (accountState.isSignedIn) StudioGreen else OpusRed, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (accountState.isSignedIn) StudioGreen.copy(alpha = 0.18f)
                                    else OpusRed.copy(alpha = 0.16f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (accountState.isSignedIn) Icons.Default.VerifiedUser else Icons.Default.AccountCircle,
                                contentDescription = "Authentication Status",
                                tint = if (accountState.isSignedIn) StudioGreen else OpusRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (accountState.isSignedIn) {
                                    "Authenticated: ${accountState.displayName} (${accountState.caiLinkedUsername})"
                                } else {
                                    "Firebase Cloud & Character.AI Sign-In"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textMain
                            )
                            Text(
                                text = "${accountState.caiTokenStatusText} • ${accountState.cloudSyncStatus}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (accountState.isSignedIn) StudioGreen else textSub,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (accountState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = OpusRed,
                            strokeWidth = 2.5.dp
                        )
                    }
                }

                if (accountState.isSignedIn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(cardInnerBg)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = StudioGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Character: ${accountState.activeCharacter.name} • Persona: ${accountState.activePersona.name}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textMain
                                )
                                Text(
                                    text = "${accountState.activeCharacter.voiceProfileName} • ${accountState.activeCharacter.recommendedHoldoffMs}ms Chime Gate",
                                    fontSize = 11.sp,
                                    color = textSub
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onNavigateToHome,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("login_continue_home_button")
                        ) {
                            Text("Dashboard", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 1: Google Sign-In via Credential Manager & Firebase Auth
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderCol, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = OpusRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "1. Instant Google Sign-In (Firebase Auth)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textMain
                    )
                }

                Text(
                    text = "Authenticate with your Google account via Android Credential Manager to back up your Character.AI personas, voice presets, and 48kHz Chime-Blocker settings to Firebase Firestore.",
                    fontSize = 12.sp,
                    color = textSub
                )

                Button(
                    onClick = {
                        viewModel.signInWithGoogleCredentialManager(
                            activityContext = context,
                            characterHandleInput = characterHandleInput
                        )
                    },
                    enabled = !accountState.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_google_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OpusRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Continue with Google (Credential Manager)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        viewModel.signInWithAppleFirebase(
                            activityContext = context,
                            characterHandleInput = characterHandleInput,
                            appleIdEmailHint = emailOrUsernameInput,
                            appleIdToken = passwordInput
                        )
                    },
                    enabled = !accountState.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_apple_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = textMain,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Continue with Apple ID (Firebase OAuth)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textMain
                    )
                }

                if (!accountState.authErrorMessage.isNullOrBlank()) {
                    Text(
                        text = accountState.authErrorMessage ?: "",
                        fontSize = 11.sp,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.testTag("login_auth_error_text")
                    )
                }
            }
        }

        // Section 2: Email / Password & Provider Sign-In -> c.ai Direct Sync
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderCol, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = StudioViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "2. Email / Password & Provider Sign-In",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = textMain
                        )
                    }

                    FilterChip(
                        selected = isRegisterMode,
                        onClick = { isRegisterMode = !isRegisterMode },
                        label = {
                            Text(
                                text = if (isRegisterMode) "Mode: Sign Up" else "Mode: Sign In",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OpusRed.copy(alpha = 0.16f),
                            selectedLabelColor = OpusRed
                        ),
                        modifier = Modifier.testTag("login_toggle_mode_chip")
                    )
                }

                // Provider Selection Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuthProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = selectedProvider == provider,
                            onClick = { selectedProvider = provider },
                            label = {
                                Text(
                                    text = provider.badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OpusRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("login_provider_${provider.name.lowercase()}")
                        )
                    }
                }

                // PRIMARY BUTTON AT TOP: 1-Tap Sign In with Character.AI (WebView)
                Button(
                    onClick = {
                        selectedProvider = AuthProvider.CHARACTER_AI
                        viewModel.openCaiWebViewLogin()
                    },
                    enabled = !accountState.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_cai_webview_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OpusRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1-Tap Sign In with Character.AI (WebView)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("c.ai Session Token") },
                    placeholder = { Text("Manual c.ai Session Token (Backup Option)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide token" else "Show token"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                OutlinedTextField(
                    value = emailOrUsernameInput,
                    onValueChange = { emailOrUsernameInput = it },
                    label = {
                        Text(
                            when (selectedProvider) {
                                AuthProvider.GOOGLE_EMAIL -> "Google Email Address"
                                AuthProvider.APPLE_ID -> "Apple ID Email (e.g. user@icloud.com)"
                                AuthProvider.CHARACTER_AI -> "Character.AI Username or Email"
                            }
                        )
                    },
                    placeholder = { Text("kevofromitaly10 or creator@example.com") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_email_input")
                )

                OutlinedTextField(
                    value = characterHandleInput,
                    onValueChange = { characterHandleInput = it },
                    label = { Text("Auto-Sync Character.AI @Handle on Login") },
                    placeholder = { Text("@aria_vance") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_character_handle_input")
                )

                Button(
                    onClick = {
                        viewModel.authenticateAndSyncAccount(
                            provider = selectedProvider,
                            identifier = emailOrUsernameInput,
                            secretOrToken = passwordInput,
                            characterHandleInput = characterHandleInput,
                            isSignUp = isRegisterMode,
                            activityContext = context
                        )
                    },
                    enabled = !accountState.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_email_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioViolet,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRegisterMode) {
                            "Create Account & Auto-Sync Character"
                        } else {
                            "Sign In with ${selectedProvider.badgeText} & Sync Character"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider(color = borderCol)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToAccountSync,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("login_open_character_sync_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Manage Synced Personas (${accountState.syncedPersonas.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (accountState.isSignedIn) {
                        OutlinedButton(
                            onClick = { viewModel.signOutAccount(context) },
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("login_sign_out_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Sign Out",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sign Out",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
