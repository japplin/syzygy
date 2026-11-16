package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyzygyTest {
    @Test
    fun projectionRegistryLooksUpTypedProjection() {
        val projection = StringProjection()
        val registry = projectionRegistry(
            projectionEntry(String::class, projection),
        )

        assertSame(projection, registry.projectionFor("Syzygy"))
    }

    @Test
    fun aggregateProjectionRegistryRejectsDuplicateOutputTypes() {
        val aggregate = AggregateProjectionRegistry()
        aggregate.add(Any(), projectionRegistry(projectionEntry(String::class, StringProjection())))

        val error = runCatching {
            aggregate.add(Any(), projectionRegistry(projectionEntry(String::class, StringProjection())))
        }.exceptionOrNull()

        check(error is IllegalStateException)
        assertTrue(error.message!!.contains("Multiple Projections"))
    }

    @Test
    fun childSyzygyRemembersItsValueAndCancelsThePreviousScopeWhenKeyChanges() = runTest(UnconfinedTestDispatcher()) {
        val key = mutableStateOf("first")
        val created = mutableListOf<ScopedValue>()

        moleculeFlow(RecompositionMode.Immediate) {
            val currentKey = key.value
            CompositionLocalProvider(LocalAlignment provides NoOpAlignment) {
                childSyzygy(currentKey) { scope ->
                    ScopedValue(created.size, scope).also(created::add)
                }.id
            }
            created.last().id
        }.test {
            assertEquals(0, awaitItem())

            key.value = "second"
            assertEquals(1, awaitItem())
            advanceUntilIdle()

            assertFalse(created[0].scope.coroutineContext[Job]!!.isActive)
            assertTrue(created[1].scope.coroutineContext[Job]!!.isActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aggregateProjectionRegistryAddsAndRemovesContributions() {
        val aggregate = AggregateProjectionRegistry()
        val contribution = projectionRegistry(projectionEntry(String::class, StringProjection()))
        val token = Any()

        aggregate.add(token, contribution)
        assertEquals(1, aggregate.entries.size)

        aggregate.remove(token)
        assertEquals(0, aggregate.entries.size)
    }

    @Test
    fun satelliteRegistryLaunchesEverySatelliteOnTheSuppliedScope() {
        val satellite = RecordingSatellite()
        val registry = object : SatelliteRegistry {
            override val satellites = setOf(satellite)
        }
        val scope = CoroutineScope(SupervisorJob())

        registry.launchAll(scope)
        assertTrue(satellite.started)
        assertSame(scope, satellite.scope)
        scope.cancel()
    }

    @Test
    fun alignmentStrategyInvokesEverySuppliedAlignment() = runTest(UnconfinedTestDispatcher()) {
        val first = CountingAlignment()
        val second = CountingAlignment()

        moleculeFlow(RecompositionMode.Immediate) {
            val strategy = alignmentStrategy(first, second)
            val alignment = remember(strategy) { strategy.createAlignment() }
            CompositionLocalProvider(LocalAlignment provides alignment) {
                childSyzygy("value") { Any() }
            }
            first.calls + second.calls
        }.test {
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private object NoOpAlignment : Alignment {
        @Composable
        override fun align(value: Any, scope: CoroutineScope) = Unit
    }

    private class CountingAlignment : Alignment {
        var calls = 0

        @Composable
        override fun align(value: Any, scope: CoroutineScope) {
            calls++
        }
    }

    private data class ScopedValue(
        val id: Int,
        val scope: CoroutineScope,
    )

    private class StringProjection : Projection<String> {
        @Composable
        override fun project(output: String) = Unit
    }

    private class RecordingSatellite : Satellite {
        var started = false
        var scope: CoroutineScope? = null

        override fun launch(scope: CoroutineScope) {
            started = true
            this.scope = scope
        }
    }
}
