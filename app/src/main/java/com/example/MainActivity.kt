package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.VoiceTakeEntity
import com.example.ui.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.components.AudioPlayerBottomCard
import com.example.ui.components.ExportConfigurationDialog
import com.example.ui.screens.CharacterAiCallScreen
import com.example.ui.screens.OpusVoiceStudioScreen
import com.example.ui.screens.TakesHistoryScreen
import com.example.ui.screens.VoiceCloningScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OpusLightBorder
import com.example.ui.theme.OpusLightTextPrimary
import com.example.ui.theme.OpusLightTextSecondary
import com.example.ui.theme.OpusRed
import com.example.ui.theme.OpusRedLight
import com.example.ui.theme.OpusWhiteBackground
import com.example.ui.theme.OpusWhiteSurface
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioSurface

class MainActivity : ComponentActivity() {
    private val viewModel: StudioViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            MyApplicationTheme(darkTheme = isDarkMode) {
                val uiState by viewModel.uiState.collectAsState()
                val playerState by viewModel.playerState.collectAsState()
                val exportConfig by viewModel.exportConfig.collectAsState()
                val context = LocalContext.current

                var showExportModal by remember { mutableStateOf(false) }
                var exportTargetTake by remember { mutableStateOf<VoiceTakeEntity?>(null) }

                val bgMain = if (isDarkMode) StudioBackground else OpusWhiteSurface
                val navSurface = if (isDarkMode) StudioSurface else OpusWhiteBackground
                val borderCol = if (isDarkMode) StudioCardBorder else OpusLightBorder
                val unselectedCol = if (isDarkMode) Color(0xFF94A3B8) else OpusLightTextSecondary

                if (uiState.activeTab != StudioTab.OPUS_TRIMMER) {
                    BackHandler {
                        viewModel.setTab(StudioTab.OPUS_TRIMMER)
                    }
                }

                if (showExportModal) {
                    ExportConfigurationDialog(
                        targetTake = exportTargetTake,
                        batchExportCount = 0,
                        exportConfig = exportConfig,
                        onConfigChange = { viewModel.updateExportConfig(it) },
                        onConfirmDownload = {
                            exportTargetTake?.let { take ->
                                viewModel.exportTakeWithConfig(context, take, exportConfig)
                            }
                            showExportModal = false
                        },
                        onConfirmShare = {
                            exportTargetTake?.let { take ->
                                viewModel.shareTake(context, take, exportConfig)
                            }
                            showExportModal = false
                        },
                        onDismiss = { showExportModal = false }
                    )
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgMain),
                    containerColor = bgMain,
                    topBar = {
                        StudioTopAppBar(
                            isDarkMode = isDarkMode,
                            onToggleTheme = { viewModel.toggleDarkMode() },
                            activeTab = uiState.activeTab,
                            onSelectCallTab = { viewModel.setTab(StudioTab.CAI_CALL) }
                        )
                    },
                    bottomBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            if (uiState.activeTake != null && uiState.activeTab != StudioTab.OPUS_TRIMMER) {
                                AudioPlayerBottomCard(
                                    activeTake = uiState.activeTake,
                                    playerState = playerState,
                                    onPlayPause = { viewModel.audioPlayer.togglePlayPause() },
                                    onReplay = { viewModel.audioPlayer.replay() },
                                    onSeek = { viewModel.audioPlayer.seekTo(it) },
                                    onRewind5s = { viewModel.audioPlayer.rewind5Seconds() },
                                    onForward5s = { viewModel.audioPlayer.forward5Seconds() },
                                    onSpeedChange = { viewModel.setSpeed(it) },
                                    onExportWav = { take ->
                                        exportTargetTake = take
                                        showExportModal = true
                                    },
                                    onShare = { take ->
                                        viewModel.shareTake(context, take, exportConfig)
                                    }
                                )
                            }

                            NavigationBar(
                                containerColor = navSurface,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, borderCol)
                            ) {
                                NavigationBarItem(
                                    selected = uiState.activeTab == StudioTab.OPUS_TRIMMER,
                                    onClick = { viewModel.setTab(StudioTab.OPUS_TRIMMER) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.ContentCut,
                                            contentDescription = "Voice Notes & 48kHz Trimmer",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = { Text("Voice Notes", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = OpusRed,
                                        indicatorColor = OpusRed,
                                        unselectedIconColor = unselectedCol,
                                        unselectedTextColor = unselectedCol
                                    ),
                                    modifier = Modifier.testTag("nav_tab_opus_trimmer")
                                )

                                NavigationBarItem(
                                    selected = uiState.activeTab == StudioTab.CAI_CALL,
                                    onClick = { viewModel.setTab(StudioTab.CAI_CALL) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.PhoneInTalk,
                                            contentDescription = "Character.AI Live Call & Chime-Blocker",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = { Text("c.ai Call", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = OpusRed,
                                        indicatorColor = OpusRed,
                                        unselectedIconColor = unselectedCol,
                                        unselectedTextColor = unselectedCol
                                    ),
                                    modifier = Modifier.testTag("nav_tab_cai_call")
                                )

                                NavigationBarItem(
                                    selected = uiState.activeTab == StudioTab.CLONING,
                                    onClick = { viewModel.setTab(StudioTab.CLONING) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = "Character Voice Clone",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = { Text("Voice Clone", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = OpusRed,
                                        indicatorColor = OpusRed,
                                        unselectedIconColor = unselectedCol,
                                        unselectedTextColor = unselectedCol
                                    ),
                                    modifier = Modifier.testTag("nav_tab_cloning")
                                )

                                NavigationBarItem(
                                    selected = uiState.activeTab == StudioTab.HISTORY,
                                    onClick = { viewModel.setTab(StudioTab.HISTORY) },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "Saved Clips & Call Takes",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = { Text("Saved Clips", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = OpusRed,
                                        indicatorColor = OpusRed,
                                        unselectedIconColor = unselectedCol,
                                        unselectedTextColor = unselectedCol
                                    ),
                                    modifier = Modifier.testTag("nav_tab_history")
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    when (uiState.activeTab) {
                        StudioTab.OPUS_TRIMMER -> OpusVoiceStudioScreen(
                            viewModel = viewModel,
                            paddingValues = paddingValues
                        )
                        StudioTab.CAI_CALL -> CharacterAiCallScreen(
                            viewModel = viewModel,
                            paddingValues = paddingValues
                        )
                        StudioTab.CLONING -> VoiceCloningScreen(
                            viewModel = viewModel,
                            paddingValues = paddingValues
                        )
                        StudioTab.HISTORY -> TakesHistoryScreen(
                            viewModel = viewModel,
                            onOpenExportModal = { take ->
                                exportTargetTake = take
                                showExportModal = true
                            },
                            paddingValues = paddingValues
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudioTopAppBar(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    activeTab: StudioTab,
    onSelectCallTab: () -> Unit
) {
    val barBg = if (isDarkMode) StudioSurface else OpusWhiteBackground
    val borderCol = if (isDarkMode) StudioCardBorder else OpusLightBorder
    val titleCol = if (isDarkMode) Color(0xFFF8FAFC) else OpusLightTextPrimary
    val subCol = if (isDarkMode) Color(0xFF94A3B8) else OpusLightTextSecondary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .border(1.dp, borderCol),
        color = barBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        .background(OpusRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "OpusVoice Studio Icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "OpusVoice Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleCol,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "48kHz Mono • c.ai Call Bridge & PTT Voice Notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = subCol,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == StudioTab.CAI_CALL) OpusRed else OpusRedLight)
                        .clickable { onSelectCallTab() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("top_bar_cai_call_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = "c.ai Call Bridge",
                            tint = if (activeTab == StudioTab.CAI_CALL) Color.White else OpusRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "c.ai Call",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == StudioTab.CAI_CALL) Color.White else OpusRed
                        )
                    }
                }

                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFF222634) else OpusRedLight)
                        .testTag("theme_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle White / Dark Mode",
                        tint = OpusRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
