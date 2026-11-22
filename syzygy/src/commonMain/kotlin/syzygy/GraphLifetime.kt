package syzygy

import androidx.compose.runtime.retain.RetainObserver
import dev.zacsweers.metro.ExperimentalMetroApi
import dev.zacsweers.metro.ExperimentalMetroCoroutinesApi
import dev.zacsweers.metro.SuspendCloseableGraph
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@PublishedApi
@OptIn(ExperimentalMetroApi::class, ExperimentalMetroCoroutinesApi::class)
internal class GraphLifetime(
    val graph: Any,
    parent: GraphLifetime?,
    private val cleanupContext: CoroutineContext,
    closeGraph: (suspend () -> Unit)?,
    private val key: Any?,
    private val shutdownTimeout: Duration,
) : RetainObserver {
    val lifetime = AlignmentLifetime(::close)

    private val parentReference = parent?.lifetime?.retain()
    private val suspendClose = closeGraph ?: (graph as? SuspendCloseableGraph)?.let { it::close }
    private val ownStrategy = (graph as? HasAlignmentStrategy)?.let { lazy { it.alignmentStrategy } }
    private val childStrategy = (graph as? HasChildAlignmentStrategy)?.let { lazy { it.childAlignmentStrategy } }
    private val closing = if (shutdownTimeout.isFinite()) CompletableDeferred<Unit>() else null

    val alignmentStrategy: AlignmentStrategy? get() = ownStrategy?.value
    val childAlignmentStrategy: AlignmentStrategy? get() = childStrategy?.value

    override fun onRetained() = Unit

    override fun onEnteredComposition() = Unit

    override fun onExitedComposition() = Unit

    override fun onRetired() = retire()

    override fun onUnused() = retire()

    private fun retire() {
        closing?.let { closeStarted ->
            CoroutineScope(cleanupContext).launch(start = CoroutineStart.UNDISPATCHED) {
                if (withTimeoutOrNull(shutdownTimeout) { closeStarted.await() } == null) {
                    reportTimeout(AlignmentShutdownTimeoutException.Phase.WaitingForHolds)
                } else if (withTimeoutOrNull(shutdownTimeout) { lifetime.shutdown.join() } == null) {
                    reportTimeout(AlignmentShutdownTimeoutException.Phase.Closing)
                }
            }
        }
        lifetime.release()
    }

    private fun reportTimeout(phase: AlignmentShutdownTimeoutException.Phase) {
        val graphName = graph::class.simpleName ?: "graph"
        lifetime.shutdown.completeExceptionally(
            AlignmentShutdownTimeoutException(phase, shutdownTimeout, graphName, key),
        )
    }

    private fun close() {
        closing?.complete(Unit)
        val closeGraph = suspendClose
        if (closeGraph != null) {
            // The presenter job may have been cancelled when Compose retired this alignment.
            CoroutineScope(cleanupContext).launch {
                completeClose {
                    closeSuspending(closeGraph)
                }
            }
        } else {
            completeClose {
                (graph as? AutoCloseable).use { closeStrategies() }
            }
        }
    }

    private inline fun completeClose(closeGraph: () -> Unit) {
        parentReference.use {
            try {
                closeGraph()
                lifetime.shutdown.complete(Unit)
            } catch (failure: Throwable) {
                lifetime.shutdown.completeExceptionally(failure)
                throw failure
            }
        }
    }

    private suspend fun closeSuspending(closeGraph: suspend () -> Unit) {
        val strategyFailure = runCatching { closeStrategies() }.exceptionOrNull()
        try {
            closeGraph()
        } catch (failure: Throwable) {
            if (strategyFailure == null) throw failure
            if (failure !== strategyFailure) strategyFailure.addSuppressed(failure)
        }
        strategyFailure?.let { throw it }
    }

    private fun closeStrategies() {
        val own = ownStrategy?.takeIf { it.isInitialized() }?.value
        val child = childStrategy?.takeIf { it.isInitialized() }?.value
        own?.takeUnless { it === graph }.use {
            if (child !== own && child !== graph) child?.close()
        }
    }
}
