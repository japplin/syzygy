package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * The holder/engine: one aligned unit at runtime.
 *
 * It owns a [CoroutineScope], builds a [Syzygy] on it via the `align` seam, and runs that unit's root
 * [Model] as its **own** Compose runtime — a headless Molecule loop in [RecompositionMode.Immediate].
 *
 * Two properties that make the whole design work, and must not be "simplified" away:
 *
 * - **Effect-scope alignment via Molecule.** `LaunchedEffect`/`rememberCoroutineScope` derive their
 *   coroutine context from the composition's recomposer, not from any scope in lexical reach. Running
 *   the model with `scope.launchMolecule(...)` builds that recomposer from the owned [scope], so
 *   effects inside `transform` run on the owned scope and die exactly when it is cancelled. Molecule's
 *   role here is *effect-scope alignment*, not "producing a StateFlow." (IMPLEMENTATION_PLAN §3.)
 * - **`RecompositionMode.Immediate`.** These are headless nodes with no vsync frame clock. Immediate
 *   recomposes on snapshot writes, needs no frame clock, and correctly prevents animation APIs from
 *   running inside models. Never use `ContextClock` for nodes.
 *
 * One composition has exactly one effect coroutine context, so per-node effect scoping *requires* a
 * recomposer per node — this is why Syzygy runs one runtime per node, not one per screen. Do not
 * collapse it (IMPLEMENTATION_PLAN §6, discipline #4).
 */
class AlignedRuntime<Input, Output> private constructor(
    val unit: SyzygyGraph<Input, Output>,
    initialInput: Input,
) : Retirable {

    val scope: CoroutineScope get() = unit.coroutineScope

    // Input is a snapshot state so a changing input on the parent's recomposition flows into this
    // node's independent composition (a fresh composition would otherwise capture only the first input).
    private val inputState = mutableStateOf(initialInput)

    var input: Input
        get() = inputState.value
        set(value) { inputState.value = value }

    /**
     * The unit's output as observable state. Produced by the owned scope's own Molecule runtime.
     * `LocalSyzygy` is provided *inside* this composition so a nested `syzygy()` call within
     * `transform` discovers this unit as its parent (IMPLEMENTATION_PLAN §3, parent discovery).
     */
    /**
     * Retention store for units nested inside this unit's `transform` (model-nesting). A child
     * Molecule composition does not inherit `CompositionLocal`s from outside, so the engine must
     * re-provide [LocalRetainRegistry] here — rooted at this unit, so nested retained units are retired
     * when this unit is (IMPLEMENTATION_PLAN §5.5, model-nesting).
     */
    private val childRegistry = MutableRetainRegistry()

    /**
     * The unit's output as observable state. Produced by the owned scope's own Molecule runtime.
     * [LocalSyzygy] is provided *inside* this composition so a nested `syzygy()` call within
     * `transform` discovers this unit as its parent, and [LocalRetainRegistry] so a nested retained
     * unit has a retention host (IMPLEMENTATION_PLAN §3, parent discovery).
     */
    val output: StateFlow<Output> = scope.launchMolecule(mode = RecompositionMode.Immediate) {
        // CompositionLocalProvider returns Unit, so capture transform's result in a holder and return
        // it. The holder is set on every (synchronous) recomposition before it is read.
        val holder = Holder<Output>()
        CompositionLocalProvider(
            LocalSyzygy provides unit,
            LocalRetainRegistry provides childRegistry,
        ) {
            holder.value = unit.root.transform(inputState.value)
        }
        holder.value
    }

    override fun retire() {
        // Retire nested units first, then cancel this unit's scope (which tears down its Molecule loop).
        childRegistry.retireAll()
        scope.cancel("Aligned unit retired")
    }

    companion object {
        /**
         * Create an aligned runtime. Syzygy owns the scope: it is built here (not by the caller) so
         * the alignment can be *guaranteed*. [hostContext] contributes the dispatcher (so a test's
         * `TestDispatcher` flows through); [lifetime] decides the parent [Job], hence what cancels it:
         *
         * - [Lifetime.Remembered] → child of the host composition's job: dies on composition exit.
         * - [Lifetime.Scoped]     → child of the caller's scope: dies with that scope.
         * - [Lifetime.Retained]/[Lifetime.NavEntry] → an independent root job: survives composition
         *   teardown (config change) and is retired only when its [RetainRegistry] forgets it.
         */
        fun <Input, Output> create(
            input: Input,
            parent: SyzygyGraph<*, *>?,
            lifetime: Lifetime,
            hostContext: CoroutineContext,
            align: (CoroutineScope, SyzygyGraph<*, *>?) -> SyzygyGraph<Input, Output>,
        ): AlignedRuntime<Input, Output> {
            val dispatcher = hostContext[ContinuationInterceptor]
            val parentJob: Job? = when (lifetime) {
                Lifetime.Remembered -> hostContext[Job]
                is Lifetime.Scoped -> lifetime.scope.coroutineContext[Job]
                Lifetime.Retained, Lifetime.NavEntry -> null
            }
            var context: CoroutineContext = SupervisorJob(parentJob) + CoroutineName("syzygy")
            if (dispatcher != null) context += dispatcher
            val owned = CoroutineScope(context)

            val unit = align(owned, parent)
            check(unit.coroutineScope === owned) {
                "align must return a Syzygy whose coroutineScope is the scope it was handed. Wire your " +
                    "graph's coroutineScope to the @Provides scope parameter (see README, AppGraph.Factory)."
            }
            return AlignedRuntime(unit, input)
        }
    }
}

/** Convenience for `syzygy()`/`SyzygyContent`: collect the runtime's output as Compose [State]. */
@Composable
fun <Output> AlignedRuntime<*, Output>.outputState(): State<Output> = output.collectAsState()

/** A single-slot holder that lets [AlignedRuntime] return a (possibly null) value through `CompositionLocalProvider`. */
private class Holder<T> {
    private object Unset

    private var slot: Any? = Unset

    @Suppress("UNCHECKED_CAST")
    var value: T
        get() = slot.let { check(it !== Unset) { "output read before transform ran" }; it as T }
        set(v) { slot = v }
}
