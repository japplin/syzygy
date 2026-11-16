package syzygy.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import syzygy.Project
import syzygy.Projection

/**
 * Each graph contributes the matching projection registry entry. Projections remain pure functions of
 * output — every event and value they need was computed by the model and passed through state.
 */

class HomeProjection : Projection<HomeState> {
    @Composable
    override fun project(output: HomeState) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(output.greeting, style = MaterialTheme.typography.headlineMedium)
            // The nested clock shares the root Molecule composition.
            Text("On this screen for ${output.secondsOnScreen}s")
            Button(onClick = output.onOpenProfile) { Text("Open Profile") }
        }
    }
}

class ProfileProjection : Projection<ProfileState> {
    @Composable
    override fun project(output: ProfileState) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Profile of ${output.displayName}", style = MaterialTheme.typography.headlineMedium)
            Text("Viewed ${output.screenViews} time(s) this session")
            Button(onClick = output.onOpenSettings) { Text("Open Settings") }
        }
    }
}

class SettingsProjection : Projection<SettingsState> {
    @Composable
    override fun project(output: SettingsState) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Version ${output.versionName}")
        }
    }
}

class AppProjection : Projection<AppState> {
    @Composable
    override fun project(output: AppState) {
        when (val screen = output.screen) {
            is AppScreen.Home -> Project(screen.state)
            is AppScreen.Profile -> Project(screen.state)
            is AppScreen.Settings -> Project(screen.state)
        }
    }
}
