package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PcmWavHelper
import com.example.audio.PlayerState
import com.example.data.db.VoiceTakeEntity
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AudioPlayerBottomCard(
    activeTake: VoiceTakeEntity?,
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onSeek: (Float) -> Unit,
    onRewind5s: () -> Unit,
    onForward5s: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onExportWav: (VoiceTakeEntity) -> Unit,
    onShare: (VoiceTakeEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeTake == null) return

    val amplitudes = PcmWavHelper.parseAmplitudes(activeTake.amplitudesJson)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(1.dp, StudioCardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = StudioSurface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header: Title and voice badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeTake.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = if (activeTake.isMultiSpeaker) "Multi-Speaker Dialogue" else "Voice: ${activeTake.speaker1Voice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioCyan
                    )
                }

                // Speed chip cycler (1.0x -> 1.25x -> 1.5x -> 2.0x)
                val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
                val nextSpeed = when (playerState.speed) {
                    1.0f -> 1.25f
                    1.25f -> 1.5f
                    1.5f -> 2.0f
                    else -> 1.0f
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioSurfaceVariant)
                        .border(1.dp, StudioCardBorder, RoundedCornerShape(8.dp))
                        .clickable { onSpeedChange(nextSpeed) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("speed_chip"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${playerState.speed}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Waveform Visualizer
            StudioWaveformVisualizer(
                amplitudes = amplitudes,
                progress = playerState.progress,
                isPlaying = playerState.isPlaying,
                onSeek = onSeek,
                height = 54.dp
            )

            // Timestamps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(playerState.currentPositionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = formatTime(playerState.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback and Export Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary action: Replay
                IconButton(
                    onClick = onReplay,
                    modifier = Modifier.testTag("replay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Replay",
                        tint = TextSecondary
                    )
                }

                // Rewind 5s
                IconButton(
                    onClick = onRewind5s,
                    modifier = Modifier.testTag("rewind_5s_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 5 seconds",
                        tint = TextSecondary
                    )
                }

                // Primary Play / Pause Button with radiant studio gradient
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(StudioCyan, StudioViolet))
                        )
                        .clickable { onPlayPause() }
                        .testTag("play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Forward 5s
                IconButton(
                    onClick = onForward5s,
                    modifier = Modifier.testTag("forward_5s_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 5 seconds",
                        tint = TextSecondary
                    )
                }

                // Action buttons: Export WAV & Share
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { onExportWav(activeTake) },
                        modifier = Modifier.testTag("export_wav_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export WAV file",
                            tint = StudioCyan
                        )
                    }

                    IconButton(
                        onClick = { onShare(activeTake) },
                        modifier = Modifier.testTag("share_voice_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share voice note",
                            tint = StudioViolet
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
}
