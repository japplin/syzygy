package syzygy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.structuralEqualityPolicy

/** Routes one Back event through the current rendering. Use one host per UI input owner. */
class BackHost {
    private val root = BackNode { BackRegistration(active = true, onBackPress = null) }
    private val canHandle = derivedStateOf(structuralEqualityPolicy()) { root.resolve() != null }

    /** True when a rendering handles Back or a modal layer blocks it. */
    val canHandleBack: Boolean get() = canHandle.value

    /** Invokes the selected action, returning true if this host consumed the event. */
    fun dispatchBack(): Boolean {
        val action = root.resolve() ?: return false
        action()
        return true
    }

    /** Connects nested [BackScope] and [BackOverlay] calls to this host. */
    @Composable
    fun Content(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalBackNode provides root, content = content)
    }
}

/**
 * A branch of the UI that can receive Back. Its eligible children take precedence over
 * [onBackPress]. The UI parent can select one sibling with [active], for example by pane focus.
 * If multiple sibling scopes are active, the most recently registered one takes precedence.
 * An inactive scope excludes its entire subtree, including any overlays.
 */
@Composable
fun BackScope(
    onBackPress: (() -> Unit)? = null,
    active: Boolean = true,
    content: @Composable () -> Unit,
) {
    BackBranch(BackRegistration(active, onBackPress), content)
}

/**
 * A layer that receives Back before ordinary branches of the same parent. Use the same
 * [order] as the layout uses for draw order. Higher values win, with the most recently
 * registered layer first on ties. A [modal] layer consumes Back even without an action.
 */
@Composable
fun BackOverlay(
    order: Int,
    onBackPress: (() -> Unit)? = null,
    modal: Boolean = true,
    active: Boolean = true,
    content: @Composable () -> Unit,
) {
    BackBranch(BackRegistration(active, onBackPress, Layer(order, modal)), content)
}

private val LocalBackNode = compositionLocalOf<BackNode?> { null }

@Composable
private fun BackBranch(registration: BackRegistration, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalBackNode.current) { "Install a BackHost before adding Back scopes." }
    val latest = rememberUpdatedState(registration)
    val node = remember { BackNode { latest.value } }
    DisposableEffect(parent, node) {
        parent.children.add(node)
        onDispose { parent.children.remove(node) }
    }
    CompositionLocalProvider(LocalBackNode provides node, content = content)
}

private data class Layer(val order: Int, val modal: Boolean)

private data class BackRegistration(
    val active: Boolean,
    val onBackPress: (() -> Unit)?,
    val layer: Layer? = null,
)

private val ConsumeBack: () -> Unit = {}

private class BackNode(val registration: () -> BackRegistration) {
    val children = mutableStateListOf<BackNode>()

    fun resolve(): (() -> Unit)? {
        val current = registration()
        val activeChildren = children.asReversed().filter { it.registration().active }
        val overlays = activeChildren.filter { it.registration().layer != null }
            .sortedByDescending { it.registration().layer?.order }
        for (overlay in overlays) {
            overlay.resolve()?.let { return it }
        }
        val activeBranch = activeChildren.firstOrNull { it.registration().layer == null }
        return activeBranch?.resolve() ?: current.onBackPress ?: ConsumeBack.takeIf {
            current.layer?.modal == true
        }
    }
}
