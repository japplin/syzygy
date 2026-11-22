package syzygy

import androidx.compose.runtime.Composable
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CompletableDeferred

/** Controls how long an aligned graph stays open. */
@OptIn(ExperimentalAtomicApi::class)
class AlignmentLifetime internal constructor(close: () -> Unit) {
    private val references = AtomicInt(1)
    private val closeGraph = AtomicReference<(() -> Unit)?>(close)
    internal val shutdown = CompletableDeferred<Unit>()

    /**
     * Keeps the graph open until the returned handle is closed. Acquire the handle before handing
     * off work that can outlive the alignment. The handle can be closed more than once.
     *
     * @throws IllegalStateException if the graph has already started closing.
     */
    fun retain(): AutoCloseable {
        do {
            val count = references.load()
            check(count > 0) { "Cannot use a syzygy graph after it has started closing." }
        } while (!references.compareAndSet(count, count + 1))

        val open = AtomicBoolean(true)
        return AutoCloseable {
            if (open.compareAndSet(true, false)) release()
        }
    }

    /**
     * Waits for teardown. Throws [AlignmentShutdownTimeoutException] if the configured deadline
     * expires. The graph continues to shut down after a timeout or cancellation of the caller.
     * Release any holds owned by the caller before awaiting.
     */
    suspend fun awaitClosed() = shutdown.await()

    internal fun release() {
        if (references.fetchAndAdd(-1) == 1) {
            // Lifetime handles may still be stored in outputs after the graph closes.
            closeGraph.exchange(null)?.invoke()
        }
    }
}

/** Returns the lifetime of the nearest graph after it has been aligned. */
@Composable
fun currentAlignmentLifetime(): AlignmentLifetime =
    checkNotNull(LocalAlignment.current) { "No current syzygy alignment." }
        .requireAlignedGraph()
        .lifetime
