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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import com.example.data.model.CharacterVisibility
import com.example.ui.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.components.CharacterAiWebViewLoginDialog
import com.example.ui.theme.OpusLightBorder
import com.example.ui.theme.OpusLightTextPrimary
import com.example.ui.theme.OpusLightTextSecondary
import com.example.ui.theme.OpusRed
import com.example.ui.theme.OpusRedLight
import com.example.ui.theme.OpusWhiteBackground
import com.example.ui.theme.OpusWhiteSurface
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioGreen
import java.util.Locale

private enum class AccountSubView(val title: String) {
    AUTH_PORTAL("Sign In / Sign Up"),
    SYNCED_ROSTER("Synced Characters"),
    MANAGE_PERSONAS("Manage Personas"),
    IMPORT_CHARACTER("Sync New Character")
}

@Composable
fun AccountSyncScreen(
    viewModel: StudioViewModel,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val accountState by viewModel.accountState.collectAsState()
    val showWebViewLogin by viewModel.showCaiWebViewLogin.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()

    val bgCard = if (isDark) Color(0xFF181B24) else OpusWhiteBackground
    val bgSurface = if (isDark) Color(0xFF222634) else OpusWhiteSurface
    val borderCol = if (isDark) Color(0xFF2E3446) else OpusLightBorder
    val textMain = if (isDark) Color(0xFFF8FAFC) else OpusLightTextPrimary
    val textSub = if (isDark) Color(0xFF94A3B8) else OpusLightTextSecondary

    var activeSubView by remember { mutableStateOf(AccountSubView.AUTH_PORTAL) }
    var selectedProvider by remember { mutableStateOf(AuthProvider.CHARACTER_AI) }
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

    var customPersonaName by remember { mutableStateOf("") }
    var customPersonaHandle by remember { mutableStateOf("") }
    var customPersonaDefinition by remember { mutableStateOf("") }

    if (showWebViewLogin) {
        CharacterAiWebViewLoginDialog(
            onDismiss = { viewModel.closeCaiWebViewLogin() },
            onTokenExtracted = { token, cookies ->
                passwordOrTokenInput = token
                viewModel.onCaiWebViewTokenCaptured(token, cookies)
            }
        )
    }

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
                    AccountSubView.AUTH_PORTAL -> "Sign In"
                    AccountSubView.SYNCED_ROSTER -> "Chars (${accountState.syncedCharacters.size})"
                    AccountSubView.MANAGE_PERSONAS -> "Personas (${accountState.syncedPersonas.size})"
                    AccountSubView.IMPORT_CHARACTER -> "Sync ID"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) OpusRed else Color.Transparent)
                        .clickable { activeSubView = tab }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("account_subtab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
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
                                        "${accountState.displayName} (${accountState.caiLinkedUsername.ifBlank { "@user" }}) • ${accountState.provider.badgeText}"
                                    } else {
                                        "Not Signed In • Local Studio Session"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = textMain
                                )
                                Text(
                                    text = "Character: ${accountState.activeCharacter.name} • Persona: ${accountState.activePersona.name}",
                                    fontSize = 11.sp,
                                    color = if (accountState.isSignedIn) StudioGreen else textSub,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${accountState.caiTokenStatusText} • ${accountState.cloudSyncStatus}",
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
                                text = "1. FIREBASE AUTH & GOOGLE CREDENTIAL MANAGER",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "One-tap Google Sign-In via Android Credential Manager backs up your synced Character.AI metadata, personas, and 48kHz Chime-Blocker presets to Firebase Cloud Firestore.",
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

                    // 2. EMAIL / PASSWORD & PROVIDER SIGN-IN -> C.AI DIRECT SYNC (WITH 1-TAP WEBVIEW LOGIN)
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
                                    text = "2. EMAIL / PASSWORD & PROVIDER SIGN-IN",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OpusRed,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Use 1-Tap Character.AI WebView Login to automatically capture your session token into EncryptedSharedPreferences and sync your real handle, characters (Public, Unlisted, Private + Lorebooks), and Personas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSub,
                                    fontSize = 11.sp
                                )
                            }

                            // Provider Selector Pills (c.ai Direct Sync highlighted)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AuthProvider.entries.forEach { provider ->
                                    val isSelected = selectedProvider == provider
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) OpusRed else bgSurface)
                                            .border(
                                                1.dp,
                                                if (isSelected) OpusRed else borderCol,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { selectedProvider = provider }
                                            .padding(vertical = 10.dp, horizontal = 6.dp)
                                            .testTag("auth_provider_${provider.name.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = provider.badgeText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else textSub,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // PRIMARY BUTTON AT TOP OF C.AI DIRECT SYNC: 1-Tap Sign In with Character.AI (WebView)
                            Button(
                                onClick = {
                                    selectedProvider = AuthProvider.CHARACTER_AI
                                    viewModel.openCaiWebViewLogin()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OpusRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("cai_one_tap_webview_login_button")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "1-Tap Sign In with Character.AI (WebView)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }

                            // Token Status Strip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgSurface)
                                    .border(
                                        1.dp,
                                        if (accountState.hasCaiSessionToken) StudioGreen else borderCol,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("cai_token_status_banner"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (accountState.hasCaiSessionToken) Icons.Default.Check else Icons.Default.Key,
                                        contentDescription = null,
                                        tint = if (accountState.hasCaiSessionToken) StudioGreen else OpusRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = accountState.caiTokenStatusText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (accountState.hasCaiSessionToken) StudioGreen else textMain
                                        )
                                        Text(
                                            text = "AES256 EncryptedSharedPreferences • Auto-syncs handle, characters & personas",
                                            fontSize = 9.sp,
                                            color = textSub
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = borderCol)

                            // Manual Backup Option Underneath: "c.ai Session Token" & Identifier
                            Text(
                                text = "BACKUP MANUAL C.AI SESSION TOKEN / CREDENTIALS:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSub
                            )

                            OutlinedTextField(
                                value = passwordOrTokenInput,
                                onValueChange = { passwordOrTokenInput = it },
                                label = { Text("c.ai Session Token") },
                                placeholder = { Text("Paste HTTP_AUTHORIZATION / char_token (Backup Option)", fontSize = 12.sp) },
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
                                value = identifierInput,
                                onValueChange = { identifierInput = it },
                                label = {
                                    Text(
                                        when (selectedProvider) {
                                            AuthProvider.CHARACTER_AI -> "Character.AI Handle or Email (e.g. kevofromitaly10)"
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
                                value = characterHandleInput,
                                onValueChange = { characterHandleInput = it },
                                label = { Text("Character.AI @handle or Character ID to Auto-Sync (Optional)") },
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
                                        isSignUp = isSignUpMode,
                                        activityContext = context
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
                                    .testTag("auth_submit_sync_button")
                            ) {
                                Icon(
                                    imageVector = if (isSignUpMode) Icons.Default.PersonAdd else Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sync Character.AI Account, Characters & Personas",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            OutlinedButton(
                                onClick = { activeSubView = AccountSubView.MANAGE_PERSONAS },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("open_manage_synced_personas_button")
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = OpusRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Manage Synced Personas (${accountState.syncedPersonas.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = textMain
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
                                    text = "ACTIVE SYNCED CHARACTER & LOREBOOK",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OpusRed,
                                    letterSpacing = 0.8.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when (activeChar.visibility) {
                                                    CharacterVisibility.PRIVATE -> StudioAmber.copy(alpha = 0.2f)
                                                    CharacterVisibility.UNLISTED -> OpusRed.copy(alpha = 0.2f)
                                                    CharacterVisibility.PUBLIC -> StudioGreen.copy(alpha = 0.18f)
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = activeChar.visibility.label.uppercase(Locale.US),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (activeChar.visibility) {
                                                CharacterVisibility.PRIVATE -> StudioAmber
                                                CharacterVisibility.UNLISTED -> OpusRed
                                                CharacterVisibility.PUBLIC -> StudioGreen
                                            }
                                        )
                                    }
                                    if (activeChar.hasLorebook) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(StudioGreen.copy(alpha = 0.18f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "LOREBOOK (${activeChar.lorebookEntriesCount})",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = StudioGreen
                                            )
                                        }
                                    }
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = OpusRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Voice: ${activeChar.voiceProfileName}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textMain
                                        )
                                        Text(
                                            text = "Active Persona: ${accountState.activePersona.name} • Holdoff ${activeChar.recommendedHoldoffMs}ms",
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
                    // SYNCED CHARACTER.AI ROSTER LIST (PUBLIC, UNLISTED, PRIVATE + LOREBOOK STATUS)
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
                                text = "SYNCED CHARACTER.AI ROSTER (PUBLIC, UNLISTED & PRIVATE)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Includes all user-created characters, greetings, definitions, and Lorebook status. Tap any character to load into the Call Bridge.",
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(borderCol)
                                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = character.visibility.label,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textMain
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
                                    }
                                    Text(
                                        text = character.personaBio,
                                        fontSize = 11.sp,
                                        color = textSub,
                                        maxLines = 2
                                    )
                                    Text(
                                        text = "Greeting: \"${character.greeting}\"",
                                        fontSize = 10.sp,
                                        color = textSub,
                                        maxLines = 1
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ID: ${character.characterId} • ${character.voiceProfileName}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isCurrent) StudioGreen else textSub
                                        )
                                        if (character.hasLorebook) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoStories,
                                                    contentDescription = null,
                                                    tint = StudioGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Lorebook (${character.lorebookEntriesCount})",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StudioGreen
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AccountSubView.MANAGE_PERSONAS -> {
                    // MANAGE SYNCED PERSONAS LIST
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
                                text = "MANAGE SYNCED PERSONAS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Switch your active Character.AI user persona at any time. Your active persona_id and definition are sent with every live turn on wss://neo.character.ai/ws/.",
                                fontSize = 11.sp,
                                color = textSub
                            )

                            accountState.syncedPersonas.forEach { persona ->
                                val isActivePersona = persona.personaId == accountState.activePersona.personaId
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isActivePersona) bgSurface else bgCard)
                                        .border(
                                            width = if (isActivePersona) 1.5.dp else 1.dp,
                                            color = if (isActivePersona) StudioGreen else borderCol,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { viewModel.selectActiveUserPersona(persona) }
                                        .padding(12.dp)
                                        .testTag("persona_item_${persona.personaId}"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${persona.name} (${persona.handle})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = textMain
                                            )
                                            Text(
                                                text = "Persona ID: ${persona.personaId}",
                                                fontSize = 10.sp,
                                                color = if (isActivePersona) StudioGreen else textSub,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isActivePersona) StudioGreen else borderCol)
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = if (isActivePersona) "ACTIVE PERSONA" else "ACTIVATE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Text(
                                        text = persona.definition,
                                        fontSize = 11.sp,
                                        color = textSub
                                    )
                                }
                            }

                            HorizontalDivider(color = borderCol)

                            Text(
                                text = "CREATE OR IMPORT CUSTOM CALLER PERSONA:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSub
                            )

                            OutlinedTextField(
                                value = customPersonaName,
                                onValueChange = { customPersonaName = it },
                                label = { Text("Persona Display Name") },
                                placeholder = { Text("e.g. Kev (Italy Studio)", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = customPersonaHandle,
                                onValueChange = { customPersonaHandle = it },
                                label = { Text("Persona @Handle") },
                                placeholder = { Text("e.g. @kevofromitaly10", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = customPersonaDefinition,
                                onValueChange = { customPersonaDefinition = it },
                                label = { Text("Persona Backstory & Traits") },
                                placeholder = { Text("Describe how characters should perceive you during calls...", fontSize = 12.sp) },
                                maxLines = 3,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    viewModel.addOrUpdateCustomUserPersona(
                                        name = customPersonaName,
                                        handle = customPersonaHandle,
                                        definition = customPersonaDefinition
                                    )
                                    customPersonaName = ""
                                    customPersonaHandle = ""
                                    customPersonaDefinition = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OpusRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("add_custom_persona_button")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save & Switch to Persona", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                text = "SYNC CHARACTER BY CHARACTER_ID, @HANDLE OR C.AI LINK",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Paste a Character.AI link (character.ai/chat/...), character_id, or @handle to pull its greeting, description, definition, Lorebook status, and bind a 48kHz voice profile.",
                                fontSize = 11.sp,
                                color = textSub
                            )

                            OutlinedTextField(
                                value = importHandleOrUrl,
                                onValueChange = { importHandleOrUrl = it },
                                label = { Text("Character.AI character_id, @handle, or Share URL") },
                                placeholder = { Text("e.g. @aria_vance or https://character.ai/chat/...", fontSize = 12.sp) },
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
                                label = { Text("Character Personal Details / Backstory Override (Optional)") },
                                placeholder = { Text("Leave blank to fetch from C.AI API, or paste custom bio...", fontSize = 12.sp) },
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
