package syzygy.sample

import androidx.appstate.transform.transform
import androidx.compose.runtime.State
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import syzygy.projection.ProjectionHost
import syzygy.syzygy

internal fun createAppPresentation(
    projectionHost: ProjectionHost,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
): State<AppUiState?> {
    val graphFactory = createGraphFactory<AppGraph.Factory>()
    return transform(initialValue = null, scope = scope, dispatcher = dispatcher) {
        syzygy {
            val graph = alignGraph { graphFactory.create(projectionHost) }
            graph.presenter.present()
        }
    }
}
