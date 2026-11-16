package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

/**
 * Factory for alignment behavior shared by one root Syzygy composition.
 *
 * A strategy is created outside the retained Molecule composition; its [createAlignment] result is
 * created once by the root ViewModel and is used by every [childSyzygy] in that tree.
 */
fun interface AlignmentStrategy {
    /** Creates the stateful alignment instance for one root tree. */
    fun createAlignment(): Alignment
}

/**
 * A policy applied to every value returned from [childSyzygy].
 *
 * The child value is intentionally opaque. Implementations may recognize optional capabilities,
 * such as [ProjectionRegistry] or [SatelliteRegistry], without requiring a common graph type.
 */
interface Alignment {
    /** Aligns [value] with the child composition group represented by [scope]. */
    @Composable
    fun align(value: Any, scope: CoroutineScope)
}

/**
 * Builds a strategy that applies all [alignments] to every child value.
 *
 * Call this from composition with stable alignment objects, typically [ProjectionAlignment] and
 * [SatelliteAlignment].
 */
@Composable
fun alignmentStrategy(vararg alignments: Alignment): AlignmentStrategy {
    val selected = alignments.toList()
    return remember(selected) {
        AlignmentStrategy { CompositeAlignment(selected) }
    }
}

/** The alignment installed by [rootSyzygy] for the active Molecule composition. */
val LocalAlignment: ProvidableCompositionLocal<Alignment?> = staticCompositionLocalOf { null }

private class CompositeAlignment(
    private val alignments: List<Alignment>,
) : Alignment {
    @Composable
    override fun align(value: Any, scope: CoroutineScope) {
        alignments.forEach { alignment -> alignment.align(value, scope) }
    }
}
