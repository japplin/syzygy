package syzygy.sample

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appstate.transform.listener
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import syzygy.projection.LocalProjectionRegistry
import syzygy.projection.Project
import syzygy.projection.ProjectionHost
import syzygy.syzygy

class SyzygyApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val projections = ProjectionHost<AppUiState>()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            listener(dispatcher = Dispatchers.Main.immediate) {
                projections.Content {
                    syzygy {
                        val graph = alignGraph { createGraph<AppGraph>() }
                        graph.presenter.present(graph)
                    }
                }
            }
        }
    }

    override fun onTerminate() {
        scope.cancel()
        super.onTerminate()
    }
}

internal val LocalScreenActive = compositionLocalOf { true }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val host = (application as SyzygyApplication).projections
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    val presentation by host.state
                    presentation?.let { current ->
                        val onBack = current.output.onBack
                        BackHandler(enabled = onBack != null) { onBack?.invoke() }
                        AnimatedContent(
                            targetState = current,
                            contentKey = { it.output.entryId },
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "Screen transition",
                        ) { shown ->
                            CompositionLocalProvider(
                                LocalProjectionRegistry provides shown.projections,
                                LocalScreenActive provides (shown.output.entryId == current.output.entryId),
                            ) {
                                Project(shown.output.screen)
                            }
                        }
                    }
                }
            }
        }
    }
}
