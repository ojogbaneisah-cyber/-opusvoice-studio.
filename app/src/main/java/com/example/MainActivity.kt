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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Login
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
import com.example.ui.ROUTE_LOGIN
import com.example.ui.ROUTE_RECORDING_STUDIO
import com.example.ui.ROUTE_SETTINGS
import com.example.ui.ROUTE_VOICE_CLONING
import com.example.ui.StudioTab
import com.example.ui.StudioViewModel
import com.example.ui.VoiceStudioSection
import com.example.ui.components.AudioPlayerBottomCard
import com.example.ui.components.ExportConfigurationDialog
import com.example.ui.components.StudioNavigationDrawer
import com.example.ui.screens.AccountSyncScreen
import com.example.ui.screens.CharacterAiCallScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LoginScreen
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

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME_DASHBOARD

                val activeTab = remember(currentRoute) {
                    StudioTab.entries.firstOrNull { it.route == currentRoute } ?: StudioTab.HOME_DASHBOARD
                }

                // Keep ViewModel activeTab and NavController synchronized in both directions
                LaunchedEffect(currentRoute) {
                    val matchedTab = StudioTab.entries.firstOrNull { it.route == currentRoute }
                    if (matchedTab != null && uiState.activeTab != matchedTab) {
                        viewModel.setTab(matchedTab)
                    }
                }

                LaunchedEffect(uiState.activeTab) {
                    if (currentRoute != uiState.activeTab.route) {
                        navigateToStudioTab(navController, viewModel, uiState.activeTab)
                    }
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

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        StudioNavigationDrawer(
                            activeTab = activeTab,
                            activeCallSection = uiState.activeCallSection,
                            activeStudioSection = uiState.activeStudioSection,
                            accountState = accountState,
                            isDarkMode = isDarkMode,
                            onSelectTab = { tab ->
                                navigateToStudioTab(navController, viewModel, tab)
                                coroutineScope.launch { drawerState.close() }
                            },
                            onSelectCallSubSection = { section ->
                                viewModel.setCallSection(section)
                                navigateToStudioTab(navController, viewModel, StudioTab.CAI_CALL)
                                coroutineScope.launch { drawerState.close() }
                            },
                            onSelectStudioSubSection = { section ->
                                viewModel.setStudioSection(section)
                                navigateToStudioTab(navController, viewModel, StudioTab.OPUS_TRIMMER)
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
                                activeTab = activeTab,
                                accountState = accountState,
                                onOpenDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onSelectAccountSync = {
                                    if (accountState.isSignedIn) {
                                        navigateToStudioTab(navController, viewModel, StudioTab.ACCOUNT_SYNC)
                                    } else {
                                        navigateToStudioTab(navController, viewModel, StudioTab.LOGIN)
                                    }
                                },
                                onSelectSettings = {
                                    navigateToStudioTab(navController, viewModel, StudioTab.SETTINGS)
                                }
                            )
                        },
                        bottomBar = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                if (uiState.activeTake != null && activeTab == StudioTab.HISTORY) {
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
                                        selected = activeTab == StudioTab.HOME_DASHBOARD,
                                        onClick = {
                                            navigateToStudioTab(navController, viewModel, StudioTab.HOME_DASHBOARD)
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.Home,
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
                                        modifier = Modifier.testTag("nav_tab_home_dashboard")
                                    )

                                    NavigationBarItem(
                                        selected = activeTab == StudioTab.CAI_CALL,
                                        onClick = {
                                            navigateToStudioTab(navController, viewModel, StudioTab.CAI_CALL)
                                        },
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
                                        selected = activeTab == StudioTab.OPUS_TRIMMER,
                                        onClick = {
                                            navigateToStudioTab(navController, viewModel, StudioTab.OPUS_TRIMMER)
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.ContentCut,
                                                contentDescription = "48kHz Voice Note Studio",
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
                                        selected = activeTab == StudioTab.ACCOUNT_SYNC || activeTab == StudioTab.LOGIN,
                                        onClick = {
                                            navigateToStudioTab(navController, viewModel, StudioTab.ACCOUNT_SYNC)
                                        },
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
                                        selected = activeTab == StudioTab.SETTINGS,
                                        onClick = {
                                            navigateToStudioTab(navController, viewModel, StudioTab.SETTINGS)
                                        },
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
                        StudioNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            paddingValues = paddingValues,
                            onOpenExportModal = { take ->
                                exportTargetTake = take
                                showExportModal = true
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun navigateToStudioTab(
    navController: NavHostController,
    viewModel: StudioViewModel,
    tab: StudioTab
) {
    viewModel.setTab(tab)
    navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun StudioNavHost(
    navController: NavHostController,
    viewModel: StudioViewModel,
    paddingValues: PaddingValues,
    onOpenExportModal: (VoiceTakeEntity) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME_DASHBOARD,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(ROUTE_HOME_DASHBOARD) {
            HomeDashboardScreen(
                viewModel = viewModel,
                paddingValues = paddingValues,
                onNavigateToRecordingStudio = {
                    navigateToStudioTab(navController, viewModel, StudioTab.OPUS_TRIMMER)
                },
                onNavigateToCallBridge = {
                    navigateToStudioTab(navController, viewModel, StudioTab.CAI_CALL)
                },
                onNavigateToSettings = {
                    navigateToStudioTab(navController, viewModel, StudioTab.SETTINGS)
                },
                onNavigateToAccountSync = {
                    navigateToStudioTab(navController, viewModel, StudioTab.ACCOUNT_SYNC)
                },
                onNavigateToHistory = {
                    navigateToStudioTab(navController, viewModel, StudioTab.HISTORY)
                },
                onNavigateToVoiceCloning = {
                    navigateToStudioTab(navController, viewModel, StudioTab.CLONING)
                }
            )
        }
        composable(ROUTE_CAI_CALL) {
            CharacterAiCallScreen(
                viewModel = viewModel,
                paddingValues = paddingValues
            )
        }
        composable(ROUTE_RECORDING_STUDIO) {
            OpusVoiceStudioScreen(
                viewModel = viewModel,
                paddingValues = paddingValues
            )
        }
        composable(ROUTE_ACCOUNT_SYNC) {
            AccountSyncScreen(
                viewModel = viewModel,
                paddingValues = paddingValues
            )
        }
        composable(ROUTE_VOICE_CLONING) {
            VoiceCloningScreen(
                viewModel = viewModel,
                paddingValues = paddingValues
            )
        }
        composable(ROUTE_HISTORY) {
            TakesHistoryScreen(
                viewModel = viewModel,
                onOpenExportModal = onOpenExportModal,
                paddingValues = paddingValues
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                paddingValues = paddingValues,
                onNavigateToAccountPortal = {
                    navigateToStudioTab(navController, viewModel, StudioTab.LOGIN)
                }
            )
        }
        composable(ROUTE_LOGIN) {
            LoginScreen(
                viewModel = viewModel,
                paddingValues = paddingValues,
                onNavigateToHome = {
                    navigateToStudioTab(navController, viewModel, StudioTab.HOME_DASHBOARD)
                },
                onNavigateToAccountSync = {
                    navigateToStudioTab(navController, viewModel, StudioTab.ACCOUNT_SYNC)
                }
            )
        }
    }
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
                            else if (activeTab == StudioTab.ACCOUNT_SYNC || activeTab == StudioTab.LOGIN) OpusRed
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
                                activeTab == StudioTab.ACCOUNT_SYNC || activeTab == StudioTab.LOGIN -> Color.White
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
                                activeTab == StudioTab.ACCOUNT_SYNC || activeTab == StudioTab.LOGIN -> Color.White
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
