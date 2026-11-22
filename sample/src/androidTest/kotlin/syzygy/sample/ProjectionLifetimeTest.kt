package syzygy.sample

import androidx.activity.compose.setContent
import androidx.appstate.transform.transform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import syzygy.HasAlignmentStrategy
import syzygy.HasChildAlignmentStrategy
import syzygy.projection.Project
import syzygy.projection.Projection
import syzygy.projection.ProjectionHost
import syzygy.projection.ProjectionRegistry
import syzygy.projection.projectionTransition
import syzygy.syzygy

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ProjectionLifetimeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun closesTheOutgoingGraphAfterTheExitAnimation() {
        val host = ProjectionHost()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val closed = mutableListOf<Int>()
        var route by mutableIntStateOf(0)
        try {
            compose.activityRule.scenario.onActivity { activity ->
                val presentation = transform<AppOutput?>(
                    initialValue = null,
                    scope = scope,
                    dispatcher = Dispatchers.Main.immediate,
                ) {
                    syzygy {
                        alignGraph { AppGraph(host) }
                        val index = route
                        val screen = syzygy(key = index) {
                            alignGraph { ScreenGraph(index, closed) }
                            ScreenOutput(index)
                        }
                        AppOutput(screen)
                    }
                }
                activity.setContent {
                    val state by presentation
                    state?.let { host.Project(it) }
                }
            }

            compose.onNodeWithText("Route 0").assertExists()
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { route = 1 }
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText("Route 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Route 0").assertExists()
            compose.runOnUiThread { assertEquals(emptyList<Int>(), closed) }

            compose.mainClock.autoAdvance = true
            compose.waitUntil(5_000) {
                compose.runOnUiThread { closed == listOf(0) }
            }
            compose.onNodeWithText("Route 0").assertDoesNotExist()
            compose.onNodeWithText("Route 1").assertExists()
        } finally {
            compose.mainClock.autoAdvance = true
            scope.cancel()
        }
    }

    private data class AppOutput(val screen: ScreenOutput)
    private data class ScreenOutput(val index: Int)

    private class AppGraph(host: ProjectionHost) :
        ProjectionRegistry, HasAlignmentStrategy, HasChildAlignmentStrategy {
        override val alignmentStrategy = host
        override val childAlignmentStrategy = host
        override val projections: Map<KClass<*>, Projection<*>> = mapOf(
            AppOutput::class to object : Projection<AppOutput> {
                @Composable
                override fun project(output: AppOutput) {
                    Project(output.screen)
                }
            },
        )
        override val projectionTransitions = setOf(projectionTransition<AppOutput> { old, new ->
            if (old.screen != new.screen) fadeIn(tween(1_000)) togetherWith fadeOut(tween(1_000)) else null
        })
    }

    private class ScreenGraph(private val index: Int, private val closed: MutableList<Int>) :
        ProjectionRegistry, AutoCloseable {
        override val projections: Map<KClass<*>, Projection<*>> = mapOf(
            ScreenOutput::class to object : Projection<ScreenOutput> {
                @Composable
                override fun project(output: ScreenOutput) {
                    check(index !in closed) { "Tried to render a closed graph." }
                    Text("Route ${output.index}")
                }
            },
        )

        override fun close() {
            closed += index
        }
    }
}
