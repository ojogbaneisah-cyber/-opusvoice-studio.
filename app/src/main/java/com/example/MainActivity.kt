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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.db.VoiceTakeEntity
import com.example.data.model.UserAccountState
import com.example.ui.CaiCallSection
import com.example.ui.ROUTE_ACCOUNT_SYNC
import com.example.ui.ROUTE_CAI_CALL
import com.example.ui.ROUTE_HISTORY
import com.example.ui.ROUTE_HOME_DASHBOARD
import com.example.ui.ROUTE_RECORDING_STUDIO
import com.example.ui.ROUTE_SETTINGS
import com.example.ui.ROUTE_VOICE_CLONING
import com.example.ui.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.VoiceStudioSection
import com.example.ui.components.AudioPlayerBottomCard
import com.example.ui.components.ExportConfigurationDialog
import com.example.ui.screens.AccountSyncScreen
import com.example.ui.screens.CharacterAiCallScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.OpusVoiceStudioScreen
import com.example.ui.screens.SettingsScreen
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
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioSurface
import kotlinx.coroutines.launch

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
                val accountState by viewModel.accountState.collectAsState()
                val playerState by viewModel.playerState.collectAsState()
                val exportConfig by viewModel.exportConfig.collectAsState()
                val context = LocalContext.current

                val navController: NavHostController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME_DASHBOARD

                val navigateToTab: (StudioTab) -> Unit = { targetTab ->
                    viewModel.setTab(targetTab)
                    if (navController.currentDestination?.route != targetTab.route) {
                        navController.navigate(targetTab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // Keep NavController synchronized if ViewModel updates activeTab directly
                LaunchedEffect(uiState.activeTab) {
                    if (navController.currentDestination?.route != null &&
                        navController.currentDestination?.route != uiState.activeTab.route
                    ) {
                        navController.navigate(uiState.activeTab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                // Keep ViewModel activeTab synchronized when back stack pops
                LaunchedEffect(currentRoute) {
                    val matchedTab = StudioTab.entries.firstOrNull { it.route == currentRoute }
                    if (matchedTab != null && uiState.activeTab != matchedTab) {
                        viewModel.setTab(matchedTab)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.attemptSilentSignIn(context)
                }

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()

                var showExportModal by remember { mutableStateOf(false) }
                var exportTargetTake by remember { mutableStateOf<VoiceTakeEntity?>(null) }

                val bgMain = if (isDarkMode) StudioBackground else OpusWhiteSurface
                val navSurface = if (isDarkMode) StudioSurface else OpusWhiteBackground
                val borderCol = if (isDarkMode) StudioCardBorder else OpusLightBorder
                val unselectedCol = if (isDarkMode) Color(0xFF94A3B8) else OpusLightTextSecondary

                if (drawerState.isOpen) {
                    BackHandler {
                        coroutineScope.launch { drawerState.close() }
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

                val activeTabForBar = StudioTab.entries.firstOrNull { it.route == currentRoute } ?: uiState.activeTab

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        StudioNavigationDrawerContent(
                            activeTab = activeTabForBar,
                            accountState = accountState,
                            isDarkMode = isDarkMode,
                            onSelectTab = { tab ->
                                navigateToTab(tab)
                                coroutineScope.launch { drawerState.close() }
                            },
                            onSelectCallSubSection = { section ->
                                viewModel.setCallSection(section)
                                navigateToTab(StudioTab.CAI_CALL)
                                coroutineScope.launch { drawerState.close() }
                            },
                            onSelectStudioSubSection = { section ->
                                viewModel.setStudioSection(section)
                                navigateToTab(StudioTab.OPUS_TRIMMER)
                                coroutineScope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgMain),
                        containerColor = bgMain,
                        topBar = {
                            StudioTopAppBar(
                                isDarkMode = isDarkMode,
                                onToggleTheme = { viewModel.toggleDarkMode() },
                                activeTab = activeTabForBar,
                                accountState = accountState,
                                onOpenDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onSelectAccountSync = { navigateToTab(StudioTab.ACCOUNT_SYNC) },
                                onSelectSettings = { navigateToTab(StudioTab.SETTINGS) }
                            )
                        },
                        bottomBar = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                if (uiState.activeTake != null && currentRoute == ROUTE_HISTORY) {
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
                                        selected = currentRoute == ROUTE_HOME_DASHBOARD,
                                        onClick = { navigateToTab(StudioTab.HOME_DASHBOARD) },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Dashboard,
                                                contentDescription = "Home Dashboard",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        label = { Text("Home", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.White,
                                            selectedTextColor = OpusRed,
                                            indicatorColor = OpusRed,
                                            unselectedIconColor = unselectedCol,
                                            unselectedTextColor = unselectedCol
                                        ),
                                        modifier = Modifier.testTag("nav_tab_home")
                                    )

                                    NavigationBarItem(
                                        selected = currentRoute == ROUTE_RECORDING_STUDIO,
                                        onClick = { navigateToTab(StudioTab.OPUS_TRIMMER) },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.ContentCut,
                                                contentDescription = "48kHz Recording Studio",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        label = { Text("Studio", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) },
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
                                        selected = currentRoute == ROUTE_CAI_CALL,
                                        onClick = { navigateToTab(StudioTab.CAI_CALL) },
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
                                        selected = currentRoute == ROUTE_ACCOUNT_SYNC,
                                        onClick = { navigateToTab(StudioTab.ACCOUNT_SYNC) },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.CloudSync,
                                                contentDescription = "Account & Character.AI Sync",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        label = { Text("c.ai Sync", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.White,
                                            selectedTextColor = OpusRed,
                                            indicatorColor = OpusRed,
                                            unselectedIconColor = unselectedCol,
                                            unselectedTextColor = unselectedCol
                                        ),
                                        modifier = Modifier.testTag("nav_tab_account_sync")
                                    )

                                    NavigationBarItem(
                                        selected = currentRoute == ROUTE_SETTINGS,
                                        onClick = { navigateToTab(StudioTab.SETTINGS) },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Studio Configuration & Settings",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        label = { Text("Settings", fontSize = 10.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.White,
                                            selectedTextColor = OpusRed,
                                            indicatorColor = OpusRed,
                                            unselectedIconColor = unselectedCol,
                                            unselectedTextColor = unselectedCol
                                        ),
                                        modifier = Modifier.testTag("nav_tab_settings")
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_HOME_DASHBOARD,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(route = ROUTE_HOME_DASHBOARD) {
                                HomeDashboardScreen(
                                    viewModel = viewModel,
                                    paddingValues = paddingValues,
                                    onNavigateToRecordingStudio = { navigateToTab(StudioTab.OPUS_TRIMMER) },
                                    onNavigateToCallBridge = { navigateToTab(StudioTab.CAI_CALL) },
                                    onNavigateToSettings = { navigateToTab(StudioTab.SETTINGS) },
                                    onNavigateToAccountSync = { navigateToTab(StudioTab.ACCOUNT_SYNC) },
                                    onNavigateToHistory = { navigateToTab(StudioTab.HISTORY) },
                                    onNavigateToVoiceCloning = { navigateToTab(StudioTab.CLONING) }
                                )
                            }

                            composable(route = ROUTE_CAI_CALL) {
                                CharacterAiCallScreen(
                                    viewModel = viewModel,
                                    paddingValues = paddingValues
                                )
                            }

                            composable(route = ROUTE_RECORDING_STUDIO) {
                                OpusVoiceStudioScreen(
                                    viewModel = viewModel,
                                    paddingValues = paddingValues
                                )
                            }

                            composable(route = ROUTE_ACCOUNT_SYNC) {
                                AccountSyncScreen(
                                    viewModel = viewModel,
                                    paddingValues = paddingValues
                                )
                            }

                            composable(route = ROUTE_VOICE_CLONING) {
                                VoiceCloningScreen(
                                    viewModel = viewModel,
                                    paddingValues = paddingValues
                                )
                            }

                            composable(route = ROUTE_HISTORY) {
                                TakesHistoryScreen(
                                    viewModel = viewModel,
                                    onOpenExportModal = { take ->
                                        exportTargetTake = take
                                        showExportModal = true
                                    },
                                    paddingValues = paddingValues
                                )
                            }

                            composable(route = ROUTE_SETTINGS) {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    paddingValues = paddingValues,
                                    onNavigateToAccountPortal = { navigateToTab(StudioTab.ACCOUNT_SYNC) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioNavigationDrawerContent(
    activeTab: StudioTab,
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
            .width(305.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drawer Account & Active Synced Character Header Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardBg)
                    .border(1.dp, if (accountState.isSignedIn) StudioGreen else borderCol, RoundedCornerShape(14.dp))
                    .clickable { onSelectTab(StudioTab.ACCOUNT_SYNC) }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (accountState.isSignedIn) accountState.displayName else "Sign In / Sign Up",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textMain
                        )
                        Text(
                            text = if (accountState.isSignedIn) {
                                "${accountState.provider.label} • Synced"
                            } else {
                                "Apple ID • Google • Character.AI"
                            },
                            fontSize = 11.sp,
                            color = if (accountState.isSignedIn) StudioGreen else OpusRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "Active Persona: ${accountState.activeCharacter.name} (${accountState.activeCharacter.voiceProfileName})",
                    fontSize = 10.sp,
                    color = textSub
                )
            }

            DrawerMenuItem(
                title = "Home Dashboard",
                subtitle = "Overview, quick actions & studio status",
                icon = Icons.Default.Dashboard,
                selected = activeTab == StudioTab.HOME_DASHBOARD,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectTab(StudioTab.HOME_DASHBOARD) }
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "CHARACTER.AI CALL & CHIME BLOCKER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OpusRed,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            DrawerMenuItem(
                title = "Live Call Deck & Native App",
                subtitle = "Start call, mute, speaker & open c.ai app",
                icon = Icons.Default.PhoneInTalk,
                selected = activeTab == StudioTab.CAI_CALL,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectCallSubSection(CaiCallSection.LIVE_DECK) }
            )
            DrawerMenuItem(
                title = "Chime-Blocker DSP & Holdoff",
                subtitle = "Post-speech gate & 800Hz–2.8kHz notch",
                icon = Icons.Default.Settings,
                selected = false,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectCallSubSection(CaiCallSection.CHIME_DSP) }
            )
            DrawerMenuItem(
                title = "Character Persona & Voice",
                subtitle = "Edit synced character & voice preset",
                icon = Icons.Default.RecordVoiceOver,
                selected = false,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectCallSubSection(CaiCallSection.CHARACTER_VOICE) }
            )

            HorizontalDivider(color = borderCol, modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "48KHZ RECORDING STUDIO",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OpusRed,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            DrawerMenuItem(
                title = "1. Record 48kHz & Import",
                subtitle = "Mono AudioRecord pre-amp & file upload",
                icon = Icons.Default.ContentCut,
                selected = activeTab == StudioTab.OPUS_TRIMMER,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectStudioSubSection(VoiceStudioSection.RECORD_IMPORT) }
            )
            DrawerMenuItem(
                title = "2. Waveform & De-Chime Trimmer",
                subtitle = "Interactive trim handles & c.ai de-chimer",
                icon = Icons.Default.ContentCut,
                selected = false,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectStudioSubSection(VoiceStudioSection.WAVEFORM_TRIM) }
            )
            DrawerMenuItem(
                title = "3. Ogg/Opus Format & PTT Export",
                subtitle = "WhatsApp .opus, Telegram & Google Chat .ogg",
                icon = Icons.Default.ContentCut,
                selected = false,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectStudioSubSection(VoiceStudioSection.FORMAT_EXPORT) }
            )

            HorizontalDivider(color = borderCol, modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "ACCOUNTS, CLONING & CONFIGURATION",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OpusRed,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            DrawerMenuItem(
                title = "Account & Character.AI Sync",
                subtitle = "Apple ID, Google Email & c.ai login",
                icon = Icons.Default.CloudSync,
                selected = activeTab == StudioTab.ACCOUNT_SYNC,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectTab(StudioTab.ACCOUNT_SYNC) }
            )
            DrawerMenuItem(
                title = "Voice Clone Lab",
                subtitle = "Analyze & clone custom character voices",
                icon = Icons.Default.RecordVoiceOver,
                selected = activeTab == StudioTab.CLONING,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectTab(StudioTab.CLONING) }
            )
            DrawerMenuItem(
                title = "Saved Clips & Call History",
                subtitle = "Replay & share recorded takes",
                icon = Icons.Default.History,
                selected = activeTab == StudioTab.HISTORY,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectTab(StudioTab.HISTORY) }
            )
            DrawerMenuItem(
                title = "Studio Settings & Configuration",
                subtitle = "Audio Configuration, Integrations & Account",
                icon = Icons.Default.Settings,
                selected = activeTab == StudioTab.SETTINGS,
                textMain = textMain,
                textSub = textSub,
                onClick = { onSelectTab(StudioTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    textMain: Color,
    textSub: Color,
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
                contentDescription = null,
                tint = if (selected) OpusRed else textSub,
                modifier = Modifier.size(20.dp)
            )
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = OpusRed.copy(alpha = 0.14f),
            unselectedContainerColor = Color.Transparent
        )
    )
}

@Composable
fun StudioTopAppBar(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    activeTab: StudioTab,
    accountState: UserAccountState,
    onOpenDrawer: () -> Unit,
    onSelectAccountSync: () -> Unit,
    onSelectSettings: () -> Unit
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDarkMode) Color(0xFF222634) else OpusRedLight)
                        .testTag("top_bar_navigation_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Navigation Menu",
                        tint = OpusRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = activeTab.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleCol,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                    Text(
                        text = activeTab.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subCol,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Account & Sync Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (accountState.isSignedIn) StudioGreen.copy(alpha = 0.18f)
                            else if (activeTab == StudioTab.ACCOUNT_SYNC) OpusRed
                            else OpusRedLight
                        )
                        .clickable { onSelectAccountSync() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("top_bar_account_sync_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (accountState.isSignedIn) Icons.Default.VerifiedUser else Icons.Default.CloudSync,
                            contentDescription = "Account & c.ai Sync",
                            tint = when {
                                accountState.isSignedIn -> StudioGreen
                                activeTab == StudioTab.ACCOUNT_SYNC -> Color.White
                                else -> OpusRed
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (accountState.isSignedIn) {
                                accountState.activeCharacter.name.substringBefore(" ")
                            } else {
                                "Sign In"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                accountState.isSignedIn -> StudioGreen
                                activeTab == StudioTab.ACCOUNT_SYNC -> Color.White
                                else -> OpusRed
                            }
                        )
                    }
                }

                // Dedicated Settings Button
                IconButton(
                    onClick = onSelectSettings,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (activeTab == StudioTab.SETTINGS) OpusRed
                            else if (isDarkMode) Color(0xFF222634) else OpusRedLight
                        )
                        .testTag("top_bar_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Studio Settings",
                        tint = if (activeTab == StudioTab.SETTINGS) Color.White else OpusRed,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Theme Toggle Button
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color(0xFF222634) else OpusRedLight)
                        .testTag("theme_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle White / Dark Mode",
                        tint = OpusRed,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
