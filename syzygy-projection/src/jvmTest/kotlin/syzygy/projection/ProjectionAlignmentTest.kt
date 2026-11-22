package syzygy.projection

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import syzygy.AlignmentStrategy
import syzygy.HasAlignmentStrategy
import syzygy.HasChildAlignmentStrategy
import syzygy.syzygy

class ProjectionAlignmentTest {
    @Test
    fun siblingGraphsRenderTheSameTypeUsingTheirOwnCompleteRegistries() = transformTest {
        val rendered = mutableListOf<String>()
        val rowProjection = Row::class to RowProjection(rendered)
        val screen = Screen(Row("shared"))
        val presentation = transform<Pair<Projected<Screen>, Projected<Screen>>?>(
            initialValue = null,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            syzygy {
                alignGraph { ProjectionRoot() }
                val left = syzygy {
                    alignGraph {
                        Graph(rowProjection, Screen::class to ScreenProjection("left", rendered))
                    }
                    projected(screen)
                }
                val right = syzygy {
                    alignGraph {
                        Graph(rowProjection, Screen::class to ScreenProjection("right", rendered))
                    }
                    projected(screen)
                }
                left to right
            }
        }

        advance()
        val (left, right) = checkNotNull(presentation.value)
        transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
            Project(left)
            Project(right)
        }

        advance()
        assertEquals(listOf("left", "row shared", "right", "row shared"), rendered)
    }

    @Test
    fun anOutgoingRenderingKeepsItsGraphOpenUntilTheRenderingLeavesComposition() = transformTest {
        val host = ProjectionHost()
        val rendered = mutableListOf<String>()
        val closed = mutableListOf<Int>()
        val appProjection = AppOutput::class to object : Projection<AppOutput> {
            @Composable
            override fun project(output: AppOutput) {
                SideEffect { rendered += "app" }
                Project(output.screen)
            }
        }
        var route by mutableStateOf(0)
        var showOutgoing by mutableStateOf(true)
        var showIncoming by mutableStateOf(false)
        val state = transform<AppOutput?>(
            initialValue = null,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            syzygy {
                alignGraph {
                    object : ProjectionRegistry, HasAlignmentStrategy, HasChildAlignmentStrategy {
                        override val projections: Map<KClass<*>, Projection<*>> = mapOf(appProjection)
                        override val alignmentStrategy = host
                        override val childAlignmentStrategy = host
                    }
                }
                val index = route
                val screen = syzygy(key = index) {
                    val screenProjection = Screen::class to ScreenProjection("route $index", rendered)
                    alignGraph {
                        Graph(appProjection, screenProjection) { closed += index }
                    }
                    val row = syzygy {
                        alignGraph { Graph(appProjection, screenProjection, Row::class to RowProjection(rendered)) }
                        Row("from $index")
                    }
                    Screen(row)
                }
                AppOutput(screen)
            }
        }

        advance()
        val outgoing = checkNotNull(state.value)

        transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
            if (showOutgoing) host.Project(outgoing)
            if (showIncoming) host.Project(checkNotNull(state.value))
        }

        advance()
        route = 1
        showIncoming = true
        advance()
        assertEquals(emptyList<Int>(), closed)
        assertTrue("route 0" in rendered)
        assertTrue("row from 0" in rendered)
        assertTrue("route 1" in rendered)
        assertTrue("row from 1" in rendered)

        showOutgoing = false
        advance()
        assertEquals(listOf(0), closed)
    }

    @Test
    fun hostDistinguishesEqualOutputsAndKeepsTheProducingAlignment() = transformTest {
        val host = ProjectionHost()
        val rendered = mutableListOf<String>()
        val rowProjection = Row::class to RowProjection(rendered)
        val presentation = transform<Pair<Screen, Screen>?>(
            initialValue = null,
            scope = backgroundScope,
            dispatcher = testDispatcher,
        ) {
            syzygy {
                alignGraph { ProjectionRoot(host) }
                val left = syzygy {
                    alignGraph {
                        Graph(
                            Screen::class to ScreenProjection("outer", rendered),
                            rowProjection,
                        )
                    }
                    syzygy {
                        alignGraph {
                            Graph(rowProjection, Screen::class to ScreenProjection("left", rendered))
                        }
                        Screen(Row("shared"))
                    }
                }
                val right = syzygy {
                    alignGraph {
                        Graph(
                            Screen::class to ScreenProjection("right", rendered),
                            rowProjection,
                        )
                    }
                    Screen(Row("shared"))
                }
                left to right
            }
        }

        advance()
        val (left, right) = checkNotNull(presentation.value)
        assertEquals(left, right)
        assertNotSame(left, right)
        transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
            host.Project(left)
            host.Project(right)
        }

        advance()
        assertEquals(listOf("left", "row shared", "right", "row shared"), rendered)
    }

    @Test
    fun rendersInTheAlignmentAndReusesItsRegistrationAcrossRecomposition() = transformTest {
        val rendered = mutableListOf<String>()
        var registrations = 0
        val rowProjection = RowProjection(rendered)
        val registry = object : ProjectionRegistry {
            override val projections: Map<KClass<*>, Projection<*>>
                get() {
                    registrations++
                    return mapOf(Row::class to rowProjection)
                }
        }
        var name by mutableStateOf("first")
        transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
            syzygy {
                alignGraph { ProjectionRoot() }
                syzygy {
                    alignGraph { registry }
                    Project(Row(name))
                }
            }
        }

        advance()
        name = "second"
        advance()

        assertEquals(listOf("row first", "row second"), rendered)
        assertEquals(1, registrations)
    }

    @Test
    fun capturingOutputRequiresAMatchingProjectionInItsOwnAlignment() {
        val children = listOf(
            Graph() to "The current graph has no projection for Screen.",
            Any() to "No projection registry for this output.",
        )
        for ((child, message) in children) {
            val failure = assertThrows(IllegalStateException::class.java) {
                transformTest {
                    transform<Projected<Screen>?>(
                        initialValue = null,
                        scope = backgroundScope,
                        dispatcher = testDispatcher,
                    ) {
                        syzygy {
                            alignGraph { ProjectionRoot() }
                            syzygy {
                                alignGraph {
                                    Graph(Screen::class to ScreenProjection("parent", mutableListOf()))
                                }
                                syzygy {
                                    alignGraph { child }
                                    projected(Screen(Row("missing screen projection")))
                                }
                            }
                        }
                    }
                    advance()
                }
            }

            assertTrue(failure.message.orEmpty().startsWith(message))
        }
    }
}

private class ProjectionRoot(
    override val childAlignmentStrategy: AlignmentStrategy = ProjectionAlignmentStrategy,
) : HasChildAlignmentStrategy

private data class AppOutput(val screen: Screen)

private data class Screen(val row: Row)

private data class Row(val name: String)

private class ScreenProjection(
    private val label: String,
    private val rendered: MutableList<String>,
) : Projection<Screen> {
    @Composable
    override fun project(output: Screen) {
        SideEffect { rendered += label }
        Project(output.row)
    }
}

private class RowProjection(private val rendered: MutableList<String>) : Projection<Row> {
    @Composable
    override fun project(output: Row) {
        SideEffect { rendered += "row ${output.name}" }
    }
}

private class Graph(
    vararg projections: Pair<KClass<*>, Projection<*>>,
    private val onClose: () -> Unit = {},
) : ProjectionRegistry, AutoCloseable {
    override val projections = projections.toMap()

    override fun close() = onClose()
}
