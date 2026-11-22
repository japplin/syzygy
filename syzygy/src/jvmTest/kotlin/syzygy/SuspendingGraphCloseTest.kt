package syzygy

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ExperimentalMetroApi
import dev.zacsweers.metro.ExperimentalMetroCoroutinesApi
import dev.zacsweers.metro.SuspendCloseableGraph
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalMetroApi::class, ExperimentalMetroCoroutinesApi::class)
class SuspendingGraphCloseTest {
    @Test
    fun anExtensionCanDelayCustomSuspendingClose() = transformTest {
        val finishExit = CompletableDeferred<Unit>()
        val finishClose = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var visible by mutableStateOf(true)
        val graph = object : HasAlignmentStrategy, AutoCloseable {
            override val alignmentStrategy = AlignmentStrategy {
                val lifetime = currentAlignmentLifetime()
                DisposableEffect(lifetime) {
                    val hold = lifetime.retain()
                    onDispose {
                        backgroundScope.launch {
                            hold.use {
                                events += "start extension exit"
                                finishExit.await()
                                events += "finish extension exit"
                            }
                        }
                    }
                }
            }

            override fun close() {
                events += "default close"
            }
        }

        val state = transform(initialValue = false, scope = backgroundScope, dispatcher = testDispatcher) {
            if (visible) {
                syzygy {
                    alignGraph(
                        onClose = { closed ->
                            assertSame(graph, closed)
                            events += "start custom close"
                            finishClose.await()
                            events += "finish custom close"
                        },
                        create = { graph },
                    )
                    true
                }
            } else {
                false
            }
        }

        advance()
        assertEquals(true, state.value)
        visible = false
        advance()
        assertEquals(false, state.value)
        assertEquals(listOf("start extension exit"), events)

        finishExit.complete(Unit)
        advance()
        assertEquals(listOf("start extension exit", "finish extension exit", "start custom close"), events)

        finishClose.complete(Unit)
        advance()
        assertEquals(
            listOf("start extension exit", "finish extension exit", "start custom close", "finish custom close"),
            events,
        )
    }

    @Test
    fun childCloseOutlivesPresenterCancellationAndFinishesBeforeParentCloses() = transformTest {
        val finishChildClose = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val presenter = Job(backgroundScope.coroutineContext[Job])
        val presenterScope = CoroutineScope(backgroundScope.coroutineContext + presenter)

        val state = transform<SuspendCloseableGraph?>(
            initialValue = null,
            scope = presenterScope,
            dispatcher = testDispatcher,
        ) {
            syzygy {
                alignGraph { AutoCloseable { events += "close parent" } }
                syzygy {
                    alignGraph {
                        object : SuspendCloseableGraph {
                            override suspend fun close() {
                                events += "start child close"
                                finishChildClose.await()
                                events += "finish child close"
                            }
                        }
                    }
                }
            }
        }

        advance()
        assertNotNull(state.value)
        presenter.cancel()
        advance()
        assertEquals(listOf("start child close"), events)

        finishChildClose.complete(Unit)
        advance()
        assertEquals(listOf("start child close", "finish child close", "close parent"), events)
    }
}
