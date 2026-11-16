package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Starts (or reconnects to) the single retained Molecule composition for this UI location.
 *
 * The returned [RootSyzygy] is both a [State] of [Output] and the owner of the projection registry
 * assembled by children of [root]. Provide [RootSyzygy.projectionRegistry] to UI with
 * `CompositionLocalProvider` before calling [Project].
 *
 * The underlying runtime is held by a ViewModel, so it survives configuration changes. [root] and
 * [input] are updated after recomposition without recreating its child values. The strategy is
 * deliberately fixed for the lifetime of this root ViewModel; use a stable strategy instance.
 */
@Composable
fun <Input, Output : Any> rootSyzygy(
    input: Input,
    strategy: AlignmentStrategy,
    root: @Composable (Input) -> Output,
): RootSyzygy<Output> {
    val holder = rememberSyzygyViewModel(
        input = input,
        strategy = strategy,
        root = root,
    )
    return RootSyzygy(
        output = holder.output.collectAsState(),
        projectionRegistry = holder.projectionRegistry,
    )
}

/**
 * The observable root [value] together with projections assembled by its child Syzygy values.
 *
 * This is intentionally not a graph interface. The root model may return any output type, and
 * child values may use any dependency-injection system or no DI system at all.
 */
@Stable
class RootSyzygy<Output : Any> internal constructor(
    private val output: State<Output>,
    val projectionRegistry: ProjectionRegistry,
) : State<Output> by output

@Composable
private fun <Input, Output : Any> rememberSyzygyViewModel(
    input: Input,
    strategy: AlignmentStrategy,
    root: @Composable (Input) -> Output,
): SyzygyViewModel<Input, Output> {
    val saveableStateRegistry = LocalSaveableStateRegistry.current
    val holder = viewModel<SyzygyViewModel<Input, Output>> {
        SyzygyViewModel(
            initialInput = input,
            strategy = strategy,
            root = root,
            initialSaveableStateRegistry = saveableStateRegistry,
        )
    }

    SideEffect {
        holder.update(
            input = input,
            root = root,
            saveableStateRegistry = saveableStateRegistry,
        )
    }

    return holder
}

private class SyzygyViewModel<Input, Output : Any>(
    initialInput: Input,
    strategy: AlignmentStrategy,
    root: @Composable (Input) -> Output,
    initialSaveableStateRegistry: SaveableStateRegistry?,
) : ViewModel() {
    private val inputs = MutableStateFlow(initialInput)
    private val roots = MutableStateFlow(root)
    private val saveableStateRegistries = MutableStateFlow(initialSaveableStateRegistry)
    private val alignment = strategy.createAlignment()
    val projectionRegistry: ProjectionRegistry = AggregateProjectionRegistry()

    // This ViewModel hosts a headless composition. Immediate observes Compose snapshots without a
    // MonotonicFrameClock, which is unavailable in viewModelScope by default.
    val output: StateFlow<Output> = viewModelScope.launchMolecule(RecompositionMode.Immediate) {
        val input by inputs.collectAsState()
        val root by roots.collectAsState()
        val saveableStateRegistry by saveableStateRegistries.collectAsState()
        val result = ResultHolder<Output>()

        CompositionLocalProvider(
            LocalAlignment provides alignment,
            LocalProjectionRegistry provides projectionRegistry,
            LocalSaveableStateRegistry provides saveableStateRegistry,
        ) {
            result.value = root(input)
        }

        result.value
    }

    fun update(
        input: Input,
        root: @Composable (Input) -> Output,
        saveableStateRegistry: SaveableStateRegistry?,
    ) {
        inputs.value = input
        roots.value = root
        saveableStateRegistries.value = saveableStateRegistry
    }
}

private class ResultHolder<T : Any> {
    private object Unset

    private var slot: Any? = Unset

    @Suppress("UNCHECKED_CAST")
    var value: T
        get() = slot.let { check(it !== Unset) { "root output read before it was produced" }; it as T }
        set(value) {
            slot = value
        }
}
