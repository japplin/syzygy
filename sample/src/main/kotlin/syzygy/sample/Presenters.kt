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
import syzygy.syzygy

@Inject
class AppPresenter(private val navigator: Navigator) {
    @Composable
    fun present(graph: AppGraph): AppUiState {
        val entry = navigator.current
        val screen = syzygy(key = entry.id) {
            when (entry.destination) {
                Destination.Home -> {
                    val home = alignGraph(graph) { graph.homeGraphFactory.create() }
                    home.presenter.present(entry.id)
                }
                Destination.Profile -> {
                    val profile = alignGraph(graph) { graph.profileGraphFactory.create() }
                    profile.presenter.present(entry.id)
                }
                Destination.Settings -> {
                    val settings = alignGraph(graph) { graph.settingsGraphFactory.create() }
                    settings.presenter.present(settings, entry.id)
                }
            }
        }
        return AppUiState(
            entryId = entry.id,
            screen = screen,
            onBack = if (navigator.canGoBack) ({ navigator.back(entry.id) }) else null,
        )
    }
}

@Inject
class HomePresenter(private val users: UserRepository, private val navigator: Navigator) {
    @Composable
    fun present(entryId: Long): HomeState {
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
            onOpenProfile = { navigator.goTo(entryId, Destination.Profile) },
        )
    }
}

@Inject
class ProfilePresenter(
    private val users: UserRepository,
    private val analytics: ProfileAnalytics,
    private val navigator: Navigator,
) {
    @Composable
    fun present(entryId: Long): ProfileState {
        val name by users.currentUserName.collectAsState()
        LaunchedEffect(Unit) { analytics.screenViewed() }
        return ProfileState(
            displayName = name,
            screenViews = analytics.screenViews,
            onOpenSettings = { navigator.goTo(entryId, Destination.Settings) },
        )
    }
}

@Inject
class SettingsPresenter(private val users: UserRepository, private val navigator: Navigator) {
    @Composable
    fun present(graph: SettingsGraph, entryId: Long): SettingsState {
        val name by users.currentUserName.collectAsState()
        var renameOpen by remember { mutableStateOf(false) }
        val rename = if (renameOpen) {
            syzygy {
                val child = alignGraph(graph) { graph.renameGraphFactory.create() }
                child.presenter.present(
                    isActive = { navigator.current.id == entryId && renameOpen },
                    onDismiss = { renameOpen = false },
                )
            }
        } else {
            null
        }
        return SettingsState(
            displayName = name,
            onOpenRename = { if (navigator.current.id == entryId) renameOpen = true },
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
