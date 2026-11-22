package syzygy.projection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import syzygy.AlignmentStrategy

/**
 * Shares projection registrations between a presenter composition and its UI.
 * Install this instance as the alignment strategy for graphs whose results are rendered by [Project].
 * A result returned by different branches needs a distinct instance for each branch the UI renders.
 */
class ProjectionHost : AlignmentStrategy by ProjectionAlignmentStrategy {
    // Snapshot state commits the registrations together with the AppState transform's result.
    // Weak keys let the UI keep an outgoing registration alive by holding the output itself.
    private val registrations = mutableStateMapOf<OutputIdentity, ProjectionSnapshot?>()

    @Composable
    @NonSkippableComposable
    override fun onResult(graph: Any, result: Any?) {
        val registry = AlignedProjections.currentInAlignment ?: return
        if (result == null || registry.find(result::class) == null) return

        Snapshot.withoutReadObservation {
            registrations.keys.removeAll { it.isCollected }
            val key = outputIdentity(result)
            val previous = registrations[key]
            when {
                previous == null && !registrations.containsKey(key) -> registrations[key] = registry
                previous == null -> Unit // This instance was returned by conflicting branches.
                registry.includes(previous) -> registrations[key] = registry
                !previous.includes(registry) -> registrations[key] = null
            }
        }
    }

    /** Renders a returned output using the registry recorded by its alignment. */
    @Composable
    fun Project(output: Any) {
        CompositionLocalProvider(LocalProjectionHost provides this) {
            syzygy.projection.Project(output)
        }
    }

    internal fun find(output: Any): ProjectionSnapshot? {
        val key = outputIdentity(output)
        val registry = registrations[key]
        check(registry != null || !registrations.containsKey(key)) {
            "Several alignments returned the same ${output::class.simpleName} instance. " +
                "Return distinct instances or capture the output with projected."
        }
        return registry
    }
}

internal val LocalProjectionHost = compositionLocalOf<ProjectionHost?> { null }

internal interface OutputIdentity {
    val isCollected: Boolean
}

internal expect fun outputIdentity(output: Any): OutputIdentity
