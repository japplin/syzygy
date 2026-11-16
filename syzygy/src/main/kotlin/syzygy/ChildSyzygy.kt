package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope

/**
 * Creates and retains a child value within the root Molecule composition.
 *
 * [create] runs once for a given [keys] group. It receives a scope that survives recomposition and
 * configuration change, then is cancelled when that group leaves composition. The value itself may
 * be any type: a dependency graph, a model holder, or a plain object. If it implements an optional
 * capability recognized by the root [AlignmentStrategy], that capability is applied automatically.
 *
 * Children do not start their own Molecule runtime. They compose as part of the root runtime owned
 * by [rootSyzygy].
 */
@Composable
fun <T : Any> childSyzygy(
    vararg keys: Any?,
    create: (CoroutineScope) -> T,
): T = key(keys.toList()) {
    val scope = rememberCoroutineScope()
    val value = remember { create(scope) }
    val alignment = LocalAlignment.current ?: error(
        "childSyzygy must run inside rootSyzygy so it can use the root Alignment.",
    )

    alignment.align(value, scope)
    value
}
