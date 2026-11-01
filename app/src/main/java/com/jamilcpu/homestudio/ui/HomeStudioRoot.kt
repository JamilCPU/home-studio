package com.jamilcpu.homestudio.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jamilcpu.homestudio.AppContainer
import com.jamilcpu.homestudio.data.api.HomeStudioApi
import com.jamilcpu.homestudio.data.NowPlayingRepository
import com.jamilcpu.homestudio.ui.agent.AgentRoute
import com.jamilcpu.homestudio.ui.agent.AgentViewModel
import com.jamilcpu.homestudio.ui.connect.ConnectScreen
import com.jamilcpu.homestudio.ui.history.HistoryRoute
import com.jamilcpu.homestudio.ui.history.HistoryViewModel
import com.jamilcpu.homestudio.ui.now.NowPlayingRoute
import com.jamilcpu.homestudio.ui.queue.QueueRoute
import com.jamilcpu.homestudio.ui.queue.QueueViewModel
import com.jamilcpu.homestudio.ui.rate.RateSheetController
import com.jamilcpu.homestudio.ui.rate.RateSheetHost
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "HomeStudioRoot"
private const val RATED_MESSAGE = "Rated"

/** App entry: wires the real [AppContainer] into the testable overload below. */
@Composable
fun HomeStudioRoot(container: AppContainer) {
    val scope = rememberCoroutineScope()
    HomeStudioRoot(
        container.serverUrl,
        container.api,
        nowRepo = container.nowPlaying,
        rateSheet = container.rateSheet,
    ) { url ->
        scope.launch {
            try {
                container.settings.setServerUrl(url)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Rejected server URL", e)
            }
        }
    }
}

/** Shows [ConnectScreen] until a server URL is saved, then the tabbed app shell. */
@Composable
fun HomeStudioRoot(
    serverUrl: StateFlow<String?>,
    api: HomeStudioApi,
    nowRepo: NowPlayingRepository? = null,
    rateSheet: RateSheetController = remember { RateSheetController() },
    onSaveUrl: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val url by serverUrl.collectAsState()
    if (url == null) {
        ConnectScreen(api = api, onSave = onSaveUrl)
    } else {
        AppShell(api, nowRepo ?: remember(api) { NowPlayingRepository(api, scope) }, rateSheet)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppShell(
    api: HomeStudioApi,
    nowRepo: NowPlayingRepository,
    rateSheet: RateSheetController,
    nav: NavHostController = rememberNavController(),
) {
    val scope = rememberCoroutineScope()
    // Hoisted above the NavHost so a half-typed request survives switching tabs.
    val agentVm = remember(api) { AgentViewModel(api, scope) }
    val queueVm = remember(api) { QueueViewModel(api, scope) }
    val historyVm = remember(api) { HistoryViewModel(api, scope) }
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    RateSheetHost(rateSheet, api) {
        historyVm.refresh()
        scope.launch { snackbar.showSnackbar(RATED_MESSAGE) }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(titleFor(route), modifier = Modifier.testTag("topBarTitle")) },
                navigationIcon = {
                    if (route == Routes.MORNING) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (route != Routes.MORNING) {
                        IconButton(onClick = { nav.navigate(Routes.MORNING) { launchSingleTop = true } }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Morning settings")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                BottomTabs.forEach { tab ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab_${tab.route}"),
                        selected = route == tab.route,
                        onClick = { nav.switchTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.START, modifier = Modifier.padding(padding)) {
            composable(Routes.NOW) {
                NowPlayingRoute(
                    repo = nowRepo,
                    api = api,
                    onRate = { videoId ->
                        val item = nowRepo.now.value?.item
                        rateSheet.open(videoId, title = item?.title?.takeIf { item.videoId == videoId })
                    },
                    onAskAgent = { nav.switchTab(Routes.AGENT) },
                )
            }
            composable(Routes.AGENT) { AgentRoute(agentVm) }
            composable(Routes.QUEUE) { QueueRoute(queueVm) }
            composable(Routes.HISTORY) { HistoryRoute(historyVm, rateSheet) }
            listOf(Routes.TASTE, Routes.MORNING).forEach { r ->
                composable(r) { PlaceholderScreen(r) }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Stand-in until the real screens land (Tasks 6-8). */
@Composable
private fun PlaceholderScreen(route: String) {
    Box(Modifier.testTag("screen_$route"), contentAlignment = Alignment.Center) {
        Text(titleFor(route))
    }
}
