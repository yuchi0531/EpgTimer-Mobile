package com.starrow.epgtimer.ui

import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.starrow.epgtimer.data.model.CustomProgramGuide
import com.starrow.epgtimer.data.model.EpgAutoAddData
import com.starrow.epgtimer.di.AppContainer
import com.starrow.epgtimer.ui.autoadd.AutoAddEditScreen
import com.starrow.epgtimer.ui.autoadd.AutoAddForm
import com.starrow.epgtimer.ui.autoadd.autoAddFormOf
import com.starrow.epgtimer.ui.autoadd.autoAddFormOfSearchCondition
import com.starrow.epgtimer.ui.guide.GuideViewModel
import com.starrow.epgtimer.ui.guide.ProgramGuideScreen
import com.starrow.epgtimer.ui.program.ProgramDetailScreen
import com.starrow.epgtimer.ui.program.ProgramDetailViewModel
import com.starrow.epgtimer.ui.recording.RecordingScreen
import com.starrow.epgtimer.ui.reservation.ReserveDetailScreen
import com.starrow.epgtimer.ui.reservation.ReservationScreen
import com.starrow.epgtimer.ui.reservation.ReservationViewModel
import com.starrow.epgtimer.ui.search.SearchScreen
import com.starrow.epgtimer.ui.settings.SettingsScreen

private data class TopItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TOP_ITEMS = listOf(
    TopItem("guide", "番組表", Icons.Outlined.CalendarMonth),
    TopItem("reserve", "予約", Icons.Outlined.EventAvailable),
    TopItem("recording", "録画済み", Icons.Outlined.VideoLibrary),
    TopItem("search", "検索", Icons.Outlined.Search),
    TopItem("settings", "設定", Icons.Outlined.Settings),
)

private val TOP_ROUTES = TOP_ITEMS.map { it.route }.toSet()

private val GUIDE_MODES = listOf(
    CustomProgramGuide.VIEW_MODE_STANDARD to "標準",
    CustomProgramGuide.VIEW_MODE_LIST to "リスト",
    CustomProgramGuide.VIEW_MODE_WEEK to "週間",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgTimerRoot(
    container: AppContainer,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
) {
    val repository = container.repository
    val navController = rememberNavController()
    val context = LocalContext.current
    val toast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val guideViewModel: GuideViewModel = viewModel(factory = vmFactory { GuideViewModel(repository) })
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val isTopLevel = route in TOP_ROUTES

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isTopLevel) {
                AppTopBar(
                    route = route,
                    guideViewModel = guideViewModel,
                    onSearch = {
                        navController.navigate("search") { launchSingleTop = true }
                    },
                )
            }
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TOP_ITEMS.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "guide",
            modifier = Modifier.padding(padding),
        ) {
            composable("guide") {
                ProgramGuideScreen(
                    viewModel = guideViewModel,
                    onOpenEvent = { event, service ->
                        SelectedContent.event = event.event
                        SelectedContent.service = service
                        navController.navigate("programDetail?eventKey=${event.event.eventKey}")
                    },
                )
            }
            composable("reserve") {
                ReservationScreen(
                    repository = repository,
                    onOpenReserve = { reserve ->
                        navController.navigate("reserveDetail?reserveId=${reserve.reserveId}")
                    },
                    onAddAutoAdd = {
                        navController.navigate("autoAddEdit")
                    },
                    onEditAutoAdd = { item ->
                        SelectedContent.autoAdd = item
                        navController.navigate("autoAddEdit?dataId=${item.dataId}")
                    },
                )
            }
            composable("recording") {
                RecordingScreen(repository = repository)
            }
            composable("search") {
                SearchScreen(
                    repository = repository,
                    onOpenEvent = { event ->
                        navController.navigate("programDetail?eventKey=${event.eventKey}")
                    },
                    onRegisterAutoAdd = { condition ->
                        SelectedContent.autoAddDraft = condition
                        navController.navigate("autoAddEdit")
                    },
                )
            }
            composable("settings") {
                SettingsScreen(
                    repository = repository,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onToast = toast,
                )
            }
            composable(
                route = "programDetail?eventKey={eventKey}",
                arguments = listOf(
                    navArgument("eventKey") {
                        type = NavType.LongType
                        defaultValue = ProgramDetailViewModel.MISSING_EVENT_KEY
                    },
                ),
            ) { entry ->
                ProgramDetailScreen(
                    repository = repository,
                    eventKey = entry.arguments?.getLong("eventKey")
                        ?: ProgramDetailViewModel.MISSING_EVENT_KEY,
                    onBack = { navController.popBackStack() },
                    onToast = toast,
                )
            }
            composable(
                route = "autoAddEdit?dataId={dataId}",
                arguments = listOf(
                    navArgument("dataId") {
                        type = NavType.IntType
                        defaultValue = EpgAutoAddData.NEW_DATA_ID
                    },
                ),
            ) { entry ->
                val dataId = entry.arguments?.getInt("dataId") ?: EpgAutoAddData.NEW_DATA_ID
                val existing = SelectedContent.autoAdd?.takeIf { it.dataId == dataId }
                val draft = SelectedContent.autoAddDraft
                AutoAddEditScreen(
                    repository = repository,
                    initial = existing?.let { autoAddFormOf(it) }
                        ?: draft?.let { autoAddFormOfSearchCondition(it) }
                        ?: AutoAddForm(),
                    onBack = {
                        SelectedContent.autoAddDraft = null
                        navController.popBackStack()
                    },
                    onSaved = {
                        SelectedContent.autoAddDraft = null
                        navController.popBackStack()
                    },
                )
            }
            composable(
                route = "reserveDetail?reserveId={reserveId}",
                arguments = listOf(
                    navArgument("reserveId") {
                        type = NavType.IntType
                        defaultValue = ReservationViewModel.MISSING_RESERVE_ID
                    },
                ),
            ) { entry ->
                ReserveDetailScreen(
                    repository = repository,
                    reserveId = entry.arguments?.getInt("reserveId")
                        ?: ReservationViewModel.MISSING_RESERVE_ID,
                    onBack = { navController.popBackStack() },
                    onToast = toast,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    route: String?,
    guideViewModel: GuideViewModel,
    onSearch: () -> Unit,
) {
    val guides by guideViewModel.guides.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("EpgTimerNW") },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "検索")
            }
            if (route == "guide") {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "表示モード")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    val currentMode = guideViewModel.modeOverride
                        ?: guides.getOrNull(guideViewModel.tabIndex)?.viewMode
                    GUIDE_MODES.forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                guideViewModel.setDisplayMode(mode)
                                menuOpen = false
                            },
                            trailingIcon = {
                                if (currentMode == mode) {
                                    Icon(Icons.Filled.Check, contentDescription = "選択中")
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}
