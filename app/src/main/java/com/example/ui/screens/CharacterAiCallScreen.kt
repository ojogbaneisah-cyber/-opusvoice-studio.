package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.MessengerTarget
import com.example.data.model.AVAILABLE_VOICES
import com.example.ui.CaiCallSection
import com.example.ui.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.components.AuthenticatedCaiCallWebViewCard
import com.example.ui.theme.OpusLightBorder
import com.example.ui.theme.OpusLightTextPrimary
import com.example.ui.theme.OpusLightTextSecondary
import com.example.ui.theme.OpusRed
import com.example.ui.theme.OpusRedDark
import com.example.ui.theme.OpusRedLight
import com.example.ui.theme.OpusWhiteBackground
import com.example.ui.theme.OpusWhiteSurface
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioGreen
import java.util.Locale
import kotlin.math.max

@Composable
fun CharacterAiCallScreen(
    viewModel: StudioViewModel,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val callState by viewModel.characterCallState.collectAsState()
    val chimeStats by viewModel.chimeInterceptorStats.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val settingsConfig by viewModel.settingsConfig.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val transcriptionState by viewModel.transcriptionState.collectAsState()
    val liveMicAmps by viewModel.opusAudioEngine.liveRecordingAmplitudes.collectAsState()
    val loadedAudio by viewModel.loadedOpusAudio.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()

    val bgCard = if (isDark) Color(0xFF181B24) else OpusWhiteBackground
    val bgSurface = if (isDark) Color(0xFF222634) else OpusWhiteSurface
    val borderCol = if (isDark) Color(0xFF2E3446) else OpusLightBorder
    val textMain = if (isDark) Color(0xFFF8FAFC) else OpusLightTextPrimary
    val textSub = if (isDark) Color(0xFFCBD5E1) else OpusLightTextSecondary

    var callerPromptInput by remember { mutableStateOf("") }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleCallerMicInCall()
        } else {
            Toast.makeText(context, "Microphone permission is required for live voice calls.", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Separated Section Navigation Menu for Character.AI Call Bridge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bgCard)
                .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CaiCallSection.entries.forEach { section ->
                val selected = uiState.activeCallSection == section
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) OpusRed else Color.Transparent)
                        .clickable { viewModel.setCallSection(section) }
                        .padding(vertical = 10.dp, horizontal = 6.dp)
                        .testTag("cai_section_${section.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = section.label,
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
            when (uiState.activeCallSection) {
                CaiCallSection.LIVE_DECK -> {
                    // 1. LIVE CHARACTER.AI CALL CONSOLE VIEW
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                width = 1.5.dp,
                                color = if (callState.isCallActive) StudioGreen else borderCol,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        color = bgCard
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val connectionBadgeColor = when {
                                callState.isChimeGateMuting -> StudioAmber
                                callState.isCallActive && callState.isCallMuted -> StudioAmber
                                callState.isCallActive -> StudioGreen
                                else -> Color(0xFF64748B)
                            }
                            val connectionBadgeLabel = when {
                                callState.isChimeGateMuting -> "CHIME GATE MUTING"
                                callState.isCallActive && callState.isCallMuted -> "CALL MUTED"
                                callState.isCallActive -> "LIVE CONNECTED"
                                else -> "STANDBY"
                            }
                            val formattedCallTimer = String.format(
                                Locale.US,
                                "%02d:%02d",
                                callState.callDurationSeconds / 60,
                                callState.callDurationSeconds % 60
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgSurface)
                                    .border(1.dp, connectionBadgeColor.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("cai_connection_status_indicator"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(connectionBadgeColor)
                                    )
                                    Text(
                                        text = connectionBadgeLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = connectionBadgeColor,
                                        letterSpacing = 0.6.sp
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = 11.sp,
                                        color = textSub
                                    )
                                    Text(
                                        text = formattedCallTimer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textMain,
                                        modifier = Modifier.testTag("cai_call_timer_text")
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5))
                                        .border(1.dp, StudioGreen, RoundedCornerShape(6.dp))
                                        .clickable { viewModel.setCallSection(CaiCallSection.CHIME_DSP) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = StudioGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (callState.chimeBlockerEnabled) "ZERO-CHIME (${callState.postSpeechMuteWindowMs}ms)" else "BYPASSED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF065F46)
                                    )
                                }
                            }

                            // Synced Character Identity Strip with Quick Sync Button
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
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(if (callState.isCallActive) StudioGreen.copy(alpha = 0.2f) else OpusRed.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (callState.isCallActive) Icons.Default.PhoneInTalk else Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = if (callState.isCallActive) StudioGreen else OpusRed,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = callState.characterName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = textMain,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "${callState.characterVoiceLabel} • ${callState.callStatusText}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (callState.isCallActive) StudioGreen else textSub,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.setTab(StudioTab.ACCOUNT_SYNC) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OpusRed),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("cai_quick_account_sync_button")
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (accountState.isSignedIn) "Synced" else "Sign In",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Active Synced User Persona Strip & Quick Switcher
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgSurface)
                                    .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ACTIVE PERSONA: ${accountState.activePersona.name} (${accountState.activePersona.handle})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StudioGreen
                                    )
                                    Text(
                                        text = "wss://neo.character.ai/ws/",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textSub
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    accountState.syncedPersonas.forEach { persona ->
                                        val isActivePersona = persona.personaId == accountState.activePersona.personaId
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isActivePersona) OpusRed else bgCard)
                                                .border(
                                                    1.dp,
                                                    if (isActivePersona) OpusRed else borderCol,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.selectActiveUserPersona(persona) }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                                .testTag("cai_call_persona_chip_${persona.personaId}")
                                        ) {
                                            Text(
                                                text = if (isActivePersona) "✓ ${persona.name}" else persona.name,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActivePersona) Color.White else textMain
                                            )
                                        }
                                    }
                                }
                            }

                            // Authenticated C.AI Call WebView Toggle (Shared Cookies + 48kHz Chime-Blocker Pipeline)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgSurface)
                                    .border(
                                        1.dp,
                                        if (callState.authenticatedWebViewEnabled) StudioGreen else borderCol,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Authenticated C.AI Call WebView",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (callState.authenticatedWebViewEnabled) StudioGreen else textMain
                                    )
                                    Text(
                                        text = "Loads official Character.AI call/voice interface with shared cookies & 48kHz Chime-Blocker DSP",
                                        fontSize = 10.sp,
                                        color = textSub
                                    )
                                }
                                Switch(
                                    checked = callState.authenticatedWebViewEnabled,
                                    onCheckedChange = { viewModel.toggleAuthenticatedCallWebView(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = StudioGreen
                                    ),
                                    modifier = Modifier.testTag("cai_authenticated_webview_switch")
                                )
                            }

                            if (callState.authenticatedWebViewEnabled) {
                                AuthenticatedCaiCallWebViewCard(
                                    character = accountState.activeCharacter,
                                    savedToken = viewModel.getSavedCaiSessionToken(),
                                    savedCookies = viewModel.getSavedCaiCookies(),
                                    holdoffWindowMs = callState.postSpeechMuteWindowMs,
                                    spectralNotchEnabled = callState.spectralNotchEnabled,
                                    isDark = isDark,
                                    borderCol = borderCol,
                                    onArmDspBridge = {
                                        if (!chimeStats.isServiceRunning) {
                                            viewModel.toggleChimeInterceptorService()
                                        }
                                    }
                                )
                            }

                            // Live Call Audio Visualizer
                            val activeWaveAmps = when {
                                callState.isListeningToCaller && liveMicAmps.isNotEmpty() -> liveMicAmps
                                playerState.isPlaying && loadedAudio != null -> loadedAudio!!.amplitudes
                                else -> List(48) { 0.14f }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgSurface)
                                    .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val count = activeWaveAmps.size.coerceAtLeast(1)
                                    val slot = size.width / count
                                    val barW = max(2.5f, slot * 0.6f)
                                    for (i in 0 until count) {
                                        val amp = activeWaveAmps[i].coerceIn(0.10f, 0.95f)
                                        val h = size.height * amp
                                        val x = i * slot + (slot - barW) / 2f
                                        val y = (size.height - h) / 2f
                                        val color = when {
                                            callState.isChimeGateMuting -> StudioAmber
                                            callState.isListeningToCaller -> StudioGreen
                                            playerState.isPlaying -> OpusRed
                                            else -> Color(0xFF64748B).copy(alpha = 0.45f)
                                        }
                                        drawRoundRect(
                                            color = color,
                                            topLeft = Offset(x, y),
                                            size = Size(barW, h),
                                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                        )
                                    }
                                }

                                Text(
                                    text = when {
                                        callState.isChimeGateMuting -> "CHIME GATE ACTIVE (MUTING C.AI BLIP)"
                                        callState.isListeningToCaller -> "CAPTURING CALLER AUDIO (48KHZ MONO)"
                                        playerState.isPlaying -> "CHARACTER SPEAKING (ZERO UI SFX)"
                                        else -> "48KHZ CALL BRIDGE READY"
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSub,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                )
                            }

                            // Live Transcript / Last Exchange Display + Neo WebSocket Turn Feed
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgSurface)
                                    .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "OTHER PERSON ON CALL (${accountState.activePersona.name.uppercase()}) SAID:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSub
                                )
                                Text(
                                    text = if (transcriptionState.isRecording && transcriptionState.liveText.isNotBlank()) {
                                        transcriptionState.liveText
                                    } else {
                                        callState.lastCallerStatement.ifBlank { "Tap 'Caller Turn' or type what the other person said below..." }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textMain,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${callState.characterName.uppercase()} REPLIED (NO CHIMES):",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OpusRed
                                )
                                Text(
                                    text = callState.lastCharacterReply,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textMain,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (callState.liveChatTurns.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "RECENT C.AI NEO WEBSOCKET TURNS (${callState.liveChatTurns.size}):",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StudioGreen
                                    )
                                    callState.liveChatTurns.takeLast(4).forEach { turn ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${turn.authorName}:",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (turn.isUser) StudioGreen else OpusRed
                                            )
                                            Text(
                                                text = turn.text,
                                                fontSize = 10.sp,
                                                color = textMain,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Primary Call Controls Deck
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.toggleCallMute() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (callState.isCallMuted) StudioAmber else bgSurface,
                                        contentColor = if (callState.isCallMuted) Color.Black else textMain
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier
                                        .weight(0.95f)
                                        .height(50.dp)
                                        .border(
                                            1.dp,
                                            if (callState.isCallMuted) StudioAmber else borderCol,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .testTag("cai_mute_call_button")
                                ) {
                                    Icon(
                                        imageVector = if (callState.isCallMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = if (callState.isCallMuted) "Unmute Call" else "Mute Call",
                                        tint = if (callState.isCallMuted) Color.Black else textMain,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (callState.isCallMuted) "Unmute" else "Mute",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            viewModel.toggleCallerMicInCall()
                                        } else {
                                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (callState.isListeningToCaller) OpusRed else bgSurface,
                                        contentColor = if (callState.isListeningToCaller) Color.White else textMain
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier
                                        .weight(1.25f)
                                        .height(50.dp)
                                        .border(1.dp, OpusRed, RoundedCornerShape(12.dp))
                                        .testTag("cai_listen_caller_button")
                                ) {
                                    Icon(
                                        imageVector = if (callState.isListeningToCaller) Icons.Default.GraphicEq else Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = if (callState.isListeningToCaller) Color.White else OpusRed,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (callState.isListeningToCaller) "Done Turn" else "Caller Turn",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.toggleCallSpeakerphone() },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (callState.speakerphoneEnabled) OpusRed else bgSurface)
                                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                        .testTag("cai_speakerphone_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SpeakerPhone,
                                        contentDescription = "Speakerphone mode",
                                        tint = if (callState.speakerphoneEnabled) Color.White else textMain
                                    )
                                }

                                Button(
                                    onClick = { viewModel.toggleCharacterLiveCall() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (callState.isCallActive) OpusRedDark else StudioGreen,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier
                                        .weight(1.05f)
                                        .height(50.dp)
                                        .testTag("cai_start_end_call_button")
                                ) {
                                    Icon(
                                        imageVector = if (callState.isCallActive) Icons.Default.CallEnd else Icons.Default.Call,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (callState.isCallActive) "End Call" else "Start Call",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Quick Text-to-Call Input (Streams via wss://neo.character.ai/ws/)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = callerPromptInput,
                                    onValueChange = { callerPromptInput = it },
                                    placeholder = { Text("Message ${callState.characterName} via c.ai Neo WebSocket...", fontSize = 12.sp) },
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
                                        .weight(1f)
                                        .testTag("cai_caller_input_field")
                                )

                                Button(
                                    onClick = {
                                        if (callerPromptInput.isNotBlank()) {
                                            viewModel.respondToCallerInCharacter(callerPromptInput)
                                            callerPromptInput = ""
                                        }
                                    },
                                    enabled = !uiState.isSynthesizing,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OpusRed,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("cai_send_caller_turn_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Speak Reply", modifier = Modifier.size(18.dp))
                                }
                            }

                            // Direct Native Character.AI App Launch Bar (No Web Redirect)
                            OutlinedButton(
                                onClick = {
                                    viewModel.armChimeBlockerAndLaunchCharacterAi(context)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = bgSurface,
                                    contentColor = textMain
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("cai_launch_external_app_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = OpusRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Launch Native Character.AI App (${settingsConfig.targetCaiPackage})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                CaiCallSection.CHIME_DSP -> {
                    // 2. DEDICATED C.AI CHIME-BLOCKER & POST-SPEECH TURN GATE VIEW
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
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = null,
                                        tint = OpusRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "C.AI CHIME INTERCEPTOR & TURN GATE",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = OpusRed,
                                            letterSpacing = 0.8.sp
                                        )
                                        Text(
                                            text = "AudioSession DSP + 48kHz PCM stream chime blocker",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textSub,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = callState.chimeBlockerEnabled,
                                    onCheckedChange = { viewModel.setChimeBlockerEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = OpusRed
                                    ),
                                    modifier = Modifier.testTag("cai_chime_blocker_switch")
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgSurface)
                                    .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("cai_interceptor_telemetry_strip"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = chimeStats.currentGateState,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (chimeStats.isServiceRunning) StudioGreen else textMain
                                    )
                                    Text(
                                        text = "Frames filtered: ${chimeStats.interceptedFramesCount} • Chimes blocked: ${chimeStats.chimesSuppressedCount}",
                                        fontSize = 10.sp,
                                        color = textSub
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.toggleChimeInterceptorService() },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (chimeStats.isServiceRunning) StudioGreen.copy(alpha = 0.18f) else bgCard,
                                        contentColor = if (chimeStats.isServiceRunning) StudioGreen else textMain
                                    ),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("cai_interceptor_service_toggle")
                                ) {
                                    Text(
                                        text = if (chimeStats.isServiceRunning) "DSP Svc: ON" else "DSP Svc: OFF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Post-Speech Chime Mute Window",
                                        fontSize = 12.sp,
                                        color = textMain,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${callState.postSpeechMuteWindowMs} ms",
                                        fontSize = 12.sp,
                                        color = OpusRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Mutes non-vocal blips right after you or the caller stop speaking until human vocal formants start",
                                    fontSize = 11.sp,
                                    color = textSub
                                )
                                Slider(
                                    value = callState.postSpeechMuteWindowMs.toFloat(),
                                    onValueChange = { viewModel.setPostSpeechMuteWindowMs(it.toInt()) },
                                    valueRange = 300f..2200f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = OpusRed,
                                        activeTrackColor = OpusRed,
                                        inactiveTrackColor = borderCol
                                    ),
                                    modifier = Modifier.testTag("cai_mute_window_slider")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChimeFilterBadge(
                                    title = "Spectral Chime Notch",
                                    subtitle = "Blocks 800Hz–2.8kHz UI blips",
                                    enabled = callState.spectralNotchEnabled,
                                    isDark = isDark,
                                    borderCol = borderCol,
                                    textMain = textMain,
                                    textSub = textSub,
                                    onClick = { viewModel.toggleSpectralNotchFilter() },
                                    modifier = Modifier.weight(1f)
                                )
                                ChimeFilterBadge(
                                    title = "Telephony Voice Filter",
                                    subtitle = "300Hz–3.4kHz phone line EQ",
                                    enabled = callState.telephonyBandpassEnabled,
                                    isDark = isDark,
                                    borderCol = borderCol,
                                    textMain = textMain,
                                    textSub = textSub,
                                    onClick = { viewModel.toggleTelephonyBandpassFilter() },
                                    modifier = Modifier.weight(1f)
                                )
                            }

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
                                        .weight(1.4f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Native c.ai App", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.setTab(StudioTab.SETTINGS) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textMain),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = OpusRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("App Routing", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                CaiCallSection.CHARACTER_VOICE -> {
                    // 3. DEDICATED CHARACTER PERSONA, VOICE PROFILE & MESSENGER FORWARDING VIEW
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "CHARACTER PERSONA & VOICE PROFILE",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OpusRed,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = "Synced from ${accountState.provider.label} (${accountState.activeCharacter.handle})",
                                        fontSize = 11.sp,
                                        color = textSub
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.setTab(StudioTab.ACCOUNT_SYNC) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OpusRed),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Switch / Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedTextField(
                                value = callState.characterName,
                                onValueChange = { viewModel.updateCharacterCallPersona(name = it) },
                                label = { Text("Character.AI Name & @Handle") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = bgSurface,
                                    unfocusedContainerColor = bgSurface,
                                    focusedBorderColor = OpusRed,
                                    unfocusedBorderColor = borderCol,
                                    focusedTextColor = textMain,
                                    unfocusedTextColor = textMain
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = callState.characterPersona,
                                onValueChange = { viewModel.updateCharacterCallPersona(persona = it) },
                                label = { Text("Character Personal Details & Speaking Style") },
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = bgSurface,
                                    unfocusedContainerColor = bgSurface,
                                    focusedBorderColor = OpusRed,
                                    unfocusedBorderColor = borderCol,
                                    focusedTextColor = textMain,
                                    unfocusedTextColor = textMain
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "48KHZ VOICE PRESET:",
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
                                    val selected = uiState.selectedVoice.id == voice.id
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) OpusRed else bgSurface)
                                            .border(1.dp, if (selected) OpusRed else borderCol, RoundedCornerShape(10.dp))
                                            .clickable { viewModel.setSelectedVoice(voice) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
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

                            Text(
                                text = "FORWARD LAST CALL REPLY AS NATIVE VOICE NOTE (AUTO-FORMATTED):",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textSub
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple(MessengerTarget.WHATSAPP, "PTT .opus", "cai_export_whatsapp"),
                                    Triple(MessengerTarget.TELEGRAM, "48kHz .ogg", "cai_export_telegram"),
                                    Triple(MessengerTarget.GOOGLE_CHAT, "48kHz .ogg", "cai_export_google_chat")
                                ).forEach { (target, formatBadge, tag) ->
                                    OutlinedButton(
                                        onClick = { viewModel.sendVoiceNoteToMessenger(context, target) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = bgSurface,
                                            contentColor = textMain
                                        ),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag(tag)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = target.label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = formatBadge,
                                                fontSize = 9.sp,
                                                color = OpusRed,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ChimeFilterBadge(
    title: String,
    subtitle: String,
    enabled: Boolean,
    isDark: Boolean,
    borderCol: Color,
    textMain: Color,
    textSub: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (enabled) {
                    if (isDark) Color(0xFF4C0519) else OpusRedLight
                } else {
                    if (isDark) Color(0xFF222634) else OpusWhiteSurface
                }
            )
            .border(1.dp, if (enabled) OpusRed else borderCol, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (enabled) OpusRed else textSub,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textMain,
                maxLines = 1
            )
        }
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = textSub,
            maxLines = 1
        )
    }
}
