package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.retain.RetainObserver
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

/**
 * The aligned unit: one **retained** object that owns the three things Syzygy aligns into a single
 * lifetime — a [CoroutineScope], a Compose runtime (the Molecule producing [output]), and the input
 * bridge — and retires them together.
 *
 * A syzygy is the alignment of three celestial bodies; this is the code shaped like that definition.
 * Because it is a [RetainObserver], it survives configuration change and is torn down only on
 * **permanent** removal ([onRetired]/[onUnused]) — behaving like a `ViewModel`. It is created and held
 * by [syzygy] via Compose's `retain {}`.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
class Syzygy<Input, Output>(
    context: CoroutineContext,
    initialInput: Input,
    alignment: @Composable (Input) -> Output,
) : RetainObserver {

    /** The owned scope. Effects inside [alignment] run on it and are cancelled exactly when it is. */
    val coroutineScope: CoroutineScope = CoroutineScope(context)

    /** The input bridge across the outer→inner composition boundary. */
    private val inputs: MutableStateFlow<Input> = MutableStateFlow(initialInput)

    /**
     * The aligned output: [alignment] run as this unit's **own** Molecule runtime on [coroutineScope],
     * in [RecompositionMode.Immediate] (headless — no frame clock). Effect-scope alignment falls out of
     * the recomposer being built from the owned scope.
     */
    val output: StateFlow<Output> = coroutineScope.launchMolecule(RecompositionMode.Immediate) {
        alignment(inputs.collectAsState().value)
    }

    /** Push a new input into the running unit (called from the outer composition on recomposition). */
    fun setInput(input: Input) {
        inputs.value = input
    }

    override fun onRetired() = coroutineScope.cancel()          // permanent removal (≈ ViewModel.onCleared)
    override fun onUnused() = coroutineScope.cancel()           // retained but never re-consumed
    override fun onRetained() = Unit
    override fun onEnteredComposition() = Unit
    override fun onExitedComposition() = Unit
}
