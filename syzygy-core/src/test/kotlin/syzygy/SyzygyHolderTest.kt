package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.retain.LocalRetainedValuesStoreProvider
import androidx.compose.runtime.retain.ManagedRetainedValuesStore
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The concrete [Syzygy] holder owns the scope + Molecule + input and retires them together via
 * [androidx.compose.runtime.retain.RetainObserver]. Verified two ways: directly ([onRetired] cancels
 * the owned scope) and end-to-end (the retain store retiring the unit cancels effects in `alignment`).
 */
@OptIn(ExperimentalComposeRuntimeApi::class, ExperimentalCoroutinesApi::class)
class SyzygyHolderTest {

    @Test
    fun onRetiredCancelsTheOwnedScope() = runTest {
        val syzygy = Syzygy<Int, Int>(
            context = SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            initialInput = 1,
            alignment = { _, input -> input },
        )
        testScheduler.advanceUntilIdle()
        assertTrue("owned scope runs while the unit is alive", syzygy.coroutineScope.coroutineContext[Job]!!.isActive)

        syzygy.onRetired()
        assertFalse("permanent removal cancels the owned scope", syzygy.coroutineScope.coroutineContext[Job]!!.isActive)
    }

    @Test
    fun storeRetiringTheUnitCancelsEffectsInAlignment() = runTest {
        val store = ManagedRetainedValuesStore()
        var effectCancelled = false
        val alignment: @Composable (CoroutineScope, Unit) -> Int = { _, _ ->
            LaunchedEffect(Unit) {
                try {
                    awaitCancellation()
                } finally {
                    effectCancelled = true
                }
            }
            0
        }

        moleculeFlow(RecompositionMode.Immediate) {
            LocalRetainedValuesStoreProvider(store) {
                syzygy(Unit, alignment)
            }
            0
        }.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // Host torn down for good: the store retires the Syzygy -> onRetired() -> scope cancelled ->
        // the effect launched inside `alignment` is cancelled.
        store.dispose()
        testScheduler.advanceUntilIdle()
        assertTrue("retiring the aligned unit cancels its effects", effectCancelled)
    }
}
