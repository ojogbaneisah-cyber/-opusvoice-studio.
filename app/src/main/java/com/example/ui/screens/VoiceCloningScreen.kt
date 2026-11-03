package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.audio.ClonedVoiceProfile
import com.example.audio.VoiceQualityReport
import com.example.ui.StudioViewModel
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun VoiceCloningScreen(
    viewModel: StudioViewModel,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val clonedVoices by viewModel.clonedVoices.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingClone.collectAsState()
    val cloneReport by viewModel.cloneReport.collectAsState()
    val selectedSampleUri by viewModel.selectedCloneSampleUri.collectAsState()
    val isRecordingMic by viewModel.opusAudioEngine.isRecordingMic.collectAsState()
    val recDurationMs by viewModel.opusAudioEngine.recordingDurationMs.collectAsState()

    var cloneNameInput by remember { mutableStateOf("My Custom Voice") }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onAudioSampleSelected(uri)
        }
    }

    val cloneMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleCloneMicrophoneRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required for 48kHz voice cloning.", Toast.LENGTH_LONG).show()
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
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "AI VOICE CLONING STUDIO",
                style = MaterialTheme.typography.titleMedium,
                color = StudioCyan,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "Upload or record a 48kHz Mono audio sample of your voice to train a custom neural voice profile for the studio.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // Quality Instructions & Guidelines Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, StudioCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            color = StudioSurfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = StudioCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "AUDIO QUALITY INSTRUCTIONS FOR BEST RESULTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioCyan,
                        letterSpacing = 1.sp
                    )
                }

                QualityCriterionItem(
                    title = "Optimal Duration: 1 to 5 Minutes",
                    desc = "Shorter than 30s yields low variety; 1-5 minutes provides rich vocal intonation."
                )
                QualityCriterionItem(
                    title = "Acoustic Environment: Quiet Room",
                    desc = "Zero background music, HVAC hum, or room echo. A carpeted room or closet works best."
                )
                QualityCriterionItem(
                    title = "Mic Technique: 15–20 cm Distance",
                    desc = "Maintain consistent distance without speaking directly into the capsule to avoid plosive pops."
                )
                QualityCriterionItem(
                    title = "Natural Articulation & Energy",
                    desc = "Read a natural passage with relaxed pacing, varied cadence, and comfortable volume."
                )
            }
        }

        // Audio Input Options Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, StudioCardBorder, RoundedCornerShape(16.dp)),
            color = StudioSurface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "RECORD 48KHZ MONO OR UPLOAD AUDIO SAMPLE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Record 48kHz Mic Button
                    Button(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                viewModel.toggleCloneMicrophoneRecording()
                            } else {
                                cloneMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecordingMic) StudioCoral else StudioCyan,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("record_clone_sample_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingMic) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRecordingMic) "Stop (${recDurationMs / 1000}s)" else "Record 48k",
                            fontSize = 12.sp
                        )
                    }

                    // Upload File Button
                    Button(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        enabled = !isRecordingMic,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioSurfaceVariant,
                            contentColor = StudioCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
                            .testTag("upload_audio_sample_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload", fontSize = 12.sp)
                    }

                    // Use Demo Voice Sample Button
                    Button(
                        onClick = { viewModel.useDemoVoiceSample() },
                        enabled = !isRecordingMic,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioSurfaceVariant,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
                            .testTag("use_demo_sample_button")
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sample", fontSize = 12.sp)
                    }
                }

                if (selectedSampleUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSurfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StudioGreen, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Audio sample loaded and ready for analysis",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Analysis Results Card (if sample selected)
        if (isAnalyzing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = StudioCyan, strokeWidth = 3.dp)
                    Text("Analyzing vocal acoustics & pitch profile...", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else if (cloneReport != null) {
            val report = cloneReport!!
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, StudioViolet.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                color = StudioSurface
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
                            text = "ACOUSTIC ANALYSIS REPORT",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioViolet,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (report.qualityScore >= 75) StudioGreen.copy(alpha = 0.2f) else StudioAmber.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Score: ${report.qualityScore}/100",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (report.qualityScore >= 75) StudioGreen else StudioAmber
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { report.qualityScore / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (report.qualityScore >= 75) StudioGreen else StudioAmber,
                        trackColor = StudioSurfaceVariant
                    )

                    Text(
                        text = report.qualityAssessment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Duration: ${report.durationSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "Estimated Pitch: ~${report.estimatedPitchHz} Hz",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioCyan
                        )
                        Text(
                            text = "Gender: ${report.estimatedGender}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioViolet
                        )
                    }

                    if (report.warnings.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            report.warnings.forEach { warn ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(14.dp))
                                    Text(warn, fontSize = 11.sp, color = StudioAmber)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Clone Name Input
                    OutlinedTextField(
                        value = cloneNameInput,
                        onValueChange = { cloneNameInput = it },
                        label = { Text("Name Your Cloned Voice") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clone_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = StudioSurfaceVariant,
                            unfocusedContainerColor = StudioSurfaceVariant,
                            focusedBorderColor = StudioCyan,
                            unfocusedBorderColor = StudioCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Generate Voice Clone CTA
                    Button(
                        onClick = { viewModel.createVoiceClone(cloneNameInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_voice_clone_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("SAVE & ACTIVATE CLONED VOICE", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Active Cloned Voices List
        if (clonedVoices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "YOUR CLONED VOICES (${clonedVoices.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioCyan,
                    letterSpacing = 1.sp
                )

                clonedVoices.forEach { clone ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, StudioCardBorder, RoundedCornerShape(14.dp)),
                        color = StudioSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(StudioCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🎙️", fontSize = 18.sp)
                                }
                                Column {
                                    Text(clone.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(
                                        text = "${clone.gender} • ${clone.sampleDurationSec}s sample • Score: ${clone.qualityScore}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.selectClonedVoiceForStudio(clone) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioCyan.copy(alpha = 0.15f),
                                    contentColor = StudioCyan
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Use in Studio", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun QualityCriterionItem(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("•", color = StudioCyan, fontSize = 16.sp)
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontSize = 11.sp)
        }
    }
}
