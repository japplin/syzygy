package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.staticCompositionLocalOf
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import javax.swing.InputMap
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The enclosing aligned unit, discovered by a child `syzygy()` call so its graph can extend the
 * parent's. `null` at the root. Provided *inside* each unit's own Molecule composition by
 * [AlignedRuntime], so a nested `syzygy()` within `transform` reads its parent automatically.
 *
 * The `parent as ConcreteGraph` cast a caller then performs is the visible, accepted cost of the core
 * being graph-agnostic — keep it explicit rather than hiding it (IMPLEMENTATION_PLAN §3, §6 discipline #3).
 */
val LocalSyzygy: ProvidableCompositionLocal<SyzygyGraph<*, *>?> = staticCompositionLocalOf { null }

private data class RetainKey(val hash: Int, val keys: List<Any?>)

@Composable
fun <Input, Output> syzygy1(
    input: Input,
    alignment: @Composable (CoroutineScope, Input) -> Output
) : Output {
    val inputFlow = MutableStateFlow(input)
    val hostScope = rememberCoroutineScope()

    val output by hostScope.launchMolecule(RecompositionMode.Immediate) {
        alignment(hostScope, inputFlow.collectAsState().value)
    }.collectAsState()

    return output
}

/**
 * Align and run [alignment] as its own Molecule runtime, returning its latest [Output].
 *
 * Creates and **retains** a [Syzygy] — the aligned unit that owns the scope, the Molecule runtime, and
 * the input bridge, and retires them together. Because the `Syzygy` is held via Compose's `retain {}`
 * (and is itself a `RetainObserver`), it behaves like a `ViewModel`: it survives configuration change
 * and is torn down only on *permanent* removal, not on recomposition or rotation. Effects inside
 * [alignment] run on the owned scope and are aligned to that same lifetime.
 *
 * Requires a `RetainedValuesStore` in the composition (`LocalRetainedValuesStore`) — provided by the
 * Android host, or by a test exercising retention headlessly.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
@Composable
fun <Input, Output> syzygy(
    input: Input,
    alignment: @Composable (Input) -> Output,
): Output {
    // Borrow the composition's dispatcher (stable, e.g. Main); the Syzygy owns an independent, retained
    // job, so it survives configuration change and is cancelled only on permanent removal.
    val dispatcher = rememberCoroutineScope().coroutineContext[ContinuationInterceptor]
    val syzygy = retain { Syzygy(SupervisorJob() + (dispatcher ?: EmptyCoroutineContext), input, alignment) }
    SideEffect { syzygy.setInput(input) }
    return syzygy.output.collectAsState().value
}

/**
 * Align and run a [Model], returning its [Output] as observable [State].
 *
 * Creates an aligned runtime: it owns a [CoroutineScope], runs your model on it as its own Compose
 * runtime, and hands the owned scope (and the discovered parent) to [align] — the seam where you build
 * whatever you want bound to this lifetime, almost always a DI graph implementing [Syzygy]. Because the
 * model runs on the owned scope, any `LaunchedEffect`/`rememberCoroutineScope` inside `transform` runs
 * on **that** scope: it survives configuration change when the scope is retained and is cancelled
 * exactly when the unit is retired.
 *
 * @param input the model input; a change flows into the node's composition on recomposition.
 * @param keys identity for lists/tabs — distinct keys produce distinct aligned units at one call site.
 * @param lifetime how long the aligned unit lives (default [Lifetime.Retained]).
 * @param align builds the [Syzygy] on the owned scope; the returned unit's `coroutineScope` must be
 *   the scope handed in.
 */
@Composable
fun <Input, Output> syzygy(
    input: Input,
    vararg keys: Any?,
    lifetime: Lifetime = Lifetime.Retained,
    align: (CoroutineScope, SyzygyGraph<*, *>?) -> SyzygyGraph<Input, Output>,
): State<Output> {
    val parent = LocalSyzygy.current
    val hostScope = rememberCoroutineScope()
    val keyList = keys.toList()

    val runtime: AlignedRuntime<Input, Output> = when (lifetime) {
        Lifetime.Remembered -> {
            // Lives in the composition: created with a job parented to the host composition, and
            // retired on composition exit. The deliberate opt-out — dies on rotation.
            val rt = remember(keyList) {
                AlignedRuntime.create(input, parent, lifetime, hostScope.coroutineContext, align)
            }
            DisposableEffect(rt) { onDispose { rt.retire() } }
            rt
        }

        is Lifetime.Scoped -> {
            // Lives exactly as long as the provided scope; NOT retired on composition exit.
            remember(keyList, lifetime.scope) {
                AlignedRuntime.create(input, parent, lifetime, hostScope.coroutineContext, align)
            }
        }

        Lifetime.Retained, Lifetime.NavEntry -> {
            // Lives in the ambient RetainRegistry: survives composition teardown (config change), and
            // is retired only when the host forgets its key (removal / nav pop).
            val registry = LocalRetainRegistry.current ?: error(
                "syzygy(lifetime = $lifetime) needs a RetainRegistry in scope. Wrap the tree in " +
                    "SyzygyContent, use the nav3 integration, or provide LocalRetainRegistry in a test.",
            )
            val key = RetainKey(currentCompositeKeyHash, keyList)
            remember(key) {
                registry.getOrCreate(key) {
                    AlignedRuntime.create(input, parent, lifetime, hostScope.coroutineContext, align)
                }
            }
        }
    }

    // Feed a changing input into the node's independent composition.
    SideEffect { runtime.input = input }

    return runtime.outputState()
}

/**
 * Run an aligned unit and hand its [Output] to [content]. The core entry point for *rendering* a
 * screen; `syzygy()` is the entry point for *nesting* a model. Same engine, two entry points.
 *
 * The core disowns the consumer side, so [content] is explicit here — `syzygy-projection` adds a
 * convenience overload that defaults it to the ambient `Project(output)` lookup. This overload also
 * provides a [RetainRegistry] for the subtree if none is ambient, so `Retained` works out of the box
 * (Android layers back it with a config-change-surviving store; a bare core host uses an in-memory one).
 */
@Composable
fun <Input, Output> SyzygyContent(
    input: Input,
    vararg keys: Any?,
    lifetime: Lifetime = Lifetime.Retained,
    align: (CoroutineScope, SyzygyGraph<*, *>?) -> SyzygyGraph<Input, Output>,
    content: @Composable (Output) -> Unit,
) {
    val ambient = LocalRetainRegistry.current
    val registry = ambient ?: remember { MutableRetainRegistry() }
    if (ambient == null) {
        DisposableEffect(registry) { onDispose { registry.retireAll() } }
    }
    CompositionLocalProvider(LocalRetainRegistry provides registry) {
        val output by syzygy(input, keys = keys, lifetime = lifetime, align = align)
        content(output)
    }
}
