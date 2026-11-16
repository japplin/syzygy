package syzygy.projection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.reflect.KClass
import syzygy.AlignmentStrategy

/** Renders a presenter output in the UI composition. */
fun interface Projection<Output : Any> {
    @Composable
    fun project(output: Output)
}

class ProjectionEntry<Output : Any>(
    val type: KClass<Output>,
    val projection: Projection<Output>,
)

/** Implemented by graphs that contribute projections through [ProjectionAlignment]. */
interface ProjectionRegistry {
    val projections: Set<ProjectionEntry<*>>
}

object ProjectionAlignment : AlignmentStrategy {
    @Composable
    override fun align(graph: Any) {
        check(graph is ProjectionRegistry) { "ProjectionAlignment requires a ProjectionRegistry." }
        val collector = checkNotNull(LocalCollector.current) {
            "Run ProjectionAlignment inside ProjectionHost.Content."
        }
        DisposableEffect(collector, graph) {
            val registration = Any()
            collector.add(registration, graph.projections.toSet())
            onDispose { collector.remove(registration) }
        }
    }
}

class ProjectionSnapshot internal constructor(
    private val entries: Map<KClass<*>, ProjectionEntry<*>>,
) {
    @Suppress("UNCHECKED_CAST")
    fun <Output : Any> find(type: KClass<Output>): Projection<Output>? =
        (entries[type] as? ProjectionEntry<Output>)?.projection
}

/** The presenter output and the projections registered in the same composition commit. */
data class Projected<Output : Any>(
    val output: Output,
    val projections: ProjectionSnapshot,
)

/** Bridges a presenter composition and one or more UI compositions. */
class ProjectionHost<Output : Any> {
    private val presentation = mutableStateOf<Projected<Output>?>(null)
    val state: State<Projected<Output>?> get() = presentation

    /** Compose this once per host. The runtime that calls it owns the coroutine lifecycle. */
    @Composable
    fun Content(content: @Composable () -> Output) {
        val collector = remember { ProjectionCollector() }
        DisposableEffect(collector) {
            onDispose { presentation.value = null }
        }
        CompositionLocalProvider(LocalCollector provides collector) {
            val output = content()
            // A contribution can recompose independently of the presenter that returns output.
            // Observe registrations here so changes also publish a new snapshot in that case.
            collector.observeRegistrations()
            SideEffect { presentation.value = Projected(output, collector.snapshot()) }
        }
    }
}

val LocalProjectionRegistry = compositionLocalOf<ProjectionSnapshot> {
    error("Provide a ProjectionSnapshot before projecting an output.")
}

@Composable
fun <Output : Any> Project(output: Output) {
    @Suppress("UNCHECKED_CAST")
    val type = output::class as KClass<Output>
    val projection = checkNotNull(LocalProjectionRegistry.current.find(type)) {
        "No projection was registered for ${type.simpleName}."
    }
    projection.project(output)
}

private val LocalCollector = compositionLocalOf<ProjectionCollector?> { null }

private class ProjectionCollector {
    private val registrations = mutableStateOf<Map<Any, Set<ProjectionEntry<*>>>>(emptyMap())
    private var lastRegistrations = registrations.value
    private var lastSnapshot = ProjectionSnapshot(emptyMap())

    fun add(key: Any, entries: Set<ProjectionEntry<*>>) {
        registrations.value += key to entries
    }

    fun remove(key: Any) {
        registrations.value -= key
    }

    fun observeRegistrations() {
        registrations.value
    }

    fun snapshot(): ProjectionSnapshot {
        val current = registrations.value
        if (current == lastRegistrations) return lastSnapshot

        val entries = buildMap {
            for (registration in current.values) {
                for (entry in registration) {
                    check(put(entry.type, entry) == null) {
                        "Multiple active projections for ${entry.type.simpleName}."
                    }
                }
            }
        }
        lastRegistrations = current
        return ProjectionSnapshot(entries).also { lastSnapshot = it }
    }
}
