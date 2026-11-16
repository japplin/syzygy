package syzygy

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Something that can be retired — its owned scope cancelled and its Compose runtime torn down.
 * Implemented by the aligned-runtime engine; held by a [RetainRegistry] so retirement can be driven
 * externally (on real removal / nav pop), not merely by composition exit.
 */
interface Retirable {
    fun retire()
}

/**
 * A store of aligned units that outlives a single composition.
 *
 * This is the seam that gives [Lifetime.Retained] its meaning **without the core depending on any
 * platform `retain` API** (IMPLEMENTATION_PLAN §3). A `Remembered` unit lives in the composition and
 * dies with it; a `Retained`/`NavEntry` unit lives here and is retired only when this registry forgets
 * its key. Android layers back this with a config-change-surviving store (retain / `ViewModel`); a
 * plain JVM test backs it with [MutableRetainRegistry] and drives keep/retire by hand — which is
 * exactly how retention is exercised headless in milestone 1.
 */
interface RetainRegistry {
    /** Return the value stored under [key], creating and storing it with [create] on first request. */
    fun <T : Any> getOrCreate(key: Any, create: () -> T): T

    /** Forget [key], retiring its value if it is [Retirable]. */
    fun retire(key: Any)

    /** Forget everything, retiring every [Retirable] value. Simulates the host going away for good. */
    fun retireAll()
}

/** A simple in-memory [RetainRegistry]. Lives wherever you put it; survives exactly as long as you keep the instance. */
class MutableRetainRegistry : RetainRegistry {
    private val store = LinkedHashMap<Any, Any>()

    override fun <T : Any> getOrCreate(key: Any, create: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return store.getOrPut(key, create) as T
    }

    override fun retire(key: Any) {
        (store.remove(key) as? Retirable)?.retire()
    }

    override fun retireAll() {
        // Copy first: retire() may mutate the map via nested units.
        val values = store.values.toList()
        store.clear()
        values.forEach { (it as? Retirable)?.retire() }
    }
}

/**
 * The ambient [RetainRegistry], or `null` when no host has provided one yet.
 *
 * `staticCompositionLocalOf` (not `compositionLocalOf`) because the registry identity does not change
 * once provided, and static locals do not invalidate readers. A `Retained`/`NavEntry` unit is
 * meaningless without a host that decides when it is finally forgotten, so `syzygy()` errors clearly
 * if it reads `null`. `SyzygyContent` (and the nav3 integration) provide one; a headless test provides
 * a [MutableRetainRegistry] it controls.
 */
val LocalRetainRegistry: ProvidableCompositionLocal<RetainRegistry?> = staticCompositionLocalOf { null }
