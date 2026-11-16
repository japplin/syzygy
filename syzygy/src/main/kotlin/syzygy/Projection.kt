package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/** Renders one concrete [Output] type. */
interface Projection<Output> {
    /** Renders [output]. */
    @Composable
    fun project(output: Output)
}

/** A type-safe association between an output type and the projection that renders it. */
data class ProjectionEntry<Output : Any>(
    val type: KClass<Output>,
    val projection: Projection<Output>,
)

/** Creates one entry for a [ProjectionRegistry] contribution. */
fun <Output : Any> projectionEntry(
    type: KClass<Output>,
    projection: Projection<Output>,
): ProjectionEntry<Output> = ProjectionEntry(type, projection)

/**
 * An optional capability through which a child value contributes output projections.
 *
 * When [ProjectionAlignment] is enabled, every registry returned by [childSyzygy] is added to the
 * aggregate registry exposed by [RootSyzygy.projectionRegistry] for as long as that child remains
 * in the Molecule composition. The registry uses exact runtime output types; register an entry for
 * the concrete type that will be passed to [Project].
 */
interface ProjectionRegistry {
    /** The projections contributed by this value. Each output type may appear only once per tree. */
    val entries: Set<ProjectionEntry<*>>
}

/** Creates an immutable [ProjectionRegistry] contribution. */
fun projectionRegistry(vararg entries: ProjectionEntry<*>): ProjectionRegistry =
    ImmutableProjectionRegistry(entries.toSet())

/**
 * The projection registry for the current Syzygy tree.
 *
 * [rootSyzygy] installs the aggregate registry within its Molecule composition. UI that renders a
 * [RootSyzygy] should provide [RootSyzygy.projectionRegistry] around [Project].
 */
val LocalProjectionRegistry: ProvidableCompositionLocal<ProjectionRegistry> =
    staticCompositionLocalOf { EmptyProjectionRegistry }

/** Returns the projection registered for [output]'s exact runtime type. */
fun <Output : Any> ProjectionRegistry.projectionFor(output: Output): Projection<Output> =
    projectionForOrNull(output) ?: missingProjection(output)

private fun <Output : Any> ProjectionRegistry.projectionForOrNull(output: Output): Projection<Output>? {
    val entry = entries.singleOrNull { it.type == output::class } ?: return null
    @Suppress("UNCHECKED_CAST")
    return entry.projection as Projection<Output>
}

private fun missingProjection(output: Any): Nothing = error(
    "No Projection registered for ${output::class.qualifiedName}. " +
        "Contribute a ProjectionRegistry from a childSyzygy value.",
)

/**
 * Renders [output] with the projection contributed for its exact runtime type.
 *
 * The first root output can arrive before its child contributions are committed. In that brief
 * interval this function renders nothing and observes the aggregate registry; it then recomposes
 * once the contribution is available. A missing type after any registry entry exists is an error.
 */
@Composable
fun <Output : Any> Project(output: Output) {
    val registry = LocalProjectionRegistry.current
    val projection = registry.projectionForOrNull(output)
    if (projection != null) {
        projection.project(output)
        return
    }

    if (registry.entries.isNotEmpty()) missingProjection(output)
}

/** Aligns [ProjectionRegistry] child contributions with the root aggregate registry. */
object ProjectionAlignment : Alignment {
    @Composable
    override fun align(value: Any, scope: CoroutineScope) {
        if (value is ProjectionRegistry) {
            val registration = remember { Any() }
            val registry = LocalProjectionRegistry.current as? AggregateProjectionRegistry
                ?: error("rootSyzygy must provide the aggregate ProjectionRegistry.")

            // Register during composition so outer UI may project the first root output. The effect
            // owns only removal when the keyed child leaves the tree.
            remember(value) {
                registry.add(registration, value)
            }
            DisposableEffect(registration) {
                onDispose { registry.remove(registration) }
            }
        }
    }
}

internal class AggregateProjectionRegistry : ProjectionRegistry {
    private val contributions = LinkedHashMap<Any, ProjectionRegistry>()
    private var aggregateEntries by mutableStateOf<Set<ProjectionEntry<*>>>(emptySet())

    override val entries: Set<ProjectionEntry<*>>
        get() = aggregateEntries

    fun add(token: Any, registry: ProjectionRegistry) {
        contributions[token] = registry
        rebuild()
    }

    fun remove(token: Any) {
        if (contributions.remove(token) != null) rebuild()
    }

    private fun rebuild() {
        val entriesByType = LinkedHashMap<KClass<*>, ProjectionEntry<*>>()
        contributions.values.forEach { registry ->
            registry.entries.forEach { entry ->
                val previous = entriesByType.putIfAbsent(entry.type, entry)
                check(previous == null) {
                    "Multiple Projections were contributed for ${entry.type.qualifiedName}."
                }
            }
        }
        aggregateEntries = entriesByType.values.toSet()
    }
}

private class ImmutableProjectionRegistry(
    override val entries: Set<ProjectionEntry<*>>,
) : ProjectionRegistry {
    init {
        val duplicate = entries.groupBy { it.type }.entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) {
            "ProjectionRegistry contains multiple Projections for ${duplicate?.key?.qualifiedName}."
        }
    }
}

private object EmptyProjectionRegistry : ProjectionRegistry {
    override val entries: Set<ProjectionEntry<*>> = emptySet()
}
