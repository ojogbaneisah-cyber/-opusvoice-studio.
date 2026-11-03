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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StudioViewModel
import com.example.ui.theme.OpusLightBorder
import com.example.ui.theme.OpusLightTextPrimary
import com.example.ui.theme.OpusLightTextSecondary
import com.example.ui.theme.OpusRed
import com.example.ui.theme.OpusRedLight
import com.example.ui.theme.OpusWhiteBackground
import com.example.ui.theme.OpusWhiteSurface
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioGreen

@Composable
fun HomeDashboardScreen(
    viewModel: StudioViewModel,
    paddingValues: PaddingValues,
    onNavigateToRecordingStudio: () -> Unit,
    onNavigateToCallBridge: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccountSync: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToVoiceCloning: () -> Unit
) {
    val context = LocalContext.current
    val isDark by viewModel.isDarkMode.collectAsState()
    val callState by viewModel.characterCallState.collectAsState()
    val interceptorStats by viewModel.chimeInterceptorStats.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val settingsConfig by viewModel.settingsConfig.collectAsState()
    val allTakes by viewModel.allTakes.collectAsState()

    val bgCard = if (isDark) Color(0xFF181B24) else OpusWhiteBackground
    val bgSurface = if (isDark) Color(0xFF222634) else OpusWhiteSurface
    val borderCol = if (isDark) Color(0xFF2E3446) else OpusLightBorder
    val textMain = if (isDark) Color(0xFFF8FAFC) else OpusLightTextPrimary
    val textSub = if (isDark) Color(0xFF94A3B8) else OpusLightTextSecondary

    val isServiceArmed = interceptorStats.isServiceRunning || callState.chimeBlockerEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("home_dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Broadcast Console Hero Card: Chime-Blocker Engine & Direct Native c.ai App Bridge
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = 1.5.dp,
                    color = if (isServiceArmed) StudioGreen else OpusRed,
                    shape = RoundedCornerShape(18.dp)
                ),
            color = bgCard
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF26121E), Color(0xFF181B24))
                            } else {
                                listOf(OpusRedLight.copy(alpha = 0.45f), OpusWhiteBackground)
                            }
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isServiceArmed) StudioGreen else OpusRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "OPUSVOICE 48KHZ STUDIO CONSOLE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = if (isServiceArmed) {
                                    "Dual-Layer Chime Blocker Armed"
                                } else {
                                    "Chime Blocker Standby"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textMain
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isServiceArmed) StudioGreen.copy(alpha = 0.18f)
                                else StudioAmber.copy(alpha = 0.18f)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "${settingsConfig.defaultSampleRateHz / 1000}kHz MONO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceArmed) StudioGreen else StudioAmber
                        )
                    }
                }

                Text(
                    text = "Suppresses Character.AI post-speech UI chimes (${settingsConfig.defaultMuteWindowMs} ms holdoff + 800Hz–2.8kHz notch) and exports native WhatsApp PTT (.opus), Telegram (.ogg), and Google Chat (.ogg) voice notes.",
                    fontSize = 12.sp,
                    color = textSub,
                    lineHeight = 17.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.armChimeBlockerAndLaunchCharacterAi(context) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OpusRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp)
                            .testTag("dashboard_launch_native_cai_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Native c.ai App",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToRecordingStudio,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("dashboard_open_recording_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Record Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 2. Primary Workspaces Grid: Recording Studio, Live Call Bridge, Settings, Saved Vault
        Text(
            text = "STUDIO WORKSPACES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = OpusRed,
            letterSpacing = 0.8.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardWorkspaceCard(
                title = "Recording Studio",
                subtitle = "48kHz Mono Capture, Waveform De-Chimer & Ogg/Opus PTT Export",
                badgeText = ".OPUS / .OGG",
                icon = Icons.Default.ContentCut,
                accentColor = OpusRed,
                bgCard = bgCard,
                borderCol = borderCol,
                textMain = textMain,
                textSub = textSub,
                testTag = "dashboard_recording_studio_card",
                modifier = Modifier.weight(1f),
                onClick = onNavigateToRecordingStudio
            )

            DashboardWorkspaceCard(
                title = "c.ai Call Bridge",
                subtitle = "Live Call Deck, Speakerphone Bridge & Real-Time Chime Gate",
                badgeText = "${settingsConfig.defaultMuteWindowMs}ms Gate",
                icon = Icons.Default.PhoneInTalk,
                accentColor = StudioGreen,
                bgCard = bgCard,
                borderCol = borderCol,
                textMain = textMain,
                textSub = textSub,
                testTag = "dashboard_cai_call_card",
                modifier = Modifier.weight(1f),
                onClick = onNavigateToCallBridge
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardWorkspaceCard(
                title = "Studio Settings",
                subtitle = "Audio Configuration, App Integrations & Account Management",
                badgeText = "4 Sections",
                icon = Icons.Default.Settings,
                accentColor = StudioAmber,
                bgCard = bgCard,
                borderCol = borderCol,
                textMain = textMain,
                textSub = textSub,
                testTag = "dashboard_settings_card",
                modifier = Modifier.weight(1f),
                onClick = onNavigateToSettings
            )

            DashboardWorkspaceCard(
                title = "Saved Takes Vault",
                subtitle = "Replay, Trim & Share Recorded Voice Notes to Messengers",
                badgeText = "${allTakes.size} Takes",
                icon = Icons.Default.History,
                accentColor = Color(0xFF38BDF8),
                bgCard = bgCard,
                borderCol = borderCol,
                textMain = textMain,
                textSub = textSub,
                testTag = "dashboard_history_card",
                modifier = Modifier.weight(1f),
                onClick = onNavigateToHistory
            )
        }

        // 3. Active Account & Synced Character Profile Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (accountState.isSignedIn) StudioGreen else borderCol,
                    shape = RoundedCornerShape(16.dp)
                ),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (accountState.isSignedIn) StudioGreen else OpusRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (accountState.isSignedIn) Icons.Default.VerifiedUser else Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (accountState.isSignedIn) {
                                    "${accountState.displayName} • ${accountState.provider.label}"
                                } else {
                                    "Account & Character.AI Cloud Sync"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textMain
                            )
                            Text(
                                text = accountState.cloudSyncStatus,
                                fontSize = 11.sp,
                                color = if (accountState.isSignedIn) StudioGreen else textSub
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Account & Sync",
                        tint = textSub,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onNavigateToAccountSync() }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgSurface)
                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Synced Character: ${accountState.activeCharacter.name} (${accountState.activeCharacter.handle})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textMain
                    )
                    Text(
                        text = "Voice Profile: ${accountState.activeCharacter.voiceProfileName} • Holdoff: ${accountState.activeCharacter.recommendedHoldoffMs} ms",
                        fontSize = 11.sp,
                        color = StudioGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.signInWithGoogleCredentialManager(context) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OpusRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("dashboard_google_signin_button")
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Google Sign-In", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onNavigateToAccountSync,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textMain),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("dashboard_account_sync_button")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apple / c.ai Sync", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // 4. Active Studio Configuration Overview Card (Separated into SettingsScreen)
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
                                text = "ACTIVE AUDIO & INTEGRATION SUMMARY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Managed in dedicated Studio Settings screen",
                                fontSize = 11.sp,
                                color = textSub
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("dashboard_configure_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Configure", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = borderCol)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryStatPill(
                        label = "Sample Rate",
                        value = "${settingsConfig.defaultSampleRateHz / 1000.0} kHz".replace(".0 ", " "),
                        textMain = textMain,
                        textSub = textSub
                    )
                    SummaryStatPill(
                        label = "Format",
                        value = ".${settingsConfig.defaultExportContainer.extension.uppercase()}",
                        textMain = textMain,
                        textSub = textSub
                    )
                    SummaryStatPill(
                        label = "Channels",
                        value = if (settingsConfig.defaultMonoChannel) "Mono (1ch)" else "Stereo (2ch)",
                        textMain = textMain,
                        textSub = textSub
                    )
                    SummaryStatPill(
                        label = "Integrations",
                        value = listOf(
                            settingsConfig.caiIntegrationEnabled,
                            settingsConfig.whatsappIntegrationEnabled,
                            settingsConfig.telegramIntegrationEnabled,
                            settingsConfig.googleChatIntegrationEnabled
                        ).count { it }.toString() + "/4 On",
                        textMain = StudioGreen,
                        textSub = textSub
                    )
                }

                OutlinedButton(
                    onClick = onNavigateToVoiceCloning,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("dashboard_voice_cloning_button")
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open 48kHz Voice Clone Lab", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun DashboardWorkspaceCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    accentColor: Color,
    bgCard: Color,
    borderCol: Color,
    textMain: Color,
    textSub: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag(testTag),
        color = bgCard
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textMain,
                maxLines = 1
            )

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = textSub,
                lineHeight = 14.sp,
                minLines = 2,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    textMain: Color,
    textSub: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = textSub
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textMain
        )
    }
}
