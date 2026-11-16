package syzygy

import androidx.compose.runtime.Composable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Milestone 1 (retention): a device can't be rotated in a JVM test, so retention is exercised by
 * driving the retain registry's keep/retire directly — asserting that a kept-then-re-entered node is
 * the same live aligned unit (its state and scope preserved), and that retiring it discards. See
 * IMPLEMENTATION_PLAN §5.1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetentionTest {

    /** A retained unit with a stable output; the point under test is instance/scope preservation. */
    private fun retainedRuntime(hostContext: CoroutineContext): AlignedRuntime<Unit, Int> =
        AlignedRuntime.create(
            input = Unit,
            parent = null,
            lifetime = Lifetime.Retained,
            hostContext = hostContext,
            align = { scope, _ ->
                object : SyzygyGraph<Unit, Int> {
                    override val coroutineScope = scope
                    override val root = object : Model<Unit, Int> {
                        @Composable
                        override fun transform(input: Unit): Int = 7
                    }
                }
            },
        )

    @Test
    fun keptNodeIsTheSameLiveUnitAcrossReentryThenRetires() = runTest {
        val registry = MutableRetainRegistry()
        val key = "home"

        // First entry: the aligned unit is created and runs on its owned scope.
        val first = registry.getOrCreate(key) { retainedRuntime(UnconfinedTestDispatcher(testScheduler)) }
        assertEquals("the live unit produced its output", 7, first.output.value)
        assertTrue("owned scope is running", first.scope.coroutineContext[Job]!!.isActive)

        // Re-entry (e.g. after a config change): the same key returns the same live unit — same instance,
        // same running composition, so its in-memory state and scope are preserved by construction.
        val second = registry.getOrCreate<AlignedRuntime<Unit, Int>>(key) {
            error("must not recreate a retained unit")
        }
        assertSame("re-entry returns the same aligned unit", first, second)
        assertTrue("scope kept running across re-entry", second.scope.coroutineContext[Job]!!.isActive)

        // Removal retires it: the aligned scope is cancelled.
        registry.retire(key)
        testScheduler.advanceUntilIdle()
        assertFalse("retire cancels the aligned scope", first.scope.coroutineContext[Job]!!.isActive)
    }

    @Test
    fun retireAllDiscardsEveryRetainedUnit() = runTest {
        val registry = MutableRetainRegistry()
        val a = retainedRuntime(UnconfinedTestDispatcher(testScheduler))
        val b = retainedRuntime(UnconfinedTestDispatcher(testScheduler))
        registry.getOrCreate("a") { a }
        registry.getOrCreate("b") { b }
        testScheduler.advanceUntilIdle()

        registry.retireAll()
        testScheduler.advanceUntilIdle()

        assertFalse(a.scope.coroutineContext[Job]!!.isActive)
        assertFalse(b.scope.coroutineContext[Job]!!.isActive)
    }
}
