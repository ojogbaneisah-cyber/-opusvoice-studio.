package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AVAILABLE_VOICES
import com.example.data.model.AuthProvider
import com.example.ui.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.theme.OpusLightBorder
import com.example.ui.theme.OpusLightTextPrimary
import com.example.ui.theme.OpusLightTextSecondary
import com.example.ui.theme.OpusRed
import com.example.ui.theme.OpusRedLight
import com.example.ui.theme.OpusWhiteBackground
import com.example.ui.theme.OpusWhiteSurface
import com.example.ui.theme.StudioGreen
import java.util.Locale

private enum class AccountSubView(val title: String) {
    AUTH_PORTAL("Sign In / Sign Up"),
    SYNCED_ROSTER("Synced Characters"),
    IMPORT_CHARACTER("Sync New Character")
}

@Composable
fun AccountSyncScreen(
    viewModel: StudioViewModel,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val accountState by viewModel.accountState.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()

    val bgCard = if (isDark) Color(0xFF181B24) else OpusWhiteBackground
    val bgSurface = if (isDark) Color(0xFF222634) else OpusWhiteSurface
    val borderCol = if (isDark) Color(0xFF2E3446) else OpusLightBorder
    val textMain = if (isDark) Color(0xFFF8FAFC) else OpusLightTextPrimary
    val textSub = if (isDark) Color(0xFF94A3B8) else OpusLightTextSecondary

    var activeSubView by remember { mutableStateOf(AccountSubView.AUTH_PORTAL) }
    var selectedProvider by remember { mutableStateOf(accountState.provider) }
    var isSignUpMode by remember { mutableStateOf(false) }

    var identifierInput by remember(accountState.emailOrUsername) {
        mutableStateOf(accountState.emailOrUsername)
    }
    var passwordOrTokenInput by remember { mutableStateOf("") }
    var characterHandleInput by remember(accountState.caiLinkedUsername) {
        mutableStateOf(accountState.caiLinkedUsername)
    }

    var importHandleOrUrl by remember { mutableStateOf("") }
    var importPersonaBio by remember { mutableStateOf("") }
    var importVoiceId by remember { mutableStateOf("Aoede") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Segmented Sub-Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgCard)
                .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AccountSubView.entries.forEach { tab ->
                val selected = activeSubView == tab
                val label = when (tab) {
                    AccountSubView.AUTH_PORTAL -> "Sign In / Up"
                    AccountSubView.SYNCED_ROSTER -> "Synced Roster (${accountState.syncedCharacters.size})"
                    AccountSubView.IMPORT_CHARACTER -> "Sync c.ai ID"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) OpusRed else Color.Transparent)
                        .clickable { activeSubView = tab }
                        .padding(vertical = 10.dp, horizontal = 6.dp)
                        .testTag("account_subtab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else textSub,
                        maxLines = 1
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Active Sync Status & Firebase Cloud Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.5.dp,
                        color = if (accountState.isSignedIn) StudioGreen else borderCol,
                        shape = RoundedCornerShape(16.dp)
                    ),
                color = bgCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
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
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (accountState.isSignedIn) StudioGreen.copy(alpha = 0.2f)
                                        else OpusRed.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (accountState.isSignedIn) Icons.Default.VerifiedUser else Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = if (accountState.isSignedIn) StudioGreen else OpusRed,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (accountState.isSignedIn) {
                                        "${accountState.displayName} • ${accountState.provider.label}"
                                    } else {
                                        "Not Signed In • Local Studio Session"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = textMain
                                )
                                Text(
                                    text = "Active Persona: ${accountState.activeCharacter.name} (${accountState.activeCharacter.voiceProfileName})",
                                    fontSize = 11.sp,
                                    color = if (accountState.isSignedIn) StudioGreen else textSub,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = accountState.cloudSyncStatus,
                                    fontSize = 10.sp,
                                    color = textSub
                                )
                            }
                        }

                        if (accountState.isSyncing) {
                            CircularProgressIndicator(
                                color = OpusRed,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (accountState.isSignedIn) {
                            OutlinedButton(
                                onClick = { viewModel.signOutAccount(context) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OpusRed),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("account_sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sign Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (accountState.firebaseUid.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = StudioGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Firebase UID: ${accountState.firebaseUid.take(12)}...",
                                    fontSize = 10.sp,
                                    color = StudioGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.syncNowWithCloud() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioGreen),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("account_cloud_sync_now_button")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Cloud Now", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            when (activeSubView) {
                AccountSubView.AUTH_PORTAL -> {
                    // 1. GOOGLE SIGN-IN VIA ANDROID CREDENTIAL MANAGER + FIREBASE CLOUD SYNC CARD
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, OpusRed, RoundedCornerShape(16.dp)),
                        color = bgCard
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "FIREBASE AUTH & GOOGLE CREDENTIAL MANAGER",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "One-tap Google Sign-In via Android Credential Manager backs up your Character.AI personas, voice configurations, and Chime-Blocker settings to Firebase Cloud Firestore.",
                                fontSize = 11.sp,
                                color = textSub
                            )

                            Button(
                                onClick = {
                                    viewModel.signInWithGoogleCredentialManager(
                                        activityContext = context,
                                        characterHandleInput = characterHandleInput
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OpusRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("google_credential_manager_signin_button")
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Continue with Google (Credential Manager)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // 2. MULTI-PROVIDER SIGN IN / SIGN UP CARD (Character.AI, Google Email, Apple ID)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, borderCol, RoundedCornerShape(16.dp)),
                        color = bgCard
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "MULTI-PROVIDER ACCOUNT & CHARACTER.AI AUTO-SYNC",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OpusRed,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Sign in or register with Apple ID, Google, or Character.AI credentials to automatically synchronize your character's personal details and 48kHz voice profile.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSub,
                                    fontSize = 11.sp
                                )
                            }

                            // Sign In vs Sign Up Mode Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgSurface)
                                    .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(false to "Sign In to Account", true to "Create New Account (Sign Up)").forEach { (signUpFlag, label) ->
                                    val active = isSignUpMode == signUpFlag
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) OpusRed else Color.Transparent)
                                            .clickable { isSignUpMode = signUpFlag }
                                            .padding(vertical = 8.dp)
                                            .testTag(if (signUpFlag) "auth_mode_signup" else "auth_mode_signin"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color.White else textSub
                                        )
                                    }
                                }
                            }

                            // Provider Selector Buttons: Google Account, Character.AI Credentials, Apple ID
                            Text(
                                text = "SELECT AUTHENTICATION METHOD:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSub
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AuthProvider.entries.forEach { provider ->
                                    val isSelected = selectedProvider == provider
                                    val providerIconText = when (provider) {
                                        AuthProvider.CHARACTER_AI -> "c.ai"
                                        AuthProvider.GOOGLE_EMAIL -> "G"
                                        AuthProvider.APPLE_ID -> ""
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) {
                                                    if (isDark) Color(0xFF4C0519) else OpusRedLight
                                                } else bgSurface
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) OpusRed else borderCol,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedProvider = provider }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                            .testTag("auth_provider_${provider.name.lowercase()}"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) OpusRed else borderCol),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = providerIconText,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = provider.label,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = textMain
                                                )
                                                Text(
                                                    text = when (provider) {
                                                        AuthProvider.CHARACTER_AI -> "Direct Character.AI username/email & automatic persona + voice sync"
                                                        AuthProvider.GOOGLE_EMAIL -> "Sign in with Google & auto-sync linked Character.AI profile"
                                                        AuthProvider.APPLE_ID -> "Sign in with Apple ID & auto-sync linked Character.AI profile"
                                                    },
                                                    fontSize = 10.sp,
                                                    color = textSub
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = OpusRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = borderCol)

                            // Credentials Input Fields
                            OutlinedTextField(
                                value = identifierInput,
                                onValueChange = { identifierInput = it },
                                label = {
                                    Text(
                                        when (selectedProvider) {
                                            AuthProvider.CHARACTER_AI -> "Character.AI Username or Email"
                                            AuthProvider.GOOGLE_EMAIL -> "Google Email Address (e.g. you@gmail.com)"
                                            AuthProvider.APPLE_ID -> "Apple ID Email (e.g. you@icloud.com)"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = OpusRed)
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = bgSurface,
                                    unfocusedContainerColor = bgSurface,
                                    focusedBorderColor = OpusRed,
                                    unfocusedBorderColor = borderCol,
                                    focusedTextColor = textMain,
                                    unfocusedTextColor = textMain
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_identifier_input")
                            )

                            OutlinedTextField(
                                value = passwordOrTokenInput,
                                onValueChange = { passwordOrTokenInput = it },
                                label = {
                                    Text(
                                        when (selectedProvider) {
                                            AuthProvider.CHARACTER_AI -> "Character.AI Password or Session Token"
                                            AuthProvider.GOOGLE_EMAIL -> "Google Account Password / App Passcode"
                                            AuthProvider.APPLE_ID -> "Apple ID Password / Passkey"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = OpusRed)
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = bgSurface,
                                    unfocusedContainerColor = bgSurface,
                                    focusedBorderColor = OpusRed,
                                    unfocusedBorderColor = borderCol,
                                    focusedTextColor = textMain,
                                    unfocusedTextColor = textMain
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_secret_input")
                            )

                            OutlinedTextField(
                                value = characterHandleInput,
                                onValueChange = { characterHandleInput = it },
                                label = { Text("Character.AI @handle or Character Name to Auto-Sync (Optional)") },
                                placeholder = { Text("e.g. @aria_vance or character.ai/chat/...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = OpusRed)
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = bgSurface,
                                    unfocusedContainerColor = bgSurface,
                                    focusedBorderColor = OpusRed,
                                    unfocusedBorderColor = borderCol,
                                    focusedTextColor = textMain,
                                    unfocusedTextColor = textMain
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_character_handle_input")
                            )

                            Button(
                                onClick = {
                                    viewModel.authenticateAndSyncAccount(
                                        provider = selectedProvider,
                                        identifier = identifierInput,
                                        secretOrToken = passwordOrTokenInput,
                                        characterHandleInput = characterHandleInput,
                                        isSignUp = isSignUpMode
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OpusRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("auth_submit_sync_button")
                            ) {
                                Icon(
                                    imageVector = if (isSignUpMode) Icons.Default.PersonAdd else Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSignUpMode) {
                                        "Sign Up with ${selectedProvider.label} & Auto-Sync"
                                    } else {
                                        "Sign In with ${selectedProvider.label} & Auto-Sync"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Currently Synced Active Character Details Card
                    val activeChar = accountState.activeCharacter
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, borderCol, RoundedCornerShape(16.dp)),
                        color = bgCard
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
                                Text(
                                    text = "ACTIVE SYNCED CHARACTER & VOICE PROFILE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OpusRed,
                                    letterSpacing = 0.8.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(StudioGreen.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "AUTO-SYNCED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StudioGreen
                                    )
                                }
                            }

                            Text(
                                text = "${activeChar.name} (${activeChar.handle})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = textMain
                            )
                            Text(
                                text = activeChar.tagline,
                                fontSize = 12.sp,
                                color = OpusRed,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = activeChar.personaBio,
                                fontSize = 12.sp,
                                color = textSub
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgSurface)
                                    .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = OpusRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Voice Profile: ${activeChar.voiceProfileName}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textMain
                                        )
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "Pitch %.2fx • Speed %.2fx • Chime Holdoff %dms",
                                                activeChar.pitchMultiplier,
                                                activeChar.speedMultiplier,
                                                activeChar.recommendedHoldoffMs
                                            ),
                                            fontSize = 10.sp,
                                            color = textSub
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.setTab(StudioTab.CAI_CALL) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OpusRed,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open Call", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                AccountSubView.SYNCED_ROSTER -> {
                    // SYNCED CHARACTER.AI ROSTER LIST
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, borderCol, RoundedCornerShape(16.dp)),
                        color = bgCard
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "SYNCED CHARACTER.AI ROSTER",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Tap any synced character below to immediately load their personal details, greeting, and 48kHz voice profile into the Call Bridge.",
                                fontSize = 11.sp,
                                color = textSub
                            )

                            accountState.syncedCharacters.forEach { character ->
                                val isCurrent = character.characterId == accountState.activeCharacter.characterId
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isCurrent) bgSurface else bgCard)
                                        .border(
                                            width = if (isCurrent) 1.5.dp else 1.dp,
                                            color = if (isCurrent) OpusRed else borderCol,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.selectActiveSyncedCharacter(character) }
                                        .padding(12.dp)
                                        .testTag("roster_char_${character.characterId}"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${character.name} (${character.handle})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = textMain
                                            )
                                            Text(
                                                text = character.tagline,
                                                fontSize = 11.sp,
                                                color = OpusRed,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isCurrent) OpusRed else borderCol)
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = if (isCurrent) "ACTIVE" else "USE PROFILE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Text(
                                        text = character.personaBio,
                                        fontSize = 11.sp,
                                        color = textSub,
                                        maxLines = 2
                                    )
                                    Text(
                                        text = "Voice: ${character.voiceProfileName} • Holdoff: ${character.recommendedHoldoffMs}ms",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCurrent) StudioGreen else textSub
                                    )
                                }
                            }
                        }
                    }
                }

                AccountSubView.IMPORT_CHARACTER -> {
                    // SYNC ANY CHARACTER BY HANDLE OR URL
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, borderCol, RoundedCornerShape(16.dp)),
                        color = bgCard
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "SYNC CHARACTER BY @HANDLE OR C.AI LINK",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Paste a Character.AI link (character.ai/chat/...) or @handle to pull its personal details and bind a 48kHz voice profile.",
                                fontSize = 11.sp,
                                color = textSub
                            )

                            OutlinedTextField(
                                value = importHandleOrUrl,
                                onValueChange = { importHandleOrUrl = it },
                                label = { Text("Character.AI @handle, Name, or Share URL") },
                                placeholder = { Text("e.g. @raiden_shogun or https://character.ai/chat/...", fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = bgSurface,
                                    unfocusedContainerColor = bgSurface,
                                    focusedBorderColor = OpusRed,
                                    unfocusedBorderColor = borderCol,
                                    focusedTextColor = textMain,
                                    unfocusedTextColor = textMain
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_character_handle_input")
                            )

                            OutlinedTextField(
                                value = importPersonaBio,
                                onValueChange = { importPersonaBio = it },
                                label = { Text("Character Personal Details / Backstory (Optional)") },
                                placeholder = { Text("Leave blank to auto-generate from handle, or paste character bio...", fontSize = 12.sp) },
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = bgSurface,
                                    unfocusedContainerColor = bgSurface,
                                    focusedBorderColor = OpusRed,
                                    unfocusedBorderColor = borderCol,
                                    focusedTextColor = textMain,
                                    unfocusedTextColor = textMain
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "MATCHED 48KHZ VOICE TIMBRE:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSub
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AVAILABLE_VOICES.forEach { voice ->
                                    val selected = importVoiceId == voice.id
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) OpusRed else bgSurface)
                                            .border(1.dp, if (selected) OpusRed else borderCol, RoundedCornerShape(10.dp))
                                            .clickable { importVoiceId = voice.id }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "${voice.iconEmoji} ${voice.name}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else textMain
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.syncNewCharacterByHandleOrUrl(
                                        rawHandleOrUrl = importHandleOrUrl,
                                        customPersonaHint = importPersonaBio,
                                        preferredVoiceId = importVoiceId
                                    )
                                    importHandleOrUrl = ""
                                    importPersonaBio = ""
                                    activeSubView = AccountSubView.SYNCED_ROSTER
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OpusRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("sync_import_character_button")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Character Details & Voice Profile", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
