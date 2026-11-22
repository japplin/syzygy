package syzygy

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.LocalRetainedValuesStoreProvider
import androidx.compose.runtime.retain.ManagedRetainedValuesStore
import androidx.compose.runtime.setValue
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyzygyTest {
    @Test
    fun reusesGraphsAcrossRecompositionAndReturnsTheLatestValue() = transformTest {
        val created = mutableListOf<Any>()
        var count by mutableStateOf(0)
        val state = transform(initialValue = "", scope = backgroundScope, dispatcher = testDispatcher) {
            syzygy {
                alignGraph { Any().also(created::add) }
                syzygy {
                    alignGraph { Any().also(created::add) }
                    "count = $count"
                }
            }
        }

        advance()
        assertEquals("count = 0", state.value)
        val first = created.toList()
        assertEquals(2, first.size)

        count++
        advance()
        assertEquals("count = 1", state.value)
        assertEquals(first, created)
    }

    @Test
    fun retainsRootAndChildAcrossCompositionRemoval() = transformTest {
        val store = ManagedRetainedValuesStore()
        val events = mutableListOf<String>()
        val presented = mutableListOf<Pair<CloseableGraph, CloseableGraph>>()
        var show by mutableStateOf(true)

        try {
            transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
                if (show) {
                    LocalRetainedValuesStoreProvider(store) {
                        syzygy {
                            val root = alignGraph { CloseableGraph("root", events) }
                            syzygy {
                                val child = alignGraph { CloseableGraph("child", events) }
                                SideEffect { presented += root to child }
                            }
                        }
                    }
                }
            }

            advance()
            val (root, child) = presented.single()

            show = false
            advance()
            assertEquals(listOf("align root", "align child", "leave child", "leave root"), events)

            show = true
            advance()
            assertEquals(2, presented.size)
            assertSame(root, presented.last().first)
            assertSame(child, presented.last().second)

            show = false
            advance()
            store.dispose()
            assertEquals(listOf("close child", "close root"), events.filter { it.startsWith("close") })
        } finally {
            store.dispose()
        }
    }

    @Test
    fun rememberAlignedReusesAValueUntilTheGraphAlignmentChanges() = transformTest {
        data class Graph(val name: String)
        val first = Graph("graph")
        val second = first.copy()
        val calculations = mutableListOf<Any>()
        var step by mutableStateOf(0)
        val state = transform<Pair<Int, Int>?>(
            initialValue = null,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            syzygy {
                if (step < 2) alignGraph { first } else alignGraph { second }
                val value = rememberAligned { graph ->
                    calculations += graph
                    calculations.size
                }
                step to value
            }
        }

        advance()
        assertEquals(0 to 1, state.value)
        assertSame(first, calculations.single())

        step = 1
        advance()
        assertEquals(1 to 1, state.value)

        step = 2
        advance()
        assertEquals(2 to 2, state.value)
        assertSame(second, calculations.last())
    }

    @Test
    fun replacingAParentKeyDisposesChildAlignmentBeforeClosingTheParent() = transformTest {
        var rootKey by mutableStateOf("first")
        val events = mutableListOf<String>()
        val state = transform(initialValue = "", scope = backgroundScope, dispatcher = testDispatcher) {
            val key = rootKey
            syzygy(key) {
                alignGraph { CloseableGraph(key, events) }
                syzygy {
                    alignGraph { CloseableGraph("$key child", events) }
                    key
                }
            }
        }

        advance()
        assertEquals("first", state.value)
        assertEquals(listOf("align first", "align first child"), events)

        rootKey = "second"
        advance()
        assertEquals("second", state.value)
        assertEquals(
            listOf(
                "align first",
                "align first child",
                "leave first child",
                "close first child",
                "leave first",
                "close first",
                "align second",
                "align second child",
            ),
            events,
        )
    }

    @Test
    fun strategyCanEnterAChildBeforeAlignGraphReturns() {
        val child = Any()
        val root = object : HasAlignmentStrategy {
            override val alignmentStrategy = AlignmentStrategy {
                syzygy {
                    assertSame(child, alignGraph { child })
                }
            }
        }

        compose {
            val output: String? = syzygy {
                assertSame(root, alignGraph { root })
                null
            }
            assertNull(output)
        }
    }

    @Test
    fun ancestorStrategiesRunBeforeTheGraphsOwnStrategyAndStayInTheirBranch() {
        val events = mutableListOf<String>()
        val root = object : HasChildAlignmentStrategy {
            override val childAlignmentStrategy = AlignmentStrategy { events += "root" }
        }
        val branch = object : HasChildAlignmentStrategy {
            override val childAlignmentStrategy = AlignmentStrategy { events += "branch" }
        }
        val leaf = object : HasAlignmentStrategy {
            override val alignmentStrategy = AlignmentStrategy { events += "leaf" }
        }

        compose {
            syzygy {
                alignGraph { root }
                syzygy {
                    alignGraph { branch }
                    syzygy { assertSame(leaf, alignGraph { leaf }) }
                }
                syzygy { assertSame(leaf, alignGraph { leaf }) }
            }
        }

        assertEquals(listOf("root", "root", "branch", "leaf", "root", "leaf"), events)
    }

    @Test
    fun closesStrategiesOnceWhenTheirOwningGraphRetires() = transformTest {
        val closed = mutableListOf<String>()
        var rootKey by mutableStateOf(0)
        var showChild by mutableStateOf(true)
        transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
            val index = rootKey
            syzygy(index) {
                alignGraph { StrategyOwner("root $index", closed) }
                if (showChild) {
                    syzygy {
                        val child = alignGraph { StrategyOwner("child", closed) }
                        assertTrue(child.name == "child")
                    }
                }
            }
        }

        advance()
        assertTrue(closed.isEmpty())

        showChild = false
        advance()
        assertEquals(listOf("strategy child", "graph child"), closed)

        showChild = true
        advance()
        rootKey++
        advance()
        assertEquals(
            listOf("strategy child", "graph child", "strategy child", "graph child", "strategy root 0", "graph root 0"),
            closed,
        )
    }

    @Test
    fun requiresAlignmentBeforeReturningAndBeforeEnteringAChild() {
        val missing = assertThrows(IllegalStateException::class.java) {
            compose { assertEquals(42, syzygy { 42 }) }
        }
        val earlyChild = assertThrows(IllegalStateException::class.java) {
            compose {
                syzygy {
                    syzygy { error("child block should not start") }
                    alignGraph { Any() }
                }
            }
        }

        assertTrue(missing.message.orEmpty().contains("Call alignGraph"))
        assertEquals(missing.message, earlyChild.message)
    }

    @Test
    fun requiresAlignmentOnEveryEvaluation() {
        val earlyChild = assertThrows(IllegalStateException::class.java) {
            transformTest {
                var enterChildEarly by mutableStateOf(false)
                val state = transform(initialValue = 0, scope = backgroundScope, dispatcher = testDispatcher) {
                    syzygy {
                        if (enterChildEarly) {
                            syzygy { error("child block should not start") }
                        }
                        alignGraph { Any() }
                        42
                    }
                }

                advance()
                assertEquals(42, state.value)
                enterChildEarly = true
                advance()
            }
        }

        assertTrue(earlyChild.message.orEmpty().contains("Call alignGraph"))
    }

    @Test
    fun abandonsAJustCreatedGraphWhenAlignmentFails() {
        val events = mutableListOf<String>()
        val duplicate = assertThrows(IllegalStateException::class.java) {
            compose {
                syzygy {
                    alignGraph { CloseableGraph("first", events) }
                    alignGraph { error("second factory should not run") }
                }
            }
        }

        assertTrue(duplicate.message.orEmpty().contains("only once"))
        assertEquals(listOf("close first"), events)
    }

    private fun compose(content: @Composable () -> Unit) {
        val recomposer = Recomposer(EmptyCoroutineContext)
        val composition = Composition(NoOpApplier(), recomposer)
        try {
            composition.setContent(content)
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }
}

private class CloseableGraph(
    private val name: String,
    private val events: MutableList<String>,
) : AutoCloseable, HasAlignmentStrategy {
    override val alignmentStrategy = AlignmentStrategy {
        DisposableEffect(Unit) {
            events.add("align $name")
            onDispose { events.add("leave $name") }
        }
    }

    override fun close() {
        events.add("close $name")
    }
}

private class StrategyOwner(
    val name: String,
    private val closed: MutableList<String>,
) : HasAlignmentStrategy, HasChildAlignmentStrategy, AutoCloseable {
    override val alignmentStrategy = object : AlignmentStrategy {
        @Composable
        override fun align(graph: Any) = Unit

        override fun close() {
            closed += "strategy $name"
        }
    }

    override val childAlignmentStrategy = alignmentStrategy

    override fun close() {
        closed += "graph $name"
    }
}

private class NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun onClear() = Unit
}
