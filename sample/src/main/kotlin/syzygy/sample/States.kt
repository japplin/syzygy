package syzygy.sample

import syzygy.navigation.NavEntry

data class AppUiState(
    val entry: NavEntry<Destination>,
    val screen: Any,
    val onBack: (() -> Unit)?,
)

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
    val displayName: String,
    val onOpenRename: () -> Unit,
    val rename: RenameState?,
)

data class RenameState(
    val name: String,
    val onNameChange: (String) -> Unit,
    val onDismiss: () -> Unit,
    val onSave: () -> Unit,
)
