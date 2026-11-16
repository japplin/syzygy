package syzygy.sample

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
import syzygy.projection.Project
import syzygy.projection.Projection

object HomeProjection : Projection<HomeState> {
    @Composable
    override fun project(output: HomeState) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(output.greeting, style = MaterialTheme.typography.headlineMedium)
            Text("On this screen for ${output.secondsOnScreen}s")
            Button(onClick = output.onOpenProfile, enabled = LocalScreenActive.current) {
                Text("Open Profile")
            }
        }
    }
}

object ProfileProjection : Projection<ProfileState> {
    @Composable
    override fun project(output: ProfileState) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Profile of ${output.displayName}", style = MaterialTheme.typography.headlineMedium)
            Text("Viewed ${output.screenViews} time(s) in this visit")
            Button(onClick = output.onOpenSettings, enabled = LocalScreenActive.current) {
                Text("Open Settings")
            }
        }
    }
}

object SettingsProjection : Projection<SettingsState> {
    @Composable
    override fun project(output: SettingsState) {
        val active = LocalScreenActive.current
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text(output.displayName)
            Button(onClick = output.onOpenRename, enabled = active) { Text("Rename") }
        }
        if (active) output.rename?.let { Project(it) }
    }
}

object RenameProjection : Projection<RenameState> {
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
