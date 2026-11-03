package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.window.Dialog
import com.example.audio.AudioExportConfig
import com.example.audio.ExportBitDepth
import com.example.audio.ExportChannels
import com.example.audio.ExportSampleRate
import com.example.data.db.VoiceTakeEntity
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ExportConfigurationDialog(
    targetTake: VoiceTakeEntity?,
    batchExportCount: Int = 0,
    exportConfig: AudioExportConfig,
    onConfigChange: (AudioExportConfig) -> Unit,
    onConfirmDownload: () -> Unit,
    onConfirmShare: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, StudioCardBorder, RoundedCornerShape(20.dp))
                .testTag("export_config_dialog"),
            color = StudioSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
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
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "AUDIO EXPORT CONFIGURATION",
                                style = MaterialTheme.typography.titleSmall,
                                color = StudioCyan,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (batchExportCount > 0) "Batch Export ($batchExportCount completed files)"
                                else targetTake?.title ?: "Master Audio Output",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close export dialog",
                            tint = TextSecondary
                        )
                    }
                }

                // Sample Rate Selection (24kHz vs 44.1kHz vs 48kHz)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SAMPLE RATE (FREQUENCY RESOLUTION)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    ExportSampleRate.entries.forEach { rate ->
                        val isSelected = exportConfig.sampleRate == rate
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (isSelected) StudioCyan else StudioCardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onConfigChange(exportConfig.copy(sampleRate = rate)) }
                                .testTag("export_rate_${rate.hz}"),
                            color = if (isSelected) StudioSurfaceVariant else StudioSurface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = rate.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) StudioCyan else TextPrimary
                                    )
                                    Text(
                                        text = rate.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = StudioCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bit Depth Selection (16-bit vs 24-bit vs 8-bit)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "BIT DEPTH (DYNAMIC HEADROOM)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExportBitDepth.entries.forEach { depth ->
                            val isSelected = exportConfig.bitDepth == depth
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        1.dp,
                                        if (isSelected) StudioViolet else StudioCardBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onConfigChange(exportConfig.copy(bitDepth = depth)) }
                                    .testTag("export_bit_${depth.bits}"),
                                color = if (isSelected) StudioSurfaceVariant else StudioSurface
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = depth.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) StudioViolet else TextPrimary
                                    )
                                    Text(
                                        text = if (depth.bits == 24) "Studio Pro" else if (depth.bits == 16) "Standard" else "Compact",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Channel & Peak Normalization Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Channels toggle
                    ExportChannels.entries.forEach { ch ->
                        val isSelected = exportConfig.channels == ch
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) StudioCyan.copy(alpha = 0.15f) else StudioSurfaceVariant)
                                .border(1.dp, if (isSelected) StudioCyan else StudioCardBorder, RoundedCornerShape(8.dp))
                                .clickable { onConfigChange(exportConfig.copy(channels = ch)) }
                                .padding(vertical = 8.dp)
                                .testTag("export_channel_${ch.count}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ch.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) StudioCyan else TextSecondary
                            )
                        }
                    }
                }

                // Peak Normalization Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Peak Loudness Normalization (-0.5 dBFS)",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Maximizes clarity without digital clipping",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = exportConfig.normalizeAudio,
                        onCheckedChange = { onConfigChange(exportConfig.copy(normalizeAudio = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = StudioGreen),
                        modifier = Modifier.testTag("export_normalize_switch")
                    )
                }

                // Estimated File Size Readout
                val durationSec = ((targetTake?.durationMs ?: 10000L) / 1000.0).coerceAtLeast(1.0)
                val bytesPerSec = exportConfig.sampleRate.hz * exportConfig.channels.count * (exportConfig.bitDepth.bits / 8)
                val estKb = ((durationSec * bytesPerSec) / 1024.0).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Spec: ${exportConfig.sampleRate.label} • ${exportConfig.bitDepth.label} • ${exportConfig.channels.label}",
                        fontSize = 11.sp,
                        color = StudioAmber
                    )
                    Text(
                        text = "~$estKb KB / file",
                        fontSize = 11.sp,
                        color = StudioCyan
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onConfirmShare != null && batchExportCount == 0) {
                        Button(
                            onClick = onConfirmShare,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioViolet.copy(alpha = 0.2f),
                                contentColor = StudioViolet
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("confirm_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = onConfirmDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(46.dp)
                            .testTag("confirm_export_download_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (batchExportCount > 0) "Export All ($batchExportCount)" else "Download .WAV",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
