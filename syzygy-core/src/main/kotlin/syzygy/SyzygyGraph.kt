package syzygy

import kotlinx.coroutines.CoroutineScope

/**
 * A DI graph shaped as an aligned unit — the type your dependency graphs implement for the
 * `AlignedRuntime`/`SyzygyContent` engine.
 *
 * It carries the two things whose shared lifetime *is* the alignment: the owned [coroutineScope] and
 * the [root] model that runs on it. A Metro `@DependencyGraph` (or any DI graph, or a hand-wired
 * factory) implements this interface; the [coroutineScope] is the one the engine handed into the
 * `align` seam, so scope, Compose runtime, and graph share one lifetime by construction.
 *
 * The core never names a DI framework — `SyzygyGraph` is the only contract the seam speaks. See
 * IMPLEMENTATION_PLAN §3. (This is the graph-side contract; the concrete retained holder is [Syzygy].)
 */
interface SyzygyGraph<Input, Output> {
    /** The scope the engine owns and handed you through `align`; effects in [root] run on it. */
    val coroutineScope: CoroutineScope

    /** The root model of this aligned unit. */
    val root: Model<Input, Output>
}
