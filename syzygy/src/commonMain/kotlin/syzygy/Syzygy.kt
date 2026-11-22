package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

/** Optional Compose work associated with an aligned graph. */
fun interface AlignmentStrategy : AutoCloseable {
    @Composable
    fun align(graph: Any)

    /** Observes the result of an aligned block before Syzygy returns it to its caller. */
    @Composable
    fun onResult(graph: Any, result: Any?) = Unit

    /** Releases resources when the graph that supplies this strategy retires or is abandoned. */
    override fun close() = Unit
}

/** A graph can supply a strategy for its own alignment. It owns that strategy's lifetime. */
interface HasAlignmentStrategy {
    val alignmentStrategy: AlignmentStrategy
}

/**
 * A graph can require this strategy on every descendant alignment. Ancestor strategies run
 * from outermost to innermost, before the aligned graph's own [HasAlignmentStrategy].
 * The graph that supplies the strategy owns it until its descendants release their alignments.
 */
interface HasChildAlignmentStrategy {
    val childAlignmentStrategy: AlignmentStrategy
}

/** A value supplied by an alignment and inherited by its descendants. */
class AlignmentLocal<Value : Any> {
    val current: Value?
        @Composable get() = LocalAlignment.current?.find(this)

    /** The value supplied by this alignment. */
    val currentInAlignment: Value?
        @Composable get() = LocalAlignment.current?.findHere(this)

    /** Supplies [value] for the current graph alignment and its descendants. */
    @Composable
    fun provide(value: Value) {
        val scope = checkNotNull(LocalAlignment.current) { "Provide alignment values inside syzygy." }
        scope.requireAlignedGraph()
        scope.provide(this, value)
    }
}

@PublishedApi
internal val LocalAlignment = staticCompositionLocalOf<SyzygyScope?> { null }

/**
 * Evaluates [content] in a graph alignment. Supply a different [key] to replace the graph and its
 * descendants. The block must align exactly one graph before entering children or returning.
 */
@OptIn(InternalComposeApi::class)
@Composable
inline fun <Result> syzygy(
    key: Any? = Unit,
    crossinline content: @Composable SyzygyScope.() -> Result,
): Result {
    val parent = LocalAlignment.current
    val parentAlignment = parent?.requireAlignedGraph()
    return key(key, parentAlignment) {
        val scope = SyzygyScope(key, parent, parentAlignment)
        // Keep the block in the caller's recompose scope so each evaluation creates its own
        // SyzygyScope, including when the result is Unit.
        val composer = currentComposer
        composer.startProvider(LocalAlignment provides scope)
        val result = scope.content()
        scope.onResult(result)
        composer.endProvider()
        result
    }
}

@DslMarker
annotation class SyzygyDsl

@SyzygyDsl
class SyzygyScope @PublishedApi internal constructor(
    private val key: Any?,
    private val parent: SyzygyScope?,
    private val parentAlignment: GraphLifetime?,
) {
    private var alignment: GraphLifetime? = null
    private val inheritedStrategies: List<AlignmentStrategy> = parent?.childStrategies.orEmpty()
    private var childStrategies = inheritedStrategies
    private var values: MutableMap<AlignmentLocal<*>, Any>? = null

    /**
     * Creates a graph on first alignment and reuses it for as long as Compose retains it.
     *
     * A custom suspending [onClose] is captured when [create] runs and replaces the graph's
     * default close operation. Close runs after the graph's strategies close and all
     * [AlignmentLifetime] handles and child graphs have released it.
     *
     * With a finite [shutdownTimeout], retirement starts a deadline for outstanding holds.
     * Closing starts a separate deadline of the same duration. [AlignmentLifetime.awaitClosed]
     * reports either timeout. The duration is captured when the graph is created.
     */
    @Composable
    fun <Graph : Any> alignGraph(
        onClose: (suspend (Graph) -> Unit)? = null,
        shutdownTimeout: Duration = Duration.INFINITE,
        create: () -> Graph,
    ): Graph {
        check(LocalAlignment.current === this) { "Call alignGraph in its own syzygy block." }
        check(alignment == null) { "Each syzygy block can call alignGraph only once." }
        require(shutdownTimeout > Duration.ZERO) { "shutdownTimeout must be positive." }

        // Capture the dispatcher, independently of the job that Compose cancels on exit.
        val dispatcher = rememberCoroutineScope().coroutineContext[ContinuationInterceptor]
        val lifetime = retain(key, parentAlignment) {
            val graph = create()
            val closeGraph = onClose?.let { close -> suspend { close(graph) } }
            GraphLifetime(
                graph,
                parentAlignment,
                dispatcher ?: EmptyCoroutineContext,
                closeGraph,
                key,
                shutdownTimeout,
            )
        }
        alignment = lifetime
        @Suppress("UNCHECKED_CAST")
        val graph = lifetime.graph as Graph
        lifetime.childAlignmentStrategy?.let { strategy ->
            childStrategies = inheritedStrategies + strategy
        }
        for (strategy in inheritedStrategies) {
            key(strategy) { strategy.align(graph) }
        }
        lifetime.alignmentStrategy?.align(graph)
        return graph
    }

    @PublishedApi
    @Composable
    internal fun onResult(result: Any?) {
        val lifetime = requireAlignedGraph()
        for (strategy in inheritedStrategies) {
            key(strategy) { strategy.onResult(lifetime.graph, result) }
        }
        lifetime.alignmentStrategy?.onResult(lifetime.graph, result)
    }

    internal fun <Value : Any> provide(local: AlignmentLocal<Value>, value: Value) {
        val supplied = values ?: mutableMapOf<AlignmentLocal<*>, Any>().also { values = it }
        supplied[local] = value
    }

    internal fun <Value : Any> find(local: AlignmentLocal<Value>): Value? =
        findHere(local) ?: parent?.find(local)

    @Suppress("UNCHECKED_CAST")
    internal fun <Value : Any> findHere(local: AlignmentLocal<Value>): Value? =
        values?.get(local) as Value?

    /**
     * After [alignGraph], remembers a value at this call site for the current alignment.
     * [calculation] receives the graph and runs again when the alignment changes.
     */
    @Composable
    fun <Value> rememberAligned(calculation: (graph: Any) -> Value): Value {
        val lifetime = requireAlignedGraph()
        return remember(lifetime) { calculation(lifetime.graph) }
    }

    @PublishedApi
    internal fun requireAlignedGraph(): GraphLifetime =
        checkNotNull(alignment) { "Call alignGraph before entering a child syzygy or returning." }
}
