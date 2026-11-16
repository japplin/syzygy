package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/** Optional Compose work associated with an aligned graph. */
fun interface AlignmentStrategy {
    @Composable
    fun align(graph: Any)
}

/** A graph can supply the work to run in its alignment. */
interface HasAlignmentStrategy {
    val alignmentStrategy: AlignmentStrategy
}

private val LocalAlignment = staticCompositionLocalOf<SyzygyScope?> { null }

/**
 * Evaluates [content] in a graph alignment. Supply a different [key] to replace the graph and its
 * descendants. The block must align exactly one graph before entering children or returning.
 */
@OptIn(InternalComposeApi::class)
@Composable
fun <Result> syzygy(
    key: Any? = Unit,
    content: @Composable SyzygyScope.() -> Result,
): Result {
    val parent = LocalAlignment.current
    val parentGraph = parent?.requireAlignedGraph()
    return key(key, parentGraph) {
        val scope = SyzygyScope(parentGraph)
        // Keep the value-producing block in the caller's recompose scope. The Unit content of
        // CompositionLocalProvider can recompose on its own, leaving the caller's result stale.
        val composer = currentComposer
        composer.startProvider(LocalAlignment provides scope)
        val result = scope.content()
        scope.requireAlignedGraph()
        composer.endProvider()
        result
    }
}

@DslMarker
annotation class SyzygyDsl

@SyzygyDsl
class SyzygyScope internal constructor(private val parent: GraphLifetime?) {
    private var alignment: GraphLifetime? = null

    /** Creates the root graph on first alignment and returns the same instance on recomposition. */
    @Composable
    fun <Graph : Any> alignGraph(create: () -> Graph): Graph = align(null, create)

    /** Creates a child using the graph from the immediately enclosing alignment. */
    @Composable
    fun <Graph : Any> alignGraph(parentGraph: Any, create: () -> Graph): Graph =
        align(parentGraph, create)

    @Composable
    private fun <Graph : Any> align(parentGraph: Any?, create: () -> Graph): Graph {
        check(LocalAlignment.current === this) { "Call alignGraph in its own syzygy block." }
        check(alignment == null) { "Each syzygy block can call alignGraph only once." }
        check(parent?.graph === parentGraph) {
            if (parent == null) {
                "A root syzygy block must call alignGraph without a parent."
            } else {
                "Pass the graph from the immediately enclosing syzygy block to alignGraph."
            }
        }

        val lifetime = remember { GraphLifetime(create()) }
        alignment = lifetime
        @Suppress("UNCHECKED_CAST")
        val graph = lifetime.graph as Graph
        (graph as? HasAlignmentStrategy)?.alignmentStrategy?.align(graph)
        return graph
    }

    internal fun requireAlignedGraph(): GraphLifetime =
        checkNotNull(alignment) { "Call alignGraph before entering a child syzygy or returning." }
}

internal class GraphLifetime(val graph: Any) : RememberObserver {
    private var closed = false

    override fun onRemembered() = Unit

    override fun onForgotten() = close()

    override fun onAbandoned() = close()

    private fun close() {
        if (closed) return
        closed = true
        (graph as? AutoCloseable)?.close()
    }
}
