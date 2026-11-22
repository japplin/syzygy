package syzygy.sample

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import syzygy.navigation.BackOverlay
import syzygy.navigation.BackScope
import syzygy.projection.LocalProjectionActive
import syzygy.projection.Project
import syzygy.projection.Projection
import syzygy.projection.ProjectionTransition
import syzygy.projection.projectionTransition

@ContributesIntoSet(AppScope::class)
@Inject
class AppScreenTransition : ProjectionTransition<AppUiState> by projectionTransition<AppUiState>(
    transform = { old, new ->
        if (old.entry !== new.entry) fadeIn() togetherWith fadeOut() else null
    },
)

@ContributesIntoMap(AppScope::class)
@ProjectionKey(AppUiState::class)
class AppProjection : Projection<AppUiState> {
    @Composable
    override fun project(output: AppUiState) {
        BackScope(onBackPress = output.onBack, active = LocalProjectionActive.current) {
            Project(output.screen)
        }
    }
}

@ContributesIntoMap(HomeScope::class)
@ProjectionKey(HomeState::class)
class HomeProjection : Projection<HomeState> {
    @Composable
    override fun project(output: HomeState) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(output.greeting, style = MaterialTheme.typography.headlineMedium)
            Text("On this screen for ${output.secondsOnScreen}s")
            Button(onClick = output.onOpenProfile, enabled = LocalProjectionActive.current) {
                Text("Open Profile")
            }
        }
    }
}

@ContributesIntoMap(ProfileScope::class)
@ProjectionKey(ProfileState::class)
class ProfileProjection : Projection<ProfileState> {
    @Composable
    override fun project(output: ProfileState) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Profile of ${output.displayName}", style = MaterialTheme.typography.headlineMedium)
            Text("Viewed ${output.screenViews} time(s) in this visit")
            Button(onClick = output.onOpenSettings, enabled = LocalProjectionActive.current) {
                Text("Open Settings")
            }
        }
    }
}

@ContributesIntoMap(SettingsScope::class)
@ProjectionKey(SettingsState::class)
class SettingsProjection : Projection<SettingsState> {
    @Composable
    override fun project(output: SettingsState) {
        val active = LocalProjectionActive.current
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text(output.displayName)
            Button(onClick = output.onOpenRename, enabled = active) { Text("Rename") }
        }
        if (active) output.rename?.let { rename ->
            BackOverlay(order = 0, onBackPress = rename.onDismiss) {
                Project(rename)
            }
        }
    }
}

@ContributesIntoMap(RenameScope::class)
@ProjectionKey(RenameState::class)
class RenameProjection : Projection<RenameState> {
    @Composable
    override fun project(output: RenameState) {
        AlertDialog(
            onDismissRequest = output.onDismiss,
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = output.name,
                    onValueChange = output.onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = output.onSave, enabled = output.name.isNotBlank()) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = output.onDismiss) { Text("Cancel") }
            },
        )
    }
}
