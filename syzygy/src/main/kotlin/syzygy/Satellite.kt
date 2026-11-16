package syzygy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CoroutineScope

/** A unit of work whose lifetime belongs to a composed Syzygy value. */
interface Satellite {
    /** Starts this satellite in [scope], which is cancelled when its owning child leaves the tree. */
    fun launch(scope: CoroutineScope)
}

/** An optional capability for a child value that owns [satellites]. */
interface SatelliteRegistry {
    /** Satellites to launch while this registry-owning child is composed. */
    val satellites: Set<Satellite>
}

/** Launches [SatelliteRegistry.satellites] in the scope owned by their child composition group. */
object SatelliteAlignment : Alignment {
    @Composable
    override fun align(value: Any, scope: CoroutineScope) {
        if (value is SatelliteRegistry) {
            DisposableEffect(value, scope) {
                value.launchAll(scope)
                onDispose { }
            }
        }
    }
}

internal fun SatelliteRegistry.launchAll(scope: CoroutineScope) {
    satellites.forEach { satellite -> satellite.launch(scope) }
}
