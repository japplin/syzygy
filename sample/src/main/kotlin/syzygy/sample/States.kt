package syzygy.sample

data class AppUiState(
    val entryId: Long,
    val screen: ScreenState,
    val onBack: (() -> Unit)?,
)

sealed interface ScreenState

data class HomeState(
    val greeting: String,
    val secondsOnScreen: Int,
    val onOpenProfile: () -> Unit,
) : ScreenState

data class ProfileState(
    val displayName: String,
    val screenViews: Int,
    val onOpenSettings: () -> Unit,
) : ScreenState

data class SettingsState(
    val displayName: String,
    val onOpenRename: () -> Unit,
    val rename: RenameState?,
) : ScreenState

data class RenameState(
    val name: String,
    val onNameChange: (String) -> Unit,
    val onDismiss: () -> Unit,
    val onSave: () -> Unit,
)
