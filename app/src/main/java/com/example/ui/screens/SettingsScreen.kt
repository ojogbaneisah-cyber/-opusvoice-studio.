package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.OpusContainerTarget
import com.example.audio.StudioAudioSource
import com.example.data.model.CaiLaunchMode
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

private enum class SettingsCategory(val title: String) {
    ALL("All"),
    AUDIO_CONFIG("Audio"),
    INTEGRATIONS("Integrations"),
    ACCOUNT_SYNC("Account"),
    APP_PREFS("Preferences")
}

@Composable
fun SettingsScreen(
    viewModel: StudioViewModel,
    paddingValues: PaddingValues,
    onNavigateToAccountPortal: () -> Unit = { viewModel.setTab(StudioTab.ACCOUNT_SYNC) }
) {
    val context = LocalContext.current
    val config by viewModel.settingsConfig.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()

    val bgCard = if (isDark) Color(0xFF181B24) else OpusWhiteBackground
    val bgSurface = if (isDark) Color(0xFF222634) else OpusWhiteSurface
    val borderCol = if (isDark) Color(0xFF2E3446) else OpusLightBorder
    val textMain = if (isDark) Color(0xFFF8FAFC) else OpusLightTextPrimary
    val textSub = if (isDark) Color(0xFF94A3B8) else OpusLightTextSecondary

    var activeCategory by remember { mutableStateOf(SettingsCategory.ALL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 4-Section Segmented Category Selector: Audio | Integrations | Account | Preferences
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgCard)
                .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsCategory.entries.forEach { cat ->
                val selected = activeCategory == cat
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) OpusRed else Color.Transparent)
                        .clickable { activeCategory = cat }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("settings_category_${cat.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cat.title,
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
            if (activeCategory == SettingsCategory.ALL || activeCategory == SettingsCategory.AUDIO_CONFIG) {
                    // 1. AUDIO CONFIGURATION: Sample rate, audio format, channel & hardware quality
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = OpusRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "AUDIO CONFIGURATION",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusRed,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Sample rate, Opus/WAV container format, channel mode, and AudioRecord pre-amp",
                                        fontSize = 11.sp,
                                        color = textSub
                                    )
                                }
                            }

                            Text(
                                text = "DEFAULT SAMPLE RATE:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSub
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(48000 to "48 kHz Studio", 44100 to "44.1 kHz CD", 24000 to "24 kHz Voice").forEach { (rateHz, label) ->
                                    val selected = config.defaultSampleRateHz == rateHz
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) OpusRed else bgSurface)
                                            .border(1.dp, if (selected) OpusRed else borderCol, RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.updateSettingsConfig { it.copy(defaultSampleRateHz = rateHz) }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp)
                                            .testTag("settings_sample_rate_$rateHz"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else textMain,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "DEFAULT VOICE NOTE CONTAINER FORMAT:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSub
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OpusContainerTarget.entries.forEach { target ->
                                    val selected = config.defaultExportContainer == target
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) OpusRed else bgSurface)
                                            .border(1.dp, if (selected) OpusRed else borderCol, RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.updateSettingsConfig { it.copy(defaultExportContainer = target) }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp)
                                            .testTag("settings_format_${target.name.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = target.extension.uppercase(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else textMain,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "DEFAULT HARDWARE AUDIORECORD SOURCE:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSub
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StudioAudioSource.entries.forEach { source ->
                                    val selected = config.defaultAudioSource == source
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) OpusRed else bgSurface)
                                            .border(1.dp, if (selected) OpusRed else borderCol, RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.updateSettingsConfig { it.copy(defaultAudioSource = source) }
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = source.label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else textMain,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            SettingsToggleRow(
                                title = "Mono Voice Note Channel (48kHz Mono)",
                                subtitle = "Forces single-channel Mono output required by WhatsApp PTT, Telegram & Google Chat",
                                checked = config.defaultMonoChannel,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(defaultMonoChannel = enabled) }
                                }
                            )

                            SettingsToggleRow(
                                title = "Hardware Noise Suppressor",
                                subtitle = "Attaches Android NoiseSuppressor DSP to 48kHz AudioRecord capture sessions",
                                checked = config.defaultNoiseSuppression,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(defaultNoiseSuppression = enabled) }
                                }
                            )

                            SettingsToggleRow(
                                title = "Hardware Auto Gain Control (AGC)",
                                subtitle = "Normalizes microphone and caller voice levels automatically during capture",
                                checked = config.defaultAutoGainControl,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(defaultAutoGainControl = enabled) }
                                }
                            )
                        }
                    }
                }

            if (activeCategory == SettingsCategory.ALL || activeCategory == SettingsCategory.INTEGRATIONS) {
                    // 2. INTEGRATION MANAGEMENT: Toggles & routing for Character.AI, WhatsApp, Telegram, Google Chat
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = OpusRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "INTEGRATION MANAGEMENT",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusRed,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Enable or disable supported app integrations and configure direct native app launch",
                                        fontSize = 11.sp,
                                        color = textSub
                                    )
                                }
                            }

                            SettingsToggleRow(
                                title = "Character.AI Native App Bridge",
                                subtitle = "Enables direct native Android package launch (ai.character.app) with zero browser redirect",
                                checked = config.caiIntegrationEnabled,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(caiIntegrationEnabled = enabled) }
                                }
                            )

                            SettingsToggleRow(
                                title = "WhatsApp Native PTT (.opus) Integration",
                                subtitle = "Exports strict PTT-YYYYMMDD-WAxxxx.opus with audio/ogg; codecs=opus MIME",
                                checked = config.whatsappIntegrationEnabled,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(whatsappIntegrationEnabled = enabled) }
                                }
                            )

                            SettingsToggleRow(
                                title = "Telegram Voice Note (.ogg) Integration",
                                subtitle = "Exports 48kHz Mono Ogg/Opus voice bubbles directly to org.telegram.messenger",
                                checked = config.telegramIntegrationEnabled,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(telegramIntegrationEnabled = enabled) }
                                }
                            )

                            SettingsToggleRow(
                                title = "Google Chat Voice Note (.ogg) Integration",
                                subtitle = "Exports 48kHz Mono Ogg/Opus voice notes to com.google.android.apps.dynamite",
                                checked = config.googleChatIntegrationEnabled,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(googleChatIntegrationEnabled = enabled) }
                                }
                            )

                            HorizontalDivider(color = borderCol)

                            Text(
                                text = "CHARACTER.AI NATIVE LAUNCH MODE (NO BROWSER REDIRECT):",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSub
                            )

                            CaiLaunchMode.entries.forEach { mode ->
                                val isSelected = config.caiLaunchMode == mode
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
                                        .clickable {
                                            viewModel.updateSettingsConfig { it.copy(caiLaunchMode = mode) }
                                        }
                                        .padding(12.dp)
                                        .testTag("settings_launch_mode_${mode.name.lowercase()}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = mode.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = textMain
                                        )
                                        Text(
                                            text = mode.description,
                                            fontSize = 11.sp,
                                            color = textSub
                                        )
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

                            OutlinedTextField(
                                value = config.targetCaiPackage,
                                onValueChange = { pkg ->
                                    viewModel.updateSettingsConfig { it.copy(targetCaiPackage = pkg) }
                                },
                                label = { Text("Designated Character.AI Android Package ID") },
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
                                    .testTag("settings_cai_package_input")
                            )

                            Button(
                                onClick = { viewModel.armChimeBlockerAndLaunchCharacterAi(context) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OpusRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_test_launch_cai_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Direct Native Character.AI App Launch", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

            if (activeCategory == SettingsCategory.ALL || activeCategory == SettingsCategory.ACCOUNT_SYNC) {
                    // 3. ACCOUNT MANAGEMENT: Authentication methods, account details & profile synchronization
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = OpusRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "ACCOUNT MANAGEMENT & CLOUD SYNC",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusRed,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Manage Firebase Authentication, Credential Manager Google Sign-In, and Character.AI profile sync",
                                        fontSize = 11.sp,
                                        color = textSub
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (accountState.isSignedIn) StudioGreen else borderCol,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (accountState.isSignedIn) {
                                        "Signed In: ${accountState.displayName} (${accountState.provider.label})"
                                    } else {
                                        "Status: Guest Mode (Not Signed In)"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = textMain
                                )
                                Text(
                                    text = "Email / Username: ${accountState.emailOrUsername.ifBlank { "None linked" }}",
                                    fontSize = 11.sp,
                                    color = textSub
                                )
                                Text(
                                    text = "Active Synced Character: ${accountState.activeCharacter.name} (${accountState.activeCharacter.handle})",
                                    fontSize = 11.sp,
                                    color = StudioGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = accountState.cloudSyncStatus,
                                    fontSize = 10.sp,
                                    color = textSub
                                )
                            }

                            SettingsToggleRow(
                                title = "Auto-Sync Character & Voice on Login",
                                subtitle = "Automatically applies your Character.AI persona & 48kHz voice preset as soon as you sign in",
                                checked = config.autoSyncCharacterOnLogin,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(autoSyncCharacterOnLogin = enabled) }
                                }
                            )

                            Button(
                                onClick = { viewModel.signInWithGoogleCredentialManager(context) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OpusRed,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_google_signin_button")
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign In with Google (Credential Manager)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.signInWithAppleFirebase(
                                        activityContext = context,
                                        characterHandleInput = accountState.caiLinkedUsername,
                                        appleIdEmailHint = accountState.emailOrUsername
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = textMain),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("settings_apple_signin_button")
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign In with Apple ID (Firebase OAuth)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateToAccountPortal,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OpusRed),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("settings_open_account_portal_button")
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("c.ai Custom Login", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.syncNowWithCloud() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioGreen),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("settings_sync_cloud_button")
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Cloud Now", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            if (accountState.isSignedIn) {
                                OutlinedButton(
                                    onClick = { viewModel.signOutAccount(context) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OpusRed),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("settings_sign_out_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sign Out of Current Account", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

            if (activeCategory == SettingsCategory.ALL || activeCategory == SettingsCategory.APP_PREFS) {
                    // 4. APPLICATION PREFERENCES: Chime-Blocker DSP, call behavior, and theme controls
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = OpusRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "APPLICATION PREFERENCES & CHIME DSP",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusRed,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Centralized controls for Chime-Blocker holdoff, call bridge behavior, and studio theme",
                                        fontSize = 11.sp,
                                        color = textSub
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Default Post-Speech Chime Holdoff Window",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textMain
                                    )
                                    Text(
                                        text = "${config.defaultMuteWindowMs} ms",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusRed
                                    )
                                }
                                Text(
                                    text = "Duration to gate non-vocal synthetic UI chimes immediately after a speech turn ends (300–2200 ms)",
                                    fontSize = 11.sp,
                                    color = textSub
                                )
                                Slider(
                                    value = config.defaultMuteWindowMs.toFloat(),
                                    onValueChange = { ms ->
                                        viewModel.updateSettingsConfig { it.copy(defaultMuteWindowMs = ms.toInt().coerceIn(300, 2200)) }
                                    },
                                    valueRange = 300f..2200f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = OpusRed,
                                        activeTrackColor = OpusRed,
                                        inactiveTrackColor = borderCol
                                    ),
                                    modifier = Modifier.testTag("settings_mute_window_slider")
                                )
                            }

                            SettingsToggleRow(
                                title = "800 Hz – 2.8 kHz Spectral Chime Notch",
                                subtitle = "Attenuates narrowband high-Q synthetic UI chimes while preserving human vocal formants",
                                checked = config.defaultSpectralNotch,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(defaultSpectralNotch = enabled) }
                                }
                            )

                            SettingsToggleRow(
                                title = "300 Hz – 3.4 kHz Telephony Voice Bandpass",
                                subtitle = "Applies authentic phone-call voice bandwidth and blocks sub-bass/ultrasonic transients",
                                checked = config.defaultTelephonyBandpass,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(defaultTelephonyBandpass = enabled) }
                                }
                            )

                            SettingsToggleRow(
                                title = "Auto-Arm Chime Blocker When Opening c.ai App",
                                subtitle = "Starts the foreground AudioSession DSP service before launching the Character.AI app",
                                checked = config.autoArmChimeBlockerOnLaunch,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(autoArmChimeBlockerOnLaunch = enabled) }
                                }
                            )

                            SettingsToggleRow(
                                title = "Default Call Routing: Speakerphone Bridge",
                                subtitle = "Enables speakerphone acoustic bridge by default when starting a live c.ai call",
                                checked = config.defaultCallSpeakerphone,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettingsConfig { it.copy(defaultCallSpeakerphone = enabled) }
                                }
                            )

                            SettingsToggleRow(
                                title = "Dark Broadcast-Console Theme",
                                subtitle = "Switch between Obsidian (#0F1117) Broadcast Console and Light surface",
                                checked = isDark,
                                textMain = textMain,
                                textSub = textSub,
                                onCheckedChange = { viewModel.toggleDarkMode() }
                            )

                            OutlinedButton(
                                onClick = { viewModel.resetSettingsToDefaults() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OpusRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("settings_reset_defaults_button")
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore Default Studio Configuration", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    textMain: Color,
    textSub: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textMain
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = textSub
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OpusRed
            )
        )
    }
}
