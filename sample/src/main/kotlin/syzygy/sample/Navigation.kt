package syzygy.sample

import androidx.compose.runtime.mutableStateListOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

enum class Destination { Home, Profile, Settings }

data class Entry(val id: Long, val destination: Destination)

@Inject
@SingleIn(AppScope::class)
class Navigator {
    private val backStack = mutableStateListOf(Entry(0, Destination.Home))
    private var nextId = 1L

    val current: Entry get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    fun goTo(from: Long, destination: Destination) {
        if (current.id == from) backStack.add(Entry(nextId++, destination))
    }

    fun back(from: Long) {
        if (current.id == from && canGoBack) backStack.removeAt(backStack.lastIndex)
    }
}
