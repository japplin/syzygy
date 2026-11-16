package syzygy.sample

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** An app-scoped singleton reached by every screen's model — including through graph extensions. */
@Inject
@SingleIn(AppScope::class)
class UserRepository {
    val currentUserName: StateFlow<String> = MutableStateFlow("Ada")
}

/** A Profile-scoped binding, alive only while the Profile aligned unit is. */
@Inject
@SingleIn(ProfileScope::class)
class ProfileAnalytics {
    var screenViews: Int = 0
        private set

    fun screenViewed() {
        screenViews++
    }
}
