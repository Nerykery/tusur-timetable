package ru.tusur.timetable.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ru.tusur.timetable.BuildConfig
import ru.tusur.timetable.R
import ru.tusur.timetable.domain.model.StartScreen
import ru.tusur.timetable.ui.about.AboutScreen
import ru.tusur.timetable.ui.map.BuildingMapScreen
import ru.tusur.timetable.ui.map.BuildingMapViewModel
import ru.tusur.timetable.ui.picker.PickerViewModel
import ru.tusur.timetable.ui.schedule.ScheduleScreen
import ru.tusur.timetable.ui.schedule.ScheduleViewModel
import ru.tusur.timetable.ui.settings.SettingsScreen
import ru.tusur.timetable.ui.settings.SettingsViewModel
import ru.tusur.timetable.ui.util.openUrl
import ru.tusur.timetable.ui.util.shareText

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    applyStartScreen: Boolean,
    startScreen: StartScreen,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(
            route = "schedule?date={date}&source={source}",
            arguments = listOf(
                navArgument("date") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("source") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ScheduleRoute(navController)
        }

        composable(DeepLinks.ROUTE_SETTINGS) {
            SettingsRoute(navController)
        }

        composable(
            route = "map?room={room}",
            arguments = listOf(
                navArgument("room") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            BuildingMapRoute(navController)
        }

        composable(DeepLinks.ROUTE_ABOUT) {
            AboutRoute(navController)
        }
    }

    LaunchedEffect(Unit) {
        if (applyStartScreen && startScreen == StartScreen.MAP) {
            navController.navigate(DeepLinks.ROUTE_MAP) { launchSingleTop = true }
        }
    }
}

@Composable
private fun ScheduleRoute(navController: NavHostController) {
    val viewModel: ScheduleViewModel = hiltViewModel()
    val pickerViewModel: PickerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickerState by pickerViewModel.state.collectAsStateWithLifecycle()
    val pickerSaved by pickerViewModel.saved.collectAsStateWithLifecycle()
    val pickerRecent by pickerViewModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ScheduleScreen(
        state = state,
        onSelectDay = viewModel::onSelectDay,
        onPrevDay = viewModel::onPreviousDay,
        onNextDay = viewModel::onNextDay,
        onPrevWeek = viewModel::onPreviousWeek,
        onNextWeek = viewModel::onNextWeek,
        onToday = viewModel::onToday,
        onOpenPicker = viewModel::onOpenPicker,
        onClosePicker = viewModel::onClosePicker,
        onSourceSelected = viewModel::onSourceSelected,
        onSavedSelected = viewModel::onSavedSelected,
        onPickerQueryChange = pickerViewModel::onQueryChange,
        onPickerReset = pickerViewModel::reset,
        pickerState = pickerState,
        pickerSaved = pickerSaved,
        pickerRecent = pickerRecent,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefresh,
        onRangeWarningShown = viewModel::onRangeWarningShown,
        onOpenMap = { room ->
            val route = if (room.isBlank()) {
                DeepLinks.ROUTE_MAP
            } else {
                "map?room=${android.net.Uri.encode(room)}"
            }
            navController.navigate(route) { launchSingleTop = true }
        },
        onOpenPerson = { person ->
            person.sourceKey?.let { key -> viewModel.onExternalSource(key, person.label) }
        },
        onOpenSettings = { navController.navigate(DeepLinks.ROUTE_SETTINGS) },
        onOpenAbout = { navController.navigate(DeepLinks.ROUTE_ABOUT) },
        onShare = {
            val url = state.shareUrl
            if (url != null) {
                shareText(
                    context = context,
                    text = "${state.sourceLabel}\n$url",
                    chooserTitle = context.getString(R.string.share_chooser),
                )
            }
        },
    )
}

@Composable
private fun SettingsRoute(navController: NavHostController) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    SettingsScreen(
        settings = settings,
        permissions = permissions,
        onThemeChange = viewModel::setTheme,
        onStartScreenChange = viewModel::setStartScreen,
        onNotificationsEnabled = viewModel::setNotificationsEnabled,
        onBeforeEnabled = viewModel::setBeforeClassEnabled,
        onBeforeMinutes = viewModel::setBeforeClassMinutes,
        onEveningEnabled = viewModel::setEveningDigestEnabled,
        onEveningHour = viewModel::setEveningDigestHour,
        onMorningEnabled = viewModel::setMorningDigestEnabled,
        onMorningHour = viewModel::setMorningDigestHour,
        onDataSourceChange = viewModel::setDataSourceMode,
        onFallbackChange = viewModel::setOfficialFallbackEnabled,
        onOpenExactAlarmSettings = viewModel::openExactAlarmSettings,
        onOpenNotificationSettings = viewModel::openNotificationSettings,
        onRefreshPermissions = viewModel::refreshPermissions,
        onOpenAbout = { navController.navigate(DeepLinks.ROUTE_ABOUT) },
        onBack = { navController.popBackStack() },
    )
}

@Composable
private fun BuildingMapRoute(navController: NavHostController) {
    val viewModel: BuildingMapViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val roomArgument = navController.currentBackStackEntry?.arguments?.getString("room")

    LaunchedEffect(state.map, roomArgument) {
        val map = state.map ?: return@LaunchedEffect
        val room = roomArgument?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (state.selectedRoomId == null) {
            val target = map.find(room)
            if (target != null) {
                viewModel.selectRoom(target)
            } else {
                viewModel.onQueryChange(room)
            }
        }
    }

    BuildingMapScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onFloor = viewModel::selectFloor,
        onSelectRoom = viewModel::selectRoom,
        onClearRoom = viewModel::clearRoom,
        onQuery = viewModel::onQueryChange,
        onDay = viewModel::selectDay,
        onPair = viewModel::selectPair,
        onFollowNow = viewModel::followNow,
        onRetry = viewModel::retry,
        onOpenSource = { href -> openUrl(context, href) },
    )
}

@Composable
private fun AboutRoute(navController: NavHostController) {
    val context = LocalContext.current
    AboutScreen(
        versionName = BuildConfig.VERSION_NAME,
        onOpenUrl = { url -> openUrl(context, url) },
        onBack = { navController.popBackStack() },
    )
}
