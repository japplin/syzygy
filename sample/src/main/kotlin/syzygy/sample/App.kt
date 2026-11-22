package syzygy.sample

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import syzygy.navigation.AndroidBackHost
import syzygy.projection.ProjectionHost

class SyzygyApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val projectionHost = ProjectionHost()
    internal lateinit var presentation: State<AppUiState?>
        private set

    override fun onCreate() {
        super.onCreate()
        presentation = createAppPresentation(projectionHost, scope, Dispatchers.Main.immediate)
    }

    override fun onTerminate() {
        scope.cancel()
        super.onTerminate()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SyzygyApplication
        val presentation = app.presentation
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    AndroidBackHost {
                        val state by presentation
                        state?.let { app.projectionHost.Project(it) }
                    }
                }
            }
        }
    }
}
