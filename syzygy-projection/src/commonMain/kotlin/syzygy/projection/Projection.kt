package syzygy.projection

import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import dev.zacsweers.metro.DefaultBinding
import kotlin.reflect.KClass
import syzygy.AlignmentLocal
import syzygy.AlignmentStrategy
import syzygy.currentAlignmentLifetime

/** Renders an output in the UI composition. */
@DefaultBinding<Projection<*>>
interface Projection<Output : Any> {
    @Composable
    fun project(output: Output)
}

/** Implemented by graphs that expose their complete projection map, including inherited bindings. */
interface ProjectionRegistry {
    /** Each key is the exact output class handled by its projection. */
    val projections: Map<KClass<*>, Projection<*>>

    /** One rule per output class in this graph. The set may include inherited Metro bindings. */
    val projectionTransitions: Set<ProjectionTransition<*>> get() = emptySet()
}

/** Registers the complete map of each aligned [ProjectionRegistry]. */
object ProjectionAlignmentStrategy : AlignmentStrategy {
    @Composable
    override fun align(graph: Any) {
        if (graph !is ProjectionRegistry) return
        val parent = AlignedProjections.current
        val lifetime = currentAlignmentLifetime()
        val registry = retain(parent, lifetime) {
            ProjectionSnapshot(parent, graph.projections, graph.projectionTransitions, lifetime::retain)
        }
        AlignedProjections.provide(registry)
    }
}

internal val AlignedProjections = AlignmentLocal<ProjectionSnapshot>()
private val LocalProjectionRegistry = compositionLocalOf<ProjectionSnapshot?> { null }

/** True for the current rendering. Outgoing projections and their descendants receive false. */
val LocalProjectionActive = compositionLocalOf { true }

/** An output paired with the immutable projection registry of its aligned graph. */
class Projected<out Output : Any> internal constructor(
    val output: Output,
    internal val registry: ProjectionSnapshot,
)

/** Captures the active graph's projections for rendering [output] outside its alignment. */
@Composable
fun <Output : Any> projected(output: Output): Projected<Output> {
    val registry = requireProjectionRegistry()
    checkNotNull(registry.find(output::class)) {
        "The current graph has no projection for ${output::class.simpleName}."
    }
    return remember(registry, outputIdentity(output)) { Projected(output, registry) }
}

@Composable
private fun requireProjectionRegistry(): ProjectionSnapshot =
    checkNotNull(LocalProjectionRegistry.current ?: AlignedProjections.currentInAlignment) {
        "No projection registry for this output. Use the ProjectionHost that recorded it " +
            "or render it within an alignment."
    }

/** Renders an output using its captured registry or the active alignment's projections. */
@Composable
fun Project(output: Any) {
    val value = if (output is Projected<*>) output.output else output
    val registry = if (output is Projected<*>) {
        output.registry
    } else {
        LocalProjectionHost.current?.find(output) ?: requireProjectionRegistry()
    }

    val frame = remember(registry, outputIdentity(value)) { ProjectionFrame(value, registry) }
    if (frame.hasTransition) registry.KeepOpen()
    ProjectWithTransitions(frame)
}

internal class ProjectionFrame(private val value: Any, private val registry: ProjectionSnapshot) {
    @Suppress("UNCHECKED_CAST")
    private val type = value::class as KClass<Any>
    private val projection = checkNotNull(registry.find(type)) {
        "The current graph has no projection for ${type.simpleName}."
    }
    private val transition = registry.findTransition(type)
    val hasTransition: Boolean get() = transition != null

    fun transformFrom(old: ProjectionFrame): ContentTransform? =
        if (old.type == type) transition?.transform(old.value, value) else null

    @Composable
    fun Render(active: Boolean) {
        registry.KeepOpen()
        CompositionLocalProvider(
            LocalProjectionRegistry provides registry,
            LocalProjectionActive provides (active && LocalProjectionActive.current),
        ) {
            projection.project(value)
        }
    }
}

internal class ProjectionSnapshot(
    private val parent: ProjectionSnapshot?,
    projections: Map<KClass<*>, Projection<*>>,
    transitions: Set<ProjectionTransition<*>>,
    private val retainGraph: () -> AutoCloseable,
) {
    private val entries = projections.toMap()
    private val transitionEntries = buildMap {
        for (rule in transitions) {
            // Metro can instantiate inherited contributions again in a child's complete set.
            if (parent?.hasTransitionBinding(rule) == true) continue
            check(put(rule.outputType, rule) == null) {
                "Multiple projection transitions for ${rule.outputType.simpleName} in the same alignment."
            }
        }
    }

    // Recognize when an ancestor returns a result already registered by a descendant.
    fun includes(ancestor: ProjectionSnapshot): Boolean =
        this === ancestor || parent?.includes(ancestor) == true

    @Suppress("UNCHECKED_CAST")
    fun <Output : Any> find(type: KClass<Output>): Projection<Output>? =
        entries[type] as? Projection<Output>

    @Suppress("UNCHECKED_CAST")
    fun <Output : Any> findTransition(type: KClass<Output>): ProjectionTransition<Output>? =
        transitionEntries[type] as? ProjectionTransition<Output> ?: parent?.findTransition(type)

    private fun hasTransitionBinding(rule: ProjectionTransition<*>): Boolean =
        transitionEntries[rule.outputType]?.let { it::class == rule::class } == true ||
            parent?.hasTransitionBinding(rule) == true

    @Composable
    fun KeepOpen() {
        // Acquire before calling the renderer and release even if Compose abandons this pass.
        remember(this) { ProjectionReference(retainGraph()) }
    }
}

private class ProjectionReference(private val reference: AutoCloseable) : RememberObserver {
    override fun onRemembered() = Unit
    override fun onForgotten() = reference.close()
    override fun onAbandoned() = reference.close()
}
