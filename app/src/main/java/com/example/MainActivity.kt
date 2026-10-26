package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ThemeMode
import com.example.ui.components.BillingDialog
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.PodcastDetailSheet
import com.example.ui.components.ProfileBottomSheet
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleGreen
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleYellow
import com.example.ui.theme.IPodcastTheme
import com.example.viewmodel.PodcastViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PodcastViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appSettings by viewModel.appSettings.collectAsState()
            val isDarkTheme = when (appSettings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            IPodcastTheme(darkTheme = isDarkTheme) {
                MainApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: PodcastViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // State bindings
    val subscribedPodcasts by viewModel.subscribedPodcasts.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val queueEpisodes by viewModel.queueEpisodes.collectAsState()
    val downloadedEpisodes by viewModel.downloadedEpisodes.collectAsState()
    val historyEpisodes by viewModel.historyEpisodes.collectAsState()

    val currentEpisode by viewModel.currentEpisode.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoadingAudio by viewModel.isLoadingAudio.collectAsState()
    val currentPositionSeconds by viewModel.currentPositionSeconds.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val sleepTimerDuration by viewModel.sleepTimerDuration.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()

    val subscriptionState by viewModel.subscriptionState.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val isBillingSheetOpen by viewModel.isBillingSheetOpen.collectAsState()
    val isProfileSheetOpen by viewModel.isProfileSheetOpen.collectAsState()
    val isFullPlayerOpen by viewModel.isFullPlayerOpen.collectAsState()
    val selectedPodcastDetail by viewModel.selectedPodcastDetail.collectAsState()
    val detailEpisodes by viewModel.detailEpisodes.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    var currentNavTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Google Podcasts Minimalist Equalizer Logo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Box(modifier = Modifier.width(3.5.dp).height(14.dp).clip(CircleShape).background(GoogleRed))
                            Box(modifier = Modifier.width(3.5.dp).height(20.dp).clip(CircleShape).background(GoogleYellow))
                            Box(modifier = Modifier.width(3.5.dp).height(26.dp).clip(CircleShape).background(GoogleBlue))
                            Box(modifier = Modifier.width(3.5.dp).height(18.dp).clip(CircleShape).background(GoogleGreen))
                            Box(modifier = Modifier.width(3.5.dp).height(12.dp).clip(CircleShape).background(GoogleRed))
                        }

                        Text(
                            text = "iPodcast",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Direct Menu Action Button (replacing profile circle as requested)
                    IconButton(
                        onClick = { viewModel.openProfileSheet() },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("topbar_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menü ve Ayarlar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                // Docked Mini Player
                AnimatedVisibility(
                    visible = currentEpisode != null && !isFullPlayerOpen,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    currentEpisode?.let { ep ->
                        MiniPlayer(
                            episode = ep,
                            isPlaying = isPlaying,
                            isLoading = isLoadingAudio,
                            currentPositionSeconds = currentPositionSeconds,
                            durationSeconds = durationSeconds,
                            onExpand = { viewModel.openFullPlayer() },
                            onPlayPause = { viewModel.togglePlayPause(ep) },
                            onSeekBackward10 = { viewModel.seekBackward10() },
                            onSeekForward30 = { viewModel.seekForward30() }
                        )
                    }
                }

                // Google Podcasts Style Bottom Navigation Bar
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentNavTab == 0,
                        onClick = { currentNavTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (currentNavTab == 0) Icons.Default.Home else Icons.Outlined.Home,
                                contentDescription = "Ana Sayfa"
                            )
                        },
                        label = { Text("Ana Sayfa") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_home")
                    )

                    NavigationBarItem(
                        selected = currentNavTab == 1,
                        onClick = { currentNavTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (currentNavTab == 1) Icons.Default.Search else Icons.Outlined.Search,
                                contentDescription = "Keşfet & Ara"
                            )
                        },
                        label = { Text("Keşfet") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_search")
                    )

                    NavigationBarItem(
                        selected = currentNavTab == 2,
                        onClick = { currentNavTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (currentNavTab == 2) Icons.Default.LibraryMusic else Icons.Outlined.LibraryMusic,
                                contentDescription = "Kitaplık"
                            )
                        },
                        label = { Text("Kitaplık") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_library")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentNavTab) {
                0 -> HomeScreen(
                    subscribedPodcasts = subscribedPodcasts,
                    episodes = episodes,
                    currentPlayingEpisodeId = currentEpisode?.id,
                    isPlaying = isPlaying,
                    onNavigateToSearch = { currentNavTab = 1 },
                    onSelectPodcast = { viewModel.selectPodcastForDetail(it) },
                    onPlayEpisode = { viewModel.playEpisode(it) },
                    onToggleQueue = { viewModel.toggleQueue(it) },
                    onToggleDownload = { viewModel.toggleDownload(it) }
                )

                1 -> SearchScreen(
                    searchQuery = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    searchResults = searchResults,
                    isSearching = isSearching,
                    onSelectPodcast = { viewModel.selectPodcastForDetail(it) },
                    onToggleSubscribe = { viewModel.toggleSubscription(it) },
                    onAddByRss = { viewModel.addPodcastByRss(it) }
                )

                2 -> LibraryScreen(
                    queueEpisodes = queueEpisodes,
                    downloadedEpisodes = downloadedEpisodes,
                    historyEpisodes = historyEpisodes,
                    subscribedPodcasts = subscribedPodcasts,
                    currentPlayingEpisodeId = currentEpisode?.id,
                    isPlaying = isPlaying,
                    onSelectPodcast = { viewModel.selectPodcastForDetail(it) },
                    onPlayEpisode = { viewModel.playEpisode(it) },
                    onToggleQueue = { viewModel.toggleQueue(it) },
                    onToggleDownload = { viewModel.toggleDownload(it) }
                )
            }
        }
    }

    // Google Podcasts Style Menu & Settings Bottom Sheet (Sole place for Developer Support)
    if (isProfileSheetOpen) {
        ProfileBottomSheet(
            subscriptionState = subscriptionState,
            appSettings = appSettings,
            onDismiss = { viewModel.closeProfileSheet() },
            onOpenBilling = {
                viewModel.closeProfileSheet()
                viewModel.openBillingSheet()
            },
            onSetThemeMode = { viewModel.setThemeMode(it) },
            onToggleWifiOnly = { viewModel.setDownloadOnlyOnWifi(it) },
            onToggleAutoDelete = { viewModel.setAutoDeletePlayed(it) },
            onClearCache = { viewModel.clearCache() },
            storageUsage = viewModel.getStorageUsage()
        )
    }

    // Podcast Detail Overlay Sheet (Real Episodes from RSS)
    selectedPodcastDetail?.let { pod ->
        PodcastDetailSheet(
            podcast = pod,
            episodes = detailEpisodes,
            currentPlayingEpisodeId = currentEpisode?.id,
            isPlaying = isPlaying,
            onBack = { viewModel.clearPodcastDetail() },
            onToggleSubscribe = { viewModel.toggleSubscription(it) },
            onPlayEpisode = { viewModel.playEpisode(it) },
            onToggleQueue = { viewModel.toggleQueue(it) },
            onToggleDownload = { viewModel.toggleDownload(it) }
        )
    }

    // Full Screen Player Sheet
    if (isFullPlayerOpen && currentEpisode != null) {
        currentEpisode?.let { ep ->
            FullPlayerSheet(
                episode = ep,
                isPlaying = isPlaying,
                isLoading = isLoadingAudio,
                currentPositionSeconds = currentPositionSeconds,
                durationSeconds = durationSeconds,
                playbackSpeed = playbackSpeed,
                sleepTimerDuration = sleepTimerDuration,
                sleepTimerRemaining = sleepTimerRemaining,
                onDismiss = { viewModel.closeFullPlayer() },
                onPlayPause = { viewModel.togglePlayPause(ep) },
                onSeekTo = { viewModel.seekTo(it) },
                onSeekBackward10 = { viewModel.seekBackward10() },
                onSeekForward30 = { viewModel.seekForward30() },
                onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                onSleepTimerSelected = { viewModel.setSleepTimer(it) }
            )
        }
    }

    // 50 TL Developer Support Modal Sheet
    if (isBillingSheetOpen) {
        BillingDialog(
            subscriptionState = subscriptionState,
            supportPlan = viewModel.supportPlan,
            plusFeatures = viewModel.plusFeatures,
            onDismiss = { viewModel.closeBillingSheet() },
            onPurchaseSupport = { viewModel.purchaseSupport() },
            onRedeemPromoCode = { viewModel.redeemPromoCode(it) },
            onRestorePurchases = { viewModel.restorePurchases() }
        )
    }
}
