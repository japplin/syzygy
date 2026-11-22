package syzygy

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphShutdownTest {
    @Test
    fun holdTimeoutStartsAtRetirementAndLateReleaseStillClosesChildThenParent() = transformTest {
        val closed = mutableListOf<String>()
        var visible by mutableStateOf(true)
        val state = transform<Pair<AlignmentLifetime, AlignmentLifetime>?>(
            initialValue = null,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            if (!visible) null
            else syzygy {
                alignGraph { AutoCloseable { closed += "parent" } }
                val parent = currentAlignmentLifetime()
                val child = syzygy(key = "detail") {
                    alignGraph(shutdownTimeout = 1.seconds) {
                        AutoCloseable { closed += "child" }
                    }
                    currentAlignmentLifetime()
                }
                parent to child
            }
        }

        advance()
        val (parent, lifetime) = checkNotNull(state.value)
        val hold = lifetime.retain()
        val parentShutdown = backgroundScope.async { parent.awaitClosed() }
        val shutdown = backgroundScope.async { runCatching { lifetime.awaitClosed() } }
        advanceTimeBy(2.seconds)
        advance()
        assertFalse(shutdown.isCompleted)

        visible = false
        advance()
        advanceTimeBy(1.seconds)
        advance()
        assertTrue(shutdown.isCompleted)
        val failure = shutdown.await().exceptionOrNull() as AlignmentShutdownTimeoutException
        assertEquals(AlignmentShutdownTimeoutException.Phase.WaitingForHolds, failure.phase)
        assertTrue(failure.message.orEmpty().contains("key=detail"))
        assertTrue(closed.isEmpty())
        assertFalse(parentShutdown.isCompleted)

        hold.close()
        hold.close()
        advance()
        assertEquals(listOf("child", "parent"), closed)
        assertTrue(parentShutdown.isCompleted)
        parentShutdown.await()
    }

    @Test
    fun suspendingCloseGetsItsOwnDeadlineAndContinuesAfterTimingOut() = transformTest {
        val finishClose = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var visible by mutableStateOf(true)
        val state = transform<AlignmentLifetime?>(null, scope = backgroundScope, dispatcher = testDispatcher) {
            if (visible) {
                syzygy {
                    alignGraph { AutoCloseable { events += "close parent" } }
                    syzygy {
                        alignGraph(
                            shutdownTimeout = 1.seconds,
                            onClose = {
                                events += "start child close"
                                finishClose.await()
                                events += "finish child close"
                            },
                        ) { Any() }
                        currentAlignmentLifetime()
                    }
                }
            } else null
        }

        advance()
        val lifetime = checkNotNull(state.value)
        val hold = lifetime.retain()
        val shutdown = backgroundScope.async { runCatching { lifetime.awaitClosed() } }
        visible = false
        advance()
        advanceTimeBy(750.milliseconds)
        advance()
        hold.close()
        advance()
        assertEquals(listOf("start child close"), events)

        advanceTimeBy(300.milliseconds)
        advance()
        assertFalse(shutdown.isCompleted)
        advanceTimeBy(700.milliseconds)
        advance()
        assertTrue(shutdown.isCompleted)
        val failure = shutdown.await().exceptionOrNull() as AlignmentShutdownTimeoutException
        assertEquals(AlignmentShutdownTimeoutException.Phase.Closing, failure.phase)
        assertEquals(listOf("start child close"), events)

        finishClose.complete(Unit)
        advance()
        assertEquals(listOf("start child close", "finish child close", "close parent"), events)
    }
}
