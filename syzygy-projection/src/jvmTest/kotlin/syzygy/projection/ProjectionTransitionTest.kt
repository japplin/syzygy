package syzygy.projection

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.reflect.KClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import syzygy.HasAlignmentStrategy
import syzygy.syzygy

class ProjectionTransitionTest {
    @Test
    fun eachProjectComparesItsOwnValuesAndKeepsItsRenderingWhenTheRuleReturnsNull() = transformTest {
        val comparisons = mutableListOf<Pair<String, String>>()
        val renderings = mutableMapOf<String, Any>()
        val graph = object : ProjectionRegistry, HasAlignmentStrategy {
            override val alignmentStrategy = ProjectionAlignmentStrategy
            override val projections: Map<KClass<*>, Projection<*>> = mapOf(
                Output::class to object : Projection<Output> {
                    @Composable
                    override fun project(output: Output) {
                        val rendering = remember { Any() }
                        SideEffect { renderings[output.name] = rendering }
                    }
                },
            )
            override val projectionTransitions = setOf(projectionTransition<Output> { old, new ->
                comparisons += old.name to new.name
                null
            })
        }
        var left by mutableStateOf(Output("left first"))
        var right by mutableStateOf(Output("right first"))
        transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
            syzygy {
                alignGraph { graph }
                Project(left)
                Project(right)
            }
        }

        advance()
        assertEquals(emptyList<Pair<String, String>>(), comparisons)
        val leftRendering = renderings.getValue("left first")
        val rightRendering = renderings.getValue("right first")
        assertNotSame(leftRendering, rightRendering)

        left = Output("left second")
        advance()
        right = Output("right second")
        advance()
        left = Output("left third")
        advance()

        assertEquals(
            listOf("left first" to "left second", "right first" to "right second", "left second" to "left third"),
            comparisons,
        )
        assertSame(leftRendering, renderings.getValue("left third"))
        assertSame(rightRendering, renderings.getValue("right second"))
    }

    @Test
    fun nearestRuleWinsWhenMetroIncludesInheritedContributionsInTheSet() {
        val app = appRule()
        val feature = featureRule()
        val retainGraph = { AutoCloseable {} }
        val root = ProjectionSnapshot(null, emptyMap(), setOf(app), retainGraph)
        val child = ProjectionSnapshot(root, emptyMap(), setOf(appRule(), feature), retainGraph)
        val grandchild = ProjectionSnapshot(child, emptyMap(), setOf(appRule(), featureRule()), retainGraph)
        val sibling = ProjectionSnapshot(root, emptyMap(), setOf(appRule()), retainGraph)

        assertSame(feature, child.findTransition(Output::class))
        assertSame(feature, grandchild.findTransition(Output::class))
        assertSame(app, sibling.findTransition(Output::class))
    }

    private fun appRule() = projectionTransition<Output> { _, _ -> null }

    private fun featureRule() = projectionTransition<Output> { _, _ -> null }

    private data class Output(val name: String)
}
