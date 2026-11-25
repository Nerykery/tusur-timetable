package ru.tusur.timetable.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import ru.tusur.timetable.data.prefs.UserPreferences
import ru.tusur.timetable.domain.model.AppSettings
import ru.tusur.timetable.ui.navigation.AppLink
import ru.tusur.timetable.ui.navigation.AppNavHost
import ru.tusur.timetable.ui.navigation.DeepLinkBus
import ru.tusur.timetable.ui.navigation.DeepLinks
import ru.tusur.timetable.ui.theme.TusurTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: UserPreferences

    @Inject
    lateinit var deepLinkBus: DeepLinkBus

    private val links = Channel<AppLink>(Channel.BUFFERED)
    private var initialLink: AppLink? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        initialLink = DeepLinks.parse(intent?.data?.toString())
        initialLink?.let {
            links.trySend(it)
            // Холодный старт: расписание/день должен применить ViewModel, а не навигация.
            deepLinkBus.post(it)
        }

        setContent {
            val settings by prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings.Default)
            TusurTheme(themeMode = settings.themeMode) {
                val navController = rememberNavController()

                androidx.compose.runtime.LaunchedEffect(navController) {
                    for (link in links) {
                        // Расписание и день уже обработаны через DeepLinkBus во ViewModel;
                        // здесь навигация нужна только для схемы корпуса и смены экрана.
                        if (link !is AppLink.Map) continue
                        navController.navigate(DeepLinks.routeFor(link)) {
                            launchSingleTop = true
                        }
                    }
                }

                AppNavHost(
                    navController = navController,
                    startDestination = DeepLinks.ROUTE_SCHEDULE,
                    applyStartScreen = initialLink == null,
                    startScreen = settings.startScreen,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val link = DeepLinks.parse(intent.data?.toString()) ?: return
        // Если экран расписания уже открыт, аргументы навигации не обновятся —
        // передаём ссылку напрямую во ViewModel, иначе открываем новый экран.
        deepLinkBus.post(link)
        links.trySend(link)
    }
}
