package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Milestone 1, the heart: prove — headless, in a plain JVM `src/test` — that a [Model] runs as a
 * Molecule loop on an owned scope, that a `LaunchedEffect` inside `transform` runs on that scope, and
 * that retiring the unit cancels the scope (and therefore the effect). See IMPLEMENTATION_PLAN §5.1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlignedRuntimeTest {

    /** Wrap a model as a [Syzygy] whose scope is the one Syzygy hands into `align`. */
    private fun <I, O> unitOf(model: Model<I, O>) =
        { scope: CoroutineScope, _: SyzygyGraph<*, *>? ->
            object : SyzygyGraph<I, O> {
                override val coroutineScope = scope
                override val root = model
            }
        }

    @Test
    fun modelRunsAsAMoleculeLoopOnTheOwnedScope() = runTest {
        val model = object : Model<Unit, Int> {
            @Composable
            override fun transform(input: Unit): Int = 10
        }

        val runtime = AlignedRuntime.create(
            input = Unit,
            parent = null,
            lifetime = Lifetime.Scoped(backgroundScope),
            hostContext = UnconfinedTestDispatcher(testScheduler),
            align = unitOf(model),
        )

        assertEquals("the owned scope's Molecule loop produced the model's output", 10, runtime.output.value)
        assertTrue("the aligned unit runs on the scope Syzygy owns", runtime.scope.coroutineContext[Job]!!.isActive)

        runtime.retire()
    }

    @Test
    fun transformReactsToStateAsAMoleculeLoop() = runTest {
        // The canonical Molecule proof (cf. README "Testing" example): the model recomposes as its
        // observed state changes, emitting a new output each time. This is exactly the loop the engine
        // runs per node — here driven through moleculeFlow + Turbine so recomposition is observable.
        val source = MutableStateFlow(1)
        val model = object : Model<Unit, Int> {
            @Composable
            override fun transform(input: Unit): Int {
                val n by source.collectAsState()
                return n * 10
            }
        }

        moleculeFlow(RecompositionMode.Immediate) { model.transform(Unit) }.test {
            assertEquals(10, awaitItem())
            source.value = 5
            assertEquals(50, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun launchedEffectRunsOnTheOwnedScopeAndDiesOnRetire() = runTest {
        var effectStarted = false
        var effectCancelled = false

        val model = object : Model<Unit, Int> {
            @Composable
            override fun transform(input: Unit): Int {
                LaunchedEffect(Unit) {
                    effectStarted = true
                    try {
                        awaitCancellation()
                    } finally {
                        effectCancelled = true
                    }
                }
                return 0
            }
        }

        val runtime = AlignedRuntime.create(
            input = Unit,
            parent = null,
            lifetime = Lifetime.Retained, // independent job: only retire() cancels it
            hostContext = backgroundScope.coroutineContext,
            align = unitOf(model),
        )
        testScheduler.advanceUntilIdle()

        assertTrue("effect in transform launched on the owned scope", effectStarted)
        assertFalse("effect still running while the unit is alive", effectCancelled)
        assertTrue("owned scope is active", runtime.scope.coroutineContext[Job]!!.isActive)

        runtime.retire()
        testScheduler.advanceUntilIdle()

        assertFalse("retire cancels the owned scope", runtime.scope.coroutineContext[Job]!!.isActive)
        assertTrue("cancelling the scope cancels the effect running on it", effectCancelled)
    }

    @Test
    fun rememberedUnitDiesWithItsHostButRetainedSurvivesIt() = runTest {
        val constant = object : Model<Unit, Int> {
            @Composable
            override fun transform(input: Unit): Int = 0
        }

        // The host composition's scope (what a Remembered unit is tied to).
        val host = CoroutineScope(
            SupervisorJob(backgroundScope.coroutineContext[Job]) + UnconfinedTestDispatcher(testScheduler),
        )

        val remembered = AlignedRuntime.create(
            Unit, null, Lifetime.Remembered, host.coroutineContext, unitOf(constant),
        )
        val retained = AlignedRuntime.create(
            Unit, null, Lifetime.Retained, host.coroutineContext, unitOf(constant),
        )
        assertTrue(remembered.scope.coroutineContext[Job]!!.isActive)
        assertTrue(retained.scope.coroutineContext[Job]!!.isActive)

        // The host goes away (e.g. composition exit / rotation).
        host.cancel()
        testScheduler.advanceUntilIdle()

        assertFalse("Remembered is tied to the host and discards with it", remembered.scope.coroutineContext[Job]!!.isActive)
        assertTrue("Retained is independent and survives the host", retained.scope.coroutineContext[Job]!!.isActive)

        retained.retire()
    }
}
