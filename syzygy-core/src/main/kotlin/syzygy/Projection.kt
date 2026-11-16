package syzygy

import androidx.compose.runtime.Composable

/**
 * A view onto an [Output] — the pure, disowned counterpart to a [Model].
 *
 * Syzygy owns the **producer** side (a model produces output) and disowns the **consumer** side (what
 * draws output). A projection is therefore a plain, portable, swappable contract: bring your own,
 * swap freely, register globally. A model and its projection are paired only by their shared [Output]
 * type parameter (compiler-enforced), never by matching names.
 *
 * Projections are pure functions of output — anything scoped they might need is computed by the model
 * and passed through the output (including callbacks that close over scoped dependencies), which keeps
 * the render layer injection-free and lets the registry be a single global map. See IMPLEMENTATION_PLAN §3.
 */
interface Projection<Output> {
    @Composable
    fun project(output: Output)
}
