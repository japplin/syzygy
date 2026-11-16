package syzygy.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import syzygy.LocalRetainRegistry
import syzygy.Lifetime
import syzygy.MutableRetainRegistry
import syzygy.RetainRegistry

/**
 * The Nav3 integration: hosts one aligned unit per back-stack entry and ties its retention to the
 * entry's lifecycle — kept while the entry is on the stack (across configuration change), retired
 * exactly when the entry is popped. This is the real integration work; the core stays
 * navigation-agnostic (IMPLEMENTATION_PLAN §4, §5.4).
 *
 * Mechanism: each entry gets a [MutableRetainRegistry] held in an entry-scoped [ViewModel]. A
 * `ViewModelStore` survives configuration change and is cleared when the entry leaves the stack, so
 * [LocalRetainRegistry] provided from that ViewModel makes every [Lifetime.Retained]/[Lifetime.NavEntry]
 * unit under the entry survive rotation and retire on pop. This uses only what a *non-Nav3* back-stack
 * integration would also need (an entry-scoped retention store), keeping navigation assumptions out of
 * the core (the Nav3 leak test, discipline #2).
 */
class SyzygyRetentionViewModel : ViewModel() {
    val registry: RetainRegistry = MutableRetainRegistry()

    override fun onCleared() {
        // The entry was popped (or the host destroyed): retire every aligned unit it retained.
        (registry as MutableRetainRegistry).retireAll()
    }
}

/**
 * A [NavEntryDecorator] that provides each entry an entry-scoped [LocalRetainRegistry]. List it
 * *inside* (after) [rememberViewModelStoreNavEntryDecorator] so the entry's `ViewModelStore` is the
 * ambient one — [rememberSyzygyNavEntryDecorators] wires the correct order for you.
 */
@Composable
fun <T : Any> rememberSyzygyNavEntryDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator(
        onPop = { /* retirement is driven by the entry ViewModel's onCleared */ },
        decorate = { entry -> SyzygyEntryHost(entry) },
    )
}

@Composable
private fun <T : Any> SyzygyEntryHost(entry: NavEntry<T>) {
    val retention: SyzygyRetentionViewModel = viewModel()
    CompositionLocalProvider(LocalRetainRegistry provides retention.registry) {
        entry.Content()
    }
}

/**
 * The decorator stack for a Syzygy-hosted [androidx.navigation3.ui.NavDisplay]: saveable state, then
 * an entry-scoped `ViewModelStore`, then Syzygy's per-entry retain registry (which relies on that
 * store). Pass this as `entryDecorators`.
 */
@Composable
fun <T : Any> rememberSyzygyNavEntryDecorators(): List<NavEntryDecorator<T>> = listOf(
    rememberSaveableStateHolderNavEntryDecorator(),
    rememberViewModelStoreNavEntryDecorator(),
    rememberSyzygyNavEntryDecorator(),
)
