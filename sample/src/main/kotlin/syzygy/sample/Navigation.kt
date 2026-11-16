package syzygy.sample

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The three destinations of the sample's Home → Profile → Settings flow. */
sealed interface Destination
data object HomeKey : Destination
data object ProfileKey : Destination
data object SettingsKey : Destination

/**
 * The navigation surface models depend on, so navigation decisions remain in testable models rather
 * than in the UI.
 */
interface Navigator {
    val backStack: SnapshotStateList<Destination>
    fun goTo(key: Destination)
    fun back(): Boolean
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NavigatorImpl : Navigator {
    override val backStack: SnapshotStateList<Destination> = mutableStateListOf(HomeKey)

    override fun goTo(key: Destination) {
        backStack.add(key)
    }

    override fun back(): Boolean =
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            true
        } else {
            false
        }
}
