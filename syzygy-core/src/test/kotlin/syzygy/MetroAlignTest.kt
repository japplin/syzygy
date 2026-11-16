package syzygy

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Milestone 2: DI through the `align` seam. Proves — still headless, still in a plain JVM test — that
 * a Metro `@DependencyGraph` can *be* a [Syzygy]: the graph is built on the scope Syzygy owns (handed
 * in via the factory `@Provides`), the root [Model] is `@Inject`-constructed, and a `@SingleIn` binding
 * is reachable from it. The core never names Metro — it only speaks [Syzygy]. See IMPLEMENTATION_PLAN §5.2.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetroAlignTest {

    @Inject
    @SingleIn(AppScope::class)
    class UserRepository {
        val currentUserName: String = "Ada"
    }

    @Inject
    class HomeModel(private val users: UserRepository) : Model<Unit, String> {
        @Composable
        override fun transform(input: Unit): String = "Hello, ${users.currentUserName}"
    }

    @DependencyGraph(AppScope::class)
    interface AppGraph : SyzygyGraph<Unit, String> {
        override val coroutineScope: CoroutineScope // backed by the @Provides below
        override val root: HomeModel                // Metro constructs it from its @Inject deps

        @DependencyGraph.Factory
        fun interface Factory {
            // The Syzygy scope is handed in by the engine; it becomes the graph's coroutineScope.
            fun create(@Provides scope: CoroutineScope): AppGraph
        }
    }

    @Test
    fun metroGraphIsBuiltThroughAlignAndRunsHeadless() = runTest {
        val runtime = AlignedRuntime.create(
            input = Unit,
            parent = null,
            lifetime = Lifetime.Scoped(backgroundScope),
            hostContext = UnconfinedTestDispatcher(testScheduler),
            // The align seam: build a Metro graph on the owned scope. The core sees only a Syzygy.
            align = { scope, _ -> createGraphFactory<AppGraph.Factory>().create(scope) },
        )

        assertEquals("the @Inject model ran with its @SingleIn binding", "Hello, Ada", runtime.output.value)
        assertTrue("DI graph is aligned to the owned, running scope", runtime.scope.coroutineContext[Job]!!.isActive)

        runtime.retire()
    }

    @Test
    fun theGraphsCoroutineScopeIsTheScopeSyzygyOwns() = runTest {
        lateinit var handedInScope: CoroutineScope
        val runtime = AlignedRuntime.create(
            input = Unit,
            parent = null,
            lifetime = Lifetime.Scoped(backgroundScope),
            hostContext = UnconfinedTestDispatcher(testScheduler),
            align = { scope, _ ->
                handedInScope = scope
                createGraphFactory<AppGraph.Factory>().create(scope)
            },
        )

        // The graph exposes the exact scope the engine created and handed to `align` (via @Provides).
        assertSame(handedInScope, runtime.unit.coroutineScope)
        runtime.retire()
    }
}
