package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserAccountState
import com.example.ui.CaiCallSection
import com.example.ui.StudioTab
import com.example.ui.VoiceStudioSection
import com.example.ui.theme.OpusLightBorder
import com.example.ui.theme.OpusLightTextPrimary
import com.example.ui.theme.OpusLightTextSecondary
import com.example.ui.theme.OpusRed
import com.example.ui.theme.OpusWhiteBackground
import com.example.ui.theme.OpusWhiteSurface
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioSurface

@Composable
fun StudioNavigationDrawer(
    activeTab: StudioTab,
    activeCallSection: CaiCallSection,
    activeStudioSection: VoiceStudioSection,
    accountState: UserAccountState,
    isDarkMode: Boolean,
    onSelectTab: (StudioTab) -> Unit,
    onSelectCallSubSection: (CaiCallSection) -> Unit,
    onSelectStudioSubSection: (VoiceStudioSection) -> Unit
) {
    val drawerBg = if (isDarkMode) StudioSurface else OpusWhiteBackground
    val cardBg = if (isDarkMode) Color(0xFF222634) else OpusWhiteSurface
    val borderCol = if (isDarkMode) StudioCardBorder else OpusLightBorder
    val textMain = if (isDarkMode) Color(0xFFF8FAFC) else OpusLightTextPrimary
    val textSub = if (isDarkMode) Color(0xFF94A3B8) else OpusLightTextSecondary

    ModalDrawerSheet(
        drawerContainerColor = drawerBg,
        modifier = Modifier
            .width(316.dp)
            .fillMaxHeight()
            .testTag("studio_navigation_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Account & Active Synced Character Header Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .border(
                        width = 1.dp,
                        color = if (accountState.isSignedIn) StudioGreen else borderCol,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        if (accountState.isSignedIn) {
                            onSelectTab(StudioTab.ACCOUNT_SYNC)
                        } else {
                            onSelectTab(StudioTab.LOGIN)
                        }
                    }
                    .padding(14.dp)
                    .testTag("drawer_account_header_card"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (accountState.isSignedIn) StudioGreen else OpusRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (accountState.isSignedIn) Icons.Default.VerifiedUser else Icons.Default.AccountCircle,
                            contentDescription = "Account Status",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (accountState.isSignedIn) accountState.displayName else "Sign In / Sign Up",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textMain
                        )
                        Text(
                            text = if (accountState.isSignedIn) {
                                "${accountState.provider.label} • Cloud Synced"
                            } else {
                                "Google • Apple ID • Character.AI"
                            },
                            fontSize = 11.sp,
                            color = if (accountState.isSignedIn) StudioGreen else OpusRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "Synced Character: ${accountState.activeCharacter.name} (${accountState.activeCharacter.voiceProfileName})",
                    fontSize = 10.sp,
                    color = textSub
                )
            }

            // Overview / Home Dashboard Item
            DrawerMenuItem(
                title = "Home Dashboard",
                subtitle = "Chime-Blocker telemetry, native c.ai bridge & quick actions",
                icon = Icons.Default.Home,
                selected = activeTab == StudioTab.HOME_DASHBOARD,
                textMain = textMain,
                textSub = textSub,
                testTag = "drawer_item_home_dashboard",
                onClick = { onSelectTab(StudioTab.HOME_DASHBOARD) }
            )

            // =========================================================================
            // DISTINCT SECTION 1: VOICE CALL DECK
            // =========================================================================
            DrawerSectionCard(
                sectionTitle = "VOICE CALL DECK",
                sectionBadge = "c.ai Bridge",
                cardBg = cardBg,
                borderCol = borderCol,
                modifier = Modifier.testTag("drawer_section_voice_call_deck")
            ) {
                DrawerMenuItem(
                    title = "Voice Call Deck",
                    subtitle = "Live Character.AI call deck & direct native app launch",
                    icon = Icons.Default.PhoneInTalk,
                    selected = activeTab == StudioTab.CAI_CALL && activeCallSection == CaiCallSection.LIVE_DECK,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_voice_call_deck",
                    onClick = { onSelectCallSubSection(CaiCallSection.LIVE_DECK) }
                )
                DrawerMenuItem(
                    title = "Chime-Blocker DSP & Holdoff",
                    subtitle = "Post-speech gate & 800Hz–2.8kHz spectral notch",
                    icon = Icons.Default.Shield,
                    selected = activeTab == StudioTab.CAI_CALL && activeCallSection == CaiCallSection.CHIME_DSP,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_chime_dsp",
                    onClick = { onSelectCallSubSection(CaiCallSection.CHIME_DSP) }
                )
                DrawerMenuItem(
                    title = "Character Persona & Voice",
                    subtitle = "Active synced character bio & 48kHz voice timbre",
                    icon = Icons.Default.RecordVoiceOver,
                    selected = activeTab == StudioTab.CAI_CALL && activeCallSection == CaiCallSection.CHARACTER_VOICE,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_character_voice",
                    onClick = { onSelectCallSubSection(CaiCallSection.CHARACTER_VOICE) }
                )
            }

            // =========================================================================
            // DISTINCT SECTION 2: AUDIO STUDIO
            // =========================================================================
            DrawerSectionCard(
                sectionTitle = "AUDIO STUDIO",
                sectionBadge = "48kHz DSP",
                cardBg = cardBg,
                borderCol = borderCol,
                modifier = Modifier.testTag("drawer_section_audio_studio")
            ) {
                DrawerMenuItem(
                    title = "Audio Studio",
                    subtitle = "48kHz mono recorder, pre-amp & audio file import",
                    icon = Icons.Default.Mic,
                    selected = activeTab == StudioTab.OPUS_TRIMMER && activeStudioSection == VoiceStudioSection.RECORD_IMPORT,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_audio_studio",
                    onClick = { onSelectStudioSubSection(VoiceStudioSection.RECORD_IMPORT) }
                )
                DrawerMenuItem(
                    title = "Waveform & De-Chime Trimmer",
                    subtitle = "Interactive trim handles & automatic tail de-chimer",
                    icon = Icons.Default.ContentCut,
                    selected = activeTab == StudioTab.OPUS_TRIMMER && activeStudioSection == VoiceStudioSection.WAVEFORM_TRIM,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_waveform_trim",
                    onClick = { onSelectStudioSubSection(VoiceStudioSection.WAVEFORM_TRIM) }
                )
                DrawerMenuItem(
                    title = "Ogg/Opus Format & PTT Export",
                    subtitle = "WhatsApp .opus, Telegram & Google Chat .ogg export",
                    icon = Icons.Default.GraphicEq,
                    selected = activeTab == StudioTab.OPUS_TRIMMER && activeStudioSection == VoiceStudioSection.FORMAT_EXPORT,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_format_export",
                    onClick = { onSelectStudioSubSection(VoiceStudioSection.FORMAT_EXPORT) }
                )
                DrawerMenuItem(
                    title = "Voice Clone Lab",
                    subtitle = "Analyze & clone custom 48kHz character voices",
                    icon = Icons.Default.RecordVoiceOver,
                    selected = activeTab == StudioTab.CLONING,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_voice_cloning",
                    onClick = { onSelectTab(StudioTab.CLONING) }
                )
                DrawerMenuItem(
                    title = "Saved Takes & Call Vault",
                    subtitle = "Replay, export & share recorded voice takes",
                    icon = Icons.Default.History,
                    selected = activeTab == StudioTab.HISTORY,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_saved_history",
                    onClick = { onSelectTab(StudioTab.HISTORY) }
                )
            }

            // =========================================================================
            // DISTINCT SECTION 3: SETTINGS
            // =========================================================================
            DrawerSectionCard(
                sectionTitle = "SETTINGS",
                sectionBadge = "Config & Auth",
                cardBg = cardBg,
                borderCol = borderCol,
                modifier = Modifier.testTag("drawer_section_settings")
            ) {
                DrawerMenuItem(
                    title = "Settings",
                    subtitle = "Audio rates, app redirection, integrations & preferences",
                    icon = Icons.Default.Settings,
                    selected = activeTab == StudioTab.SETTINGS,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_settings",
                    onClick = { onSelectTab(StudioTab.SETTINGS) }
                )
                DrawerMenuItem(
                    title = "Account & Character.AI Sync",
                    subtitle = "Manage synced personas, @handles & voice presets",
                    icon = Icons.Default.CloudSync,
                    selected = activeTab == StudioTab.ACCOUNT_SYNC,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_account_sync",
                    onClick = { onSelectTab(StudioTab.ACCOUNT_SYNC) }
                )
                DrawerMenuItem(
                    title = "Firebase Auth & Login",
                    subtitle = "Google, Apple ID & custom Character.AI credentials",
                    icon = Icons.Default.Login,
                    selected = activeTab == StudioTab.LOGIN,
                    textMain = textMain,
                    textSub = textSub,
                    testTag = "drawer_item_firebase_login",
                    onClick = { onSelectTab(StudioTab.LOGIN) }
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionCard(
    sectionTitle: String,
    sectionBadge: String,
    cardBg: Color,
    borderCol: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderCol, RoundedCornerShape(14.dp)),
        color = cardBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = OpusRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = sectionTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OpusRed,
                        letterSpacing = 0.8.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OpusRed.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sectionBadge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpusRed
                    )
                }
            }
            HorizontalDivider(
                color = borderCol.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
            content()
        }
    }
}

@Composable
private fun DrawerMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    textMain: Color,
    textSub: Color,
    testTag: String,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) OpusRed else textMain
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = textSub,
                    maxLines = 1
                )
            }
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) OpusRed else textSub,
                modifier = Modifier.size(20.dp)
            )
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = OpusRed.copy(alpha = 0.15f),
            unselectedContainerColor = Color.Transparent
        )
    )
}
