package syzygy.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import syzygy.Model
import syzygy.childSyzygy

/**
 * Home demonstrates model nesting. The clock value is remembered by `childSyzygy`, but its model
 * executes in the same ViewModel-owned Molecule composition as every other model.
 */
@Inject
class HomeModel(
    private val users: UserRepository,
    private val navigator: Navigator,
) : Model<Unit, HomeState> {
    @Composable
    override fun transform(input: Unit): HomeState {
        val name by users.currentUserName.collectAsState()

        val clock = childSyzygy("clock") { scope ->
            Clock(scope)
        }
        val seconds = clock.root.transform(Unit)

        return HomeState(
            greeting = "Hello, $name",
            secondsOnScreen = seconds,
            onOpenProfile = { navigator.goTo(ProfileKey) },
        )
    }
}

private class Clock(
    val coroutineScope: CoroutineScope,
) {
    val root = ClockModel()
}

/** A child model whose effect survives while its aligned unit is retained. */
class ClockModel : Model<Unit, Int> {
    @Composable
    override fun transform(input: Unit): Int {
        var seconds by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                seconds++
            }
        }
        return seconds
    }
}

/**
 * Profile reaches both an app binding and its own extension binding.
 */
@Inject
class ProfileModel(
    private val users: UserRepository,
    private val analytics: ProfileAnalytics,
    private val navigator: Navigator,
) : Model<Unit, ProfileState> {
    @Composable
    override fun transform(input: Unit): ProfileState {
        LaunchedEffect(Unit) { analytics.screenViewed() }
        val name by users.currentUserName.collectAsState()
        return ProfileState(
            displayName = name,
            screenViews = analytics.screenViews,
            onOpenSettings = { navigator.goTo(SettingsKey) },
        )
    }
}

/** Settings — the leaf of the flow. */
@Inject
class SettingsModel : Model<Unit, SettingsState> {
    @Composable
    override fun transform(input: Unit): SettingsState = SettingsState(versionName = "1.0")
}
