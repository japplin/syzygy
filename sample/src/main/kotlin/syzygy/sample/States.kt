package syzygy.sample

/**
 * Screen outputs. Note events are plain lambdas carried *in* the state (they close over scoped
 * dependencies computed by the model), which is what keeps projections pure and injection-free.
 */
data class HomeState(
    val greeting: String,
    val secondsOnScreen: Int,
    val onOpenProfile: () -> Unit,
)

data class ProfileState(
    val displayName: String,
    val screenViews: Int,
    val onOpenSettings: () -> Unit,
)

data class SettingsState(
    val versionName: String,
)

data class AppState(
    val screen: AppScreen,
)

sealed interface AppScreen {
    data class Home(val state: HomeState) : AppScreen
    data class Profile(val state: ProfileState) : AppScreen
    data class Settings(val state: SettingsState) : AppScreen
}
