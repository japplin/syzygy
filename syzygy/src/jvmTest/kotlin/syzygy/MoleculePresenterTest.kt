package syzygy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MoleculePresenterTest {
    @Test
    fun updatesPresenterStateAndClosesTheGraphWhenCollectionEnds() = runTest {
        var graphCreations = 0
        var graphClosures = 0

        moleculeFlow(RecompositionMode.Immediate) {
            syzygy {
                val graph = alignGraph {
                    graphCreations++
                    CounterGraph { graphClosures++ }
                }
                CounterState(graph.count) { graph.count++ }
            }
        }.test {
            val initial = awaitItem()
            assertEquals(0, initial.count)
            initial.increment()
            assertEquals(1, awaitItem().count)
        }

        assertEquals(1, graphCreations)
        assertEquals(1, graphClosures)
    }

    private data class CounterState(val count: Int, val increment: () -> Unit)

    private class CounterGraph(private val onClose: () -> Unit) : AutoCloseable {
        var count by mutableIntStateOf(0)

        override fun close() = onClose()
    }
}
