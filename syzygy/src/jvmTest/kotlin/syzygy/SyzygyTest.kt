package syzygy

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
                val root = alignGraph { Any().also(created::add) }
                syzygy {
                    alignGraph(root) { Any().also(created::add) }
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
    fun replacingAParentKeyDisposesChildAlignmentBeforeClosingTheParent() = transformTest {
        var rootKey by mutableStateOf("first")
        val events = mutableListOf<String>()
        val state = transform(initialValue = "", scope = backgroundScope, dispatcher = testDispatcher) {
            val key = rootKey
            syzygy(key) {
                val parent = alignGraph { CloseableGraph(key, events) }
                syzygy {
                    alignGraph(parent) { CloseableGraph("$key child", events) }
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
            override val alignmentStrategy = AlignmentStrategy { parent ->
                syzygy {
                    assertSame(child, alignGraph(parent) { child })
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
    fun requiresTheDirectParentByIdentity() {
        val root = "root"
        val wrongParent = assertThrows(IllegalStateException::class.java) {
            compose {
                syzygy {
                    val parent = alignGraph { root }
                    val sibling = syzygy {
                        alignGraph(parent) { "first sibling" }
                    }
                    syzygy {
                        alignGraph(sibling) { error("factory should not run") }
                    }
                }
            }
        }

        assertTrue(wrongParent.message.orEmpty().contains("immediately enclosing"))
        val ancestor = assertThrows(IllegalStateException::class.java) {
            compose {
                syzygy {
                    val parent = alignGraph { root }
                    syzygy {
                        alignGraph(parent) { "middle" }
                        syzygy {
                            alignGraph(parent) { error("factory should not run") }
                        }
                    }
                }
            }
        }
        assertEquals(wrongParent.message, ancestor.message)

        data class Graph(val name: String)
        val equalParent = assertThrows(IllegalStateException::class.java) {
            compose {
                syzygy {
                    val parent = alignGraph { Graph("root") }
                    syzygy {
                        alignGraph(parent.copy()) { error("factory should not run") }
                    }
                }
            }
        }
        assertEquals(wrongParent.message, equalParent.message)
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

private class NoOpApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = Unit
    override fun insertBottomUp(index: Int, instance: Unit) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun remove(index: Int, count: Int) = Unit
    override fun onClear() = Unit
}
