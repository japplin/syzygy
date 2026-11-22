package syzygy.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.delay
import syzygy.navigation.NavEntry
import syzygy.navigation.Navigator
import syzygy.syzygy

@Inject
class AppPresenter(
    private val navigator: Navigator<Destination>,
    private val homeGraphFactory: HomeGraph.Factory,
    private val profileGraphFactory: ProfileGraph.Factory,
    private val settingsGraphFactory: SettingsGraph.Factory,
) {
    @Composable
    fun present(): AppUiState {
        val entry = navigator.current
        val screen = syzygy(key = entry) {
            when (entry.route) {
                Destination.Home -> {
                    val home = alignGraph { homeGraphFactory.create() }
                    home.presenter.present(entry)
                }
                Destination.Profile -> {
                    val profile = alignGraph { profileGraphFactory.create() }
                    profile.presenter.present(entry)
                }
                Destination.Settings -> {
                    val settings = alignGraph { settingsGraphFactory.create() }
                    settings.presenter.present(entry)
                }
            }
        }
        return AppUiState(
            entry = entry,
            screen = screen,
            onBack = if (navigator.canPop) ({ navigator.pop(entry) }) else null,
        )
    }
}

@Inject
class HomePresenter(private val users: UserRepository, private val navigator: Navigator<Destination>) {
    @Composable
    fun present(entry: NavEntry<Destination>): HomeState {
        val name by users.currentUserName.collectAsState()
        var seconds by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                seconds++
            }
        }
        return HomeState(
            greeting = "Hello, $name",
            secondsOnScreen = seconds,
            onOpenProfile = { navigator.push(Destination.Profile, from = entry) },
        )
    }
}

@Inject
class ProfilePresenter(
    private val users: UserRepository,
    private val analytics: ProfileAnalytics,
    private val navigator: Navigator<Destination>,
) {
    @Composable
    fun present(entry: NavEntry<Destination>): ProfileState {
        val name by users.currentUserName.collectAsState()
        LaunchedEffect(Unit) { analytics.screenViewed() }
        return ProfileState(
            displayName = name,
            screenViews = analytics.screenViews,
            onOpenSettings = { navigator.push(Destination.Settings, from = entry) },
        )
    }
}

@Inject
class SettingsPresenter(
    private val users: UserRepository,
    private val navigator: Navigator<Destination>,
    private val renameGraphFactory: RenameGraph.Factory,
) {
    @Composable
    fun present(entry: NavEntry<Destination>): SettingsState {
        val name by users.currentUserName.collectAsState()
        var renameOpen by remember { mutableStateOf(false) }
        val rename = if (renameOpen) {
            syzygy {
                val child = alignGraph { renameGraphFactory.create() }
                child.presenter.present(
                    isActive = { navigator.current === entry && renameOpen },
                    onDismiss = { renameOpen = false },
                )
            }
        } else {
            null
        }
        return SettingsState(
            displayName = name,
            onOpenRename = { if (navigator.current === entry) renameOpen = true },
            rename = rename,
        )
    }
}

@Inject
class RenamePresenter(private val users: UserRepository) {
    @Composable
    fun present(isActive: () -> Boolean, onDismiss: () -> Unit): RenameState {
        var name by remember { mutableStateOf(users.currentUserName.value) }
        return RenameState(
            name = name,
            onNameChange = { if (isActive()) name = it },
            onDismiss = { if (isActive()) onDismiss() },
            onSave = {
                if (isActive() && name.isNotBlank()) {
                    users.rename(name.trim())
                    onDismiss()
                }
            },
        )
    }
}
