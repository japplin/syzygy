package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.RetainObserver
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import jdk.internal.foreign.abi.SharedUtils.alignment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
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
class RetainedCoroutineScope(
    context: CoroutineContext,
) : RetainObserver {
    val coroutineScope: CoroutineScope = CoroutineScope(context)
    override fun onRetired() = coroutineScope.cancel()
    override fun onUnused() = coroutineScope.cancel()
    override fun onRetained() = Unit
    override fun onEnteredComposition() = Unit
    override fun onExitedComposition() = Unit
}

@Composable
fun retainedCoroutineScope(context: CoroutineContext? = null): CoroutineScope {
    val resolvedContext =
        context ?: rememberCoroutineScope().coroutineContext.minusKey(Job)
    return retain { RetainedCoroutineScope(resolvedContext) }.coroutineScope
}

/**
 *
 */
@Composable
inline fun <Input, reified Output> rootSyzygy(
    input: Input,
    coroutineContext: CoroutineContext? = null,
    recompositionMode: RecompositionMode = RecompositionMode.ContextClock,
    crossinline alignment: @Composable (CoroutineScope, Input) -> Output
): Output {
    val coroutineScope = retainedCoroutineScope(coroutineContext)
    val inputs = retain { mutableStateOf(input) }
    SideEffect { inputs.value = input }
    val rootOutput by retain {
        coroutineScope.launchMolecule(recompositionMode) {
            val currentInput by inputs
            alignment(coroutineScope, currentInput)
        }
    }.collectAsState()
    return rootOutput
}

@Composable
inline fun <reified Output> childSyzygy(crossinline alignment: (CoroutineScope) -> Output): Output {
    val coroutineScope = retainedCoroutineScope(Dispatchers.Main)
    return remember {
        alignment(coroutineScope)
    }
}
