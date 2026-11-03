package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.MessengerTarget
import com.example.audio.OpusContainerTarget
import com.example.audio.StudioAudioSource
import com.example.ui.StudioViewModel
import com.example.ui.theme.OpusLightBorder
import com.example.ui.theme.OpusLightTextPrimary
import com.example.ui.theme.OpusLightTextSecondary
import com.example.ui.theme.OpusRed
import com.example.ui.theme.OpusRedDark
import com.example.ui.theme.OpusRedLight
import com.example.ui.theme.OpusRedSurface
import com.example.ui.theme.OpusWhiteBackground
import com.example.ui.theme.OpusWhiteSurface
import com.example.ui.theme.OpusWhiteSurfaceVariant
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioGreen
import java.util.Locale
import kotlin.math.max

@Composable
fun OpusVoiceStudioScreen(
    viewModel: StudioViewModel,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val loadedAudio by viewModel.loadedOpusAudio.collectAsState()
    val trimStart by viewModel.trimStartFraction.collectAsState()
    val trimEnd by viewModel.trimEndFraction.collectAsState()
    val formatSpec by viewModel.voiceNoteFormatSpec.collectAsState()
    val isRecordingMic by viewModel.opusAudioEngine.isRecordingMic.collectAsState()
    val isRecordingPaused by viewModel.opusAudioEngine.isRecordingPaused.collectAsState()
    val liveMicAmps by viewModel.opusAudioEngine.liveRecordingAmplitudes.collectAsState()
    val recDurationMs by viewModel.opusAudioEngine.recordingDurationMs.collectAsState()
    val telemetry by viewModel.opusAudioEngine.recordingTelemetry.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val lastExportedPath by viewModel.lastExportedFilePath.collectAsState()

    val isDark = viewModel.isDarkMode.collectAsState().value
    val bgCard = if (isDark) Color(0xFF181B24) else OpusWhiteBackground
    val bgSurface = if (isDark) Color(0xFF222634) else OpusWhiteSurface
    val borderCol = if (isDark) Color(0xFF2E3446) else OpusLightBorder
    val textMain = if (isDark) Color(0xFFF8FAFC) else OpusLightTextPrimary
    val textSub = if (isDark) Color(0xFF94A3B8) else OpusLightTextSecondary

    // Audio File Picker (.mp3, .wav, .m4a, .ogg)
    val audioUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var displayName = "uploaded_audio.wav"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) displayName = cursor.getString(idx)
                }
            }
            viewModel.uploadAudioToOpusStudio(uri, displayName)
        }
    }

    // Microphone Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleOpusMicrophoneRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required to record 48kHz audio.", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. AUDIO UPLOAD & 48KHZ MONO AUDIORECORD CARD
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
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDark) OpusRed.copy(alpha = 0.2f) else OpusRedLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = OpusRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "1. 48KHZ MONO AUDIORECORD & UPLOAD",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Capture 48,000 Hz 16-bit Mono PCM or import audio files",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSub,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 48kHz Mono Hardware Spec Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF4C0519) else OpusRedLight)
                            .border(1.dp, OpusRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "48kHz • MONO",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFFFDA4AF) else OpusRedDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                // Upload & Record Primary Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Upload Audio Button
                    OutlinedButton(
                        onClick = {
                            audioUploadLauncher.launch(
                                arrayOf("audio/*", "audio/mpeg", "audio/wav", "audio/mp4", "audio/x-m4a", "audio/ogg")
                            )
                        },
                        enabled = !isRecordingMic,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = bgSurface,
                            contentColor = textMain
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("opus_upload_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Upload audio file",
                            tint = OpusRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upload File",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    // Record 48kHz Mono Microphone Button
                    Button(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPerm) {
                                viewModel.toggleOpusMicrophoneRecording()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecordingMic) OpusRedDark else OpusRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.25f)
                            .height(50.dp)
                            .testTag("opus_record_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingMic) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecordingMic) "Stop 48kHz recording" else "Record 48kHz Mono audio",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRecordingMic) {
                                "Stop (${formatDurationSec(recDurationMs)})"
                            } else {
                                "Record 48kHz"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Pause / Resume Recording Button (shown while recording)
                    if (isRecordingMic) {
                        OutlinedButton(
                            onClick = { viewModel.togglePauseOpusMicrophoneRecording() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = bgSurface,
                                contentColor = textMain
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("opus_pause_mic_button")
                        ) {
                            Icon(
                                imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isRecordingPaused) "Resume recording" else "Pause recording",
                                tint = OpusRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 48kHz Mono AudioRecord Studio Pre-Amp & Input Console
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgSurface)
                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Live Telemetry Status & dBFS Meter Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            telemetry.isClipping -> StudioAmber
                                            isRecordingMic && !isRecordingPaused -> OpusRed
                                            isRecordingPaused -> StudioAmber
                                            else -> StudioGreen
                                        }
                                    )
                            )
                            Text(
                                text = telemetry.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = textMain,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = String.format(Locale.US, "%.1f dBFS", telemetry.dbFs),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.isClipping) StudioAmber else OpusRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    // Live Input Level Bar (-60 dBFS .. 0 dBFS)
                    val meterProgress = ((telemetry.dbFs + 60f) / 60f).coerceIn(0.02f, 1f)
                    LinearProgressIndicator(
                        progress = { if (isRecordingMic) meterProgress else 0.04f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (telemetry.isClipping) StudioAmber else OpusRed,
                        trackColor = borderCol
                    )

                    // AudioSource Preset Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StudioAudioSource.entries.forEach { source ->
                            val selected = telemetry.audioSource == source
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) OpusRed
                                        else if (isDark) Color(0xFF181B24) else OpusWhiteSurfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) OpusRed else borderCol,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = !isRecordingMic) {
                                        viewModel.setStudioAudioSource(source)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                                    .testTag("opus_audio_source_${source.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = source.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else textMain,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Hardware DSP Effects & Input Gain Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Noise Suppressor Chip
                            DspToggleChip(
                                label = "Noise Gate",
                                active = telemetry.noiseSuppressionEnabled,
                                isDark = isDark,
                                borderCol = borderCol,
                                textMain = textMain,
                                onClick = { viewModel.setStudioNoiseSuppression(!telemetry.noiseSuppressionEnabled) },
                                modifier = Modifier.testTag("opus_noise_suppressor_chip")
                            )
                            // Auto Gain Control Chip
                            DspToggleChip(
                                label = "Auto Gain (AGC)",
                                active = telemetry.autoGainControlEnabled,
                                isDark = isDark,
                                borderCol = borderCol,
                                textMain = textMain,
                                onClick = { viewModel.setStudioAutoGainControl(!telemetry.autoGainControlEnabled) },
                                modifier = Modifier.testTag("opus_agc_chip")
                            )
                        }

                        Text(
                            text = "Gain: ${String.format(Locale.US, "%.1fx", telemetry.inputGainMultiplier)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textMain
                        )
                    }

                    // Input Gain Slider (0.5x to 2.0x)
                    Slider(
                        value = telemetry.inputGainMultiplier,
                        onValueChange = { viewModel.setStudioInputGain(it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = OpusRed,
                            activeTrackColor = OpusRed,
                            inactiveTrackColor = borderCol
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .testTag("opus_input_gain_slider")
                    )
                }

                // Active File Metadata Strip
                loadedAudio?.let { audio ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgSurface)
                            .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = OpusRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = audio.sourceName,
                                style = MaterialTheme.typography.labelLarge,
                                color = textMain,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = "${audio.sampleRate / 1000}kHz • ${if (audio.channels == 1) "Mono" else "Stereo"} • ${formatDurationMs(audio.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = OpusRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 2. REAL-TIME INTERACTIVE WAVEFORM VISUALIZER CARD
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
                    Text(
                        text = "2. INTERACTIVE WAVEFORM VISUALIZER",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = OpusRed,
                        letterSpacing = 0.8.sp
                    )
                    if (isRecordingMic) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = OpusRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isRecordingPaused) "PAUSED (48KHZ MONO)" else "LIVE 48KHZ MONO CAPTURE",
                                style = MaterialTheme.typography.labelSmall,
                                color = OpusRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "Tap waveform to seek",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSub,
                            fontSize = 11.sp
                        )
                    }
                }

                // Interactive Canvas Waveform with Trim Window Overlay & Playhead
                val displayAmps = if (isRecordingMic && liveMicAmps.isNotEmpty()) {
                    liveMicAmps
                } else {
                    loadedAudio?.amplitudes ?: List(60) { 0.25f }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgSurface)
                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                ) {
                    OpusTrimWaveformCanvas(
                        amplitudes = displayAmps,
                        playProgress = playerState.progress,
                        trimStart = trimStart,
                        trimEnd = trimEnd,
                        isRecording = isRecordingMic,
                        isDark = isDark,
                        onSeek = { fraction ->
                            viewModel.audioPlayer.seekTo(fraction)
                        }
                    )
                }

                // Playback Transport Bar
                val totalDurationMs = loadedAudio?.durationMs ?: 0L
                val currentPosMs = playerState.currentPositionMs.toLong()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDurationMs(currentPosMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = textMain,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Replay from Trim Start
                        OutlinedButton(
                            onClick = { viewModel.playTrimmedPreview() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textMain),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("opus_preview_selection_button")
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = "Replay trim selection", tint = OpusRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Clip", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Primary Play / Pause Button (Solid Red Circle)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(OpusRed)
                                .clickable { viewModel.audioPlayer.togglePlayPause() }
                                .testTag("opus_play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Text(
                        text = formatDurationMs(totalDurationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = textSub
                    )
                }
            }
        }

        // 3. TRIMMING & CUTTING CONTROLS CARD
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
                val totalMs = loadedAudio?.durationMs ?: 8000L
                val startMs = (trimStart * totalMs).toLong()
                val endMs = (trimEnd * totalMs).toLong()
                val clipDurationMs = (endMs - startMs).coerceAtLeast(0L)

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
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = OpusRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "3. TRIMMING & CUTTING",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = OpusRed,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF4C0519) else OpusRedLight)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Selected: ${formatDurationMs(clipDurationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFFFDA4AF) else OpusRedDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dual Range Slider for Start & End Trim Selection
                RangeSlider(
                    value = trimStart..trimEnd,
                    onValueChange = { range ->
                        viewModel.setTrimRange(range.start, range.endInclusive)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = OpusRed,
                        activeTrackColor = OpusRed,
                        inactiveTrackColor = borderCol
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opus_trim_range_slider")
                )

                // Individual Precision Start & End Sliders with Timestamp Readouts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Start Time", fontSize = 11.sp, color = textSub)
                            Text(
                                text = formatDurationMs(startMs),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textMain
                            )
                        }
                        Slider(
                            value = trimStart,
                            onValueChange = { viewModel.setTrimRange(it, trimEnd) },
                            valueRange = 0f..0.95f,
                            colors = SliderDefaults.colors(
                                thumbColor = OpusRed,
                                activeTrackColor = OpusRed,
                                inactiveTrackColor = borderCol
                            ),
                            modifier = Modifier.testTag("opus_start_trim_slider")
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("End Time", fontSize = 11.sp, color = textSub)
                            Text(
                                text = formatDurationMs(endMs),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textMain
                            )
                        }
                        Slider(
                            value = trimEnd,
                            onValueChange = { viewModel.setTrimRange(trimStart, it) },
                            valueRange = 0.05f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = OpusRed,
                                activeTrackColor = OpusRed,
                                inactiveTrackColor = borderCol
                            ),
                            modifier = Modifier.testTag("opus_end_trim_slider")
                        )
                    }
                }

                // Apply Cut, Remove c.ai Chimes & Reset Trim Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetTrimRange() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textSub),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(44.dp)
                            .testTag("opus_reset_trim_button")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.applyUiChimeSuppression() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OpusRed),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(44.dp)
                            .testTag("opus_remove_chimes_button")
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = OpusRed, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("De-Chime c.ai", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            viewModel.applyTrimCut()
                            Toast.makeText(context, "Audio segment trimmed & updated!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OpusRed,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1.25f)
                            .height(44.dp)
                            .testTag("opus_apply_cut_button")
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trim & Cut", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. METADATA & FORMATTING UTILITY CARD (Mono, 48kHz, Ogg/Opus Toggle)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = OpusRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "4. METADATA & FORMATTING UTILITY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OpusRed,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Voice Note Standard: Mono • 48kHz • Ogg/Opus Target",
                                style = MaterialTheme.typography.bodySmall,
                                color = textSub,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = formatSpec.isVoiceNotePresetEnabled,
                        onCheckedChange = { viewModel.toggleVoiceNotePreset(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = OpusRed,
                            uncheckedThumbColor = textSub,
                            uncheckedTrackColor = borderCol
                        ),
                        modifier = Modifier.testTag("opus_voice_note_preset_toggle")
                    )
                }

                // Active Spec Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpecBadge(
                        title = "CHANNELS",
                        value = if (formatSpec.isVoiceNotePresetEnabled || formatSpec.isMono) "Mono (1ch)" else "Stereo (2ch)",
                        isHighlighted = formatSpec.isVoiceNotePresetEnabled,
                        isDark = isDark,
                        onClick = { viewModel.toggleMonoChannel() },
                        modifier = Modifier.weight(1f)
                    )
                    SpecBadge(
                        title = "SAMPLE RATE",
                        value = "${if (formatSpec.isVoiceNotePresetEnabled) 48000 else formatSpec.sampleRateHz} Hz",
                        isHighlighted = formatSpec.isVoiceNotePresetEnabled,
                        isDark = isDark,
                        onClick = { viewModel.cycleOpusSampleRate() },
                        modifier = Modifier.weight(1f)
                    )
                    SpecBadge(
                        title = "CONTAINER",
                        value = formatSpec.containerTarget.label,
                        isHighlighted = formatSpec.containerTarget == OpusContainerTarget.OGG_OPUS,
                        isDark = isDark,
                        onClick = { viewModel.cycleOpusContainerTarget() },
                        modifier = Modifier.weight(1.2f)
                    )
                }

                // Container Target Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OpusContainerTarget.entries.forEach { target ->
                        val selected = formatSpec.containerTarget == target
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) OpusRed else bgSurface)
                                .border(1.dp, if (selected) OpusRed else borderCol, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setOpusContainerTarget(target) }
                                .padding(vertical = 8.dp)
                                .testTag("opus_container_${target.extension}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ".${target.extension.uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else textMain
                            )
                        }
                    }
                }
            }
        }

        // 5. DIRECT DOWNLOAD WITH CUSTOM FILENAME CARD
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
                    text = "5. DIRECT LOCAL DOWNLOAD",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OpusRed,
                    letterSpacing = 0.8.sp
                )

                // Custom Filename Input
                OutlinedTextField(
                    value = formatSpec.customFileName,
                    onValueChange = { viewModel.setCustomExportFileName(it) },
                    label = { Text("Custom Output Filename") },
                    suffix = {
                        Text(
                            text = ".${formatSpec.containerTarget.extension}",
                            fontWeight = FontWeight.Bold,
                            color = OpusRed
                        )
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
                        .testTag("opus_custom_filename_input")
                )

                // Direct Download & Share Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.downloadProcessedOpusAudio(context) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OpusRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.6f)
                            .height(52.dp)
                            .testTag("opus_direct_download_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DOWNLOAD .${formatSpec.containerTarget.extension.uppercase()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.shareProcessedOpusAudio(context) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OpusRed),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("opus_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = OpusRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                // Direct Messenger PTT Voice-Note Dispatch Bar (WhatsApp, Telegram, Google Chat, Character.AI)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgSurface)
                        .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SEND AS NATIVE 48KHZ VOICE NOTE (PTT OGG/OPUS & C.AI)",
                        style = MaterialTheme.typography.labelSmall,
                        color = textSub,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MessengerTarget.entries.forEach { target ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF181B24) else OpusWhiteBackground)
                                    .border(1.dp, OpusRed.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                    .clickable { viewModel.sendVoiceNoteToMessenger(context, target) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .testTag("opus_dispatch_${target.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = target.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textMain,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = ".${target.preferredContainer.extension}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OpusRed
                                    )
                                }
                            }
                        }
                    }
                }

                // Confirmation Banner when Downloaded
                lastExportedPath?.let { savedName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF4C0519) else OpusRedSurface)
                            .border(1.dp, if (isDark) OpusRed else OpusRedLight, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = StudioGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Saved locally: $savedName",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color(0xFFFDA4AF) else OpusRedDark,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}

@Composable
private fun DspToggleChip(
    label: String,
    active: Boolean,
    isDark: Boolean,
    borderCol: Color,
    textMain: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) {
                    if (isDark) Color(0xFF4C0519) else OpusRedLight
                } else {
                    if (isDark) Color(0xFF181B24) else OpusWhiteSurfaceVariant
                }
            )
            .border(
                1.dp,
                if (active) OpusRed else borderCol,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = if (active) OpusRed else textMain.copy(alpha = 0.4f),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) {
                if (isDark) Color(0xFFFDA4AF) else OpusRedDark
            } else {
                textMain
            }
        )
    }
}

