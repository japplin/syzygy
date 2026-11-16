package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.retain.LocalRetainedValuesStoreProvider
import androidx.compose.runtime.retain.ManagedRetainedValuesStore
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Gate for the retain-based `syzygy()`: prove it runs **headless** in a plain JVM test when a
 * `RetainedValuesStore` is provided — i.e. Compose's `retain {}` works without an Android host, so the
 * platform-free/headless-testable discipline survives the move to `retain`.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
class RetainSyzygyTest {

    @Test
    fun syzygyRunsHeadlessWithAProvidedRetainStore() = runTest {
        val store = ManagedRetainedValuesStore()
        // Passed as a typed positional arg to select the minimal `syzygy` overload unambiguously
        // (the rich overload requires an `align` parameter, so it does not apply here).
        val alignment: @Composable (CoroutineScope, Int) -> Int = { _, input -> input * 2 }

        moleculeFlow(RecompositionMode.Immediate) {
            var output = 0
            LocalRetainedValuesStoreProvider(store) {
                output = syzygy(21, alignment)
            }
            output
        }.test {
            assertEquals("retain-based syzygy produced its aligned output headlessly", 42, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
