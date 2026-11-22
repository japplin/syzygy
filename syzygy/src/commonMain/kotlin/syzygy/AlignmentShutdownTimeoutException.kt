package syzygy

import kotlin.time.Duration

/** A retired alignment exceeded its configured shutdown deadline. */
class AlignmentShutdownTimeoutException internal constructor(
    val phase: Phase,
    val timeout: Duration,
    graphName: String,
    key: Any?,
) : IllegalStateException("Syzygy alignment $graphName (key=$key) timed out after $timeout ${phase.activity}.") {
    enum class Phase(internal val activity: String) {
        WaitingForHolds("waiting for retained users or child graphs"),
        Closing("running graph cleanup"),
    }
}
