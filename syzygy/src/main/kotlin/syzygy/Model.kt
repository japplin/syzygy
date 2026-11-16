package syzygy

import androidx.compose.runtime.Composable

/**
 * A composable transformation from an [Input] supplied by the host into an [Output].
 *
 * Models are deliberately small: Syzygy does not prescribe where they are stored or how they are
 * constructed. A root model normally lives in [rootSyzygy]; a child model can be held by any value
 * returned from [childSyzygy].
 */
interface Model<Input, Output> {
    /** Produces the current output for [input]. */
    @Composable
    fun transform(input: Input): Output
}