@Composable
private fun OpusTrimWaveformCanvas(
    amplitudes: List<Float>,
    playProgress: Float,
    trimStart: Float,
    trimEnd: Float,
    isRecording: Boolean,
    isDark: Boolean,
    onSeek: (Float) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("opus_waveform_canvas")
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
    ) {
        val barCount = if (amplitudes.isNotEmpty()) amplitudes.size else 60
        val effectiveAmps = if (amplitudes.isNotEmpty()) amplitudes else List(barCount) { 0.25f }

        val w = size.width
        val h = size.height
        val startX = trimStart * w
        val endX = trimEnd * w
        val playX = playProgress * w

        // Highlight active trim window region
        drawRect(
            color = if (isDark) OpusRed.copy(alpha = 0.14f) else Color(0xFFFFE4E6).copy(alpha = 0.55f),
            topLeft = Offset(startX, 0f),
            size = Size((endX - startX).coerceAtLeast(2f), h)
        )

        val totalSlot = w / barCount
        val barWidth = max(2.5f, totalSlot * 0.62f)
        val gap = totalSlot - barWidth

        for (i in 0 until barCount) {
            val fraction = (i + 0.5f) / barCount
            val inTrimWindow = isRecording || (fraction in trimStart..trimEnd)
            val isPlayed = fraction <= playProgress

            val amp = effectiveAmps[i].coerceIn(0.10f, 0.96f)
            val barH = h * amp
            val x = i * totalSlot + gap / 2f
            val y = (h - barH) / 2f

            val barColor = when {
                isRecording -> OpusRed
                !inTrimWindow -> if (isDark) Color(0xFF475569).copy(alpha = 0.45f) else Color(0xFFCBD5E1).copy(alpha = 0.45f)
                isPlayed -> if (isDark) Color(0xFFFB7185) else OpusRedDark
                else -> OpusRed
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // Draw Left & Right Trim Handle Boundary Lines
        if (!isRecording) {
            // Start handle
            drawLine(
                color = OpusRedDark,
                start = Offset(startX, 0f),
                end = Offset(startX, h),
                strokeWidth = 5f
            )
            drawCircle(
                color = OpusRedDark,
                radius = 7f,
                center = Offset(startX, 10f)
            )

            // End handle
            drawLine(
                color = OpusRedDark,
                start = Offset(endX, 0f),
                end = Offset(endX, h),
                strokeWidth = 5f
            )
            drawCircle(
                color = OpusRedDark,
                radius = 7f,
                center = Offset(endX, h - 10f)
            )

            // Playhead cursor line
            if (playProgress in 0.001f..0.999f) {
                drawLine(
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    start = Offset(playX, 0f),
                    end = Offset(playX, h),
                    strokeWidth = 3f
                )
            }
        }
    }
}

@Composable
private fun SpecBadge(
    title: String,
    value: String,
    isHighlighted: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        isHighlighted && isDark -> Color(0xFF4C0519)
        isHighlighted && !isDark -> OpusRedSurface
        !isHighlighted && isDark -> Color(0xFF222634)
        else -> OpusWhiteSurfaceVariant
    }
    val border = when {
        isHighlighted -> OpusRed
        isDark -> Color(0xFF2E3446)
        else -> OpusLightBorder
    }
    val titleColor = if (isDark) Color(0xFF94A3B8) else OpusLightTextSecondary
    val valColor = when {
        isHighlighted && isDark -> Color(0xFFFDA4AF)
        isHighlighted && !isDark -> OpusRedDark
        isDark -> Color(0xFFF8FAFC)
        else -> OpusLightTextPrimary
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 9.sp,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            color = valColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = (ms % 1000L) / 100L
    return String.format(Locale.US, "%d:%02d.%d", mins, secs, tenths)
}

private fun formatDurationSec(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}
