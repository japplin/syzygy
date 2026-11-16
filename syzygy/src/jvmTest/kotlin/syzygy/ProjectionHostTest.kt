package syzygy

import androidx.appstate.transform.listener
import androidx.appstate.transform.testing.transformTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import syzygy.projection.Projection
import syzygy.projection.ProjectionAlignment
import syzygy.projection.ProjectionEntry
import syzygy.projection.ProjectionHost
import syzygy.projection.ProjectionRegistry

class ProjectionHostTest {
    @Test
    fun transitionKeepsOutgoingProjectionsAndPairsIncomingProjectionsWithTheirOutput() = transformTest {
        var route by mutableStateOf(0)
        val views = listOf(Projection<String> {}, Projection<String> {})
        val host = ProjectionHost<String>()
        val job = backgroundScope.launch {
            listener(dispatcher = testDispatcher) {
                host.Content {
                    val index = route
                    syzygy(index) {
                        alignGraph { ProjectingGraph(ProjectionEntry(String::class, views[index])) }
                        "route $index"
                    }
                }
            }
        }

        advance()
        val outgoing = checkNotNull(host.state.value)
        assertEquals("route 0", outgoing.output)
        assertSame(views[0], outgoing.projections.find(String::class))

        route = 1
        advance()
        val incoming = checkNotNull(host.state.value)
        assertEquals("route 1", incoming.output)
        assertSame(views[1], incoming.projections.find(String::class))
        assertSame(views[0], outgoing.projections.find(String::class))
        assertNotSame(outgoing.projections, incoming.projections)

        job.cancelAndJoin()
        assertNull(host.state.value)
        assertSame(views[0], outgoing.projections.find(String::class))
    }

    @Test
    fun childProjectionLeavesTheSnapshotWhenItsBranchLeavesAlignment() = transformTest {
        var showChild by mutableStateOf(true)
        val childView = Projection<Int> {}
        val host = ProjectionHost<String>()
        backgroundScope.launch {
            listener(dispatcher = testDispatcher) {
                host.Content {
                    syzygy {
                        val root = alignGraph { Any() }
                        if (showChild) {
                            syzygy {
                                alignGraph(root) {
                                    ProjectingGraph(ProjectionEntry(Int::class, childView))
                                }
                            }
                        }
                        "screen"
                    }
                }
            }
        }

        advance()
        val withChild = checkNotNull(host.state.value)
        assertSame(childView, withChild.projections.find(Int::class))

        showChild = false
        advance()
        assertNull(checkNotNull(host.state.value).projections.find(Int::class))
        assertSame(childView, withChild.projections.find(Int::class))
    }
}

private class ProjectingGraph(entry: ProjectionEntry<*>) : ProjectionRegistry, HasAlignmentStrategy {
    override val projections = setOf(entry)
    override val alignmentStrategy = ProjectionAlignment
}
