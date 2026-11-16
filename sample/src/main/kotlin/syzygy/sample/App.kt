package syzygy.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.createGraphFactory
import syzygy.Project
import syzygy.ProjectionAlignment
import syzygy.SatelliteAlignment
import syzygy.LocalProjectionRegistry
import syzygy.alignmentStrategy
import syzygy.childSyzygy
import syzygy.rootSyzygy

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val strategy = alignmentStrategy(
                        ProjectionAlignment,
                        SatelliteAlignment,
                    )
                    val root = rootSyzygy(
                        input = Unit,
                        strategy = strategy,
                    ) {
                        val app = childSyzygy("app") { scope ->
                            createGraphFactory<AppGraph.Factory>().create(scope)
                        }

                        when (app.navigator.backStack.last()) {
                            HomeKey -> {
                                val home = childSyzygy(HomeKey) { scope ->
                                    app.homeGraphFactory.create(scope)
                                }
                                AppState(AppScreen.Home(home.root.transform(Unit)))
                            }

                            ProfileKey -> {
                                val profile = childSyzygy(ProfileKey) { scope ->
                                    app.profileGraphFactory.create(scope)
                                }
                                AppState(AppScreen.Profile(profile.root.transform(Unit)))
                            }

                            SettingsKey -> {
                                val settings = childSyzygy(SettingsKey) { scope ->
                                    app.settingsGraphFactory.create(scope)
                                }
                                AppState(AppScreen.Settings(settings.root.transform(Unit)))
                            }
                        }
                    }

                    CompositionLocalProvider(LocalProjectionRegistry provides root.projectionRegistry) {
                        val state by root
                        Project(state)
                    }
                }
            }
        }
    }
}
