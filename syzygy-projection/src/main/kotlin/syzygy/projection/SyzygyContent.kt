package syzygy.projection

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import syzygy.Lifetime
import syzygy.SyzygyGraph
import syzygy.SyzygyContent as coreSyzygyContent

/**
 * Run an aligned unit and render its output with the ambiently-registered [Projection][syzygy.Projection].
 *
 * The convenience form of the core `SyzygyContent`: it supplies the consumer side (`Project(output)`)
 * the core deliberately disowns, so the trailing lambda is `align`, matching the README's root usage:
 *
 * ```
 * setContent {
 *     SyzygyContent(Unit, "app") { scope, _ ->
 *         createGraphFactory<AppGraph.Factory>().create(scope)
 *     }
 * }
 * ```
 */
@Composable
fun <Input, Output : Any> SyzygyContent(
    input: Input,
    vararg keys: Any?,
    lifetime: Lifetime = Lifetime.Retained,
    align: (CoroutineScope, SyzygyGraph<*, *>?) -> SyzygyGraph<Input, Output>,
) {
    coreSyzygyContent(input = input, keys = keys, lifetime = lifetime, align = align) { output ->
        Project(output)
    }
}
