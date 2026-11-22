package syzygy.navigation

import androidx.compose.runtime.mutableStateListOf

/** A visit to a route. Use the entry itself as a key when rendering or aligning it. */
class NavEntry<out Route : Any> internal constructor(val route: Route)

/** An optional, observable back stack. Mutate it on the thread that owns navigation state. */
class Navigator<Route : Any>(initialRoute: Route) {
    private val entries = mutableStateListOf(NavEntry(initialRoute))

    val current: NavEntry<Route> get() = entries.last()
    val canPop: Boolean get() = entries.size > 1

    /** Pushes [route] if [from] is still the current entry. */
    fun push(route: Route, from: NavEntry<Route> = current): Boolean {
        if (current !== from) return false
        entries.add(NavEntry(route))
        return true
    }

    /** Pops one entry if [from] is still current and the stack has somewhere to go. */
    fun pop(from: NavEntry<Route> = current): Boolean {
        if (current !== from || !canPop) return false
        entries.removeAt(entries.lastIndex)
        return true
    }
}
