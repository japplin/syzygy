package syzygy.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Inject
@SingleIn(AppScope::class)
class UserRepository {
    private val name = MutableStateFlow("Ada")
    val currentUserName = name.asStateFlow()

    fun rename(value: String) {
        name.value = value
    }
}

@Inject
@SingleIn(ProfileScope::class)
class ProfileAnalytics {
    var screenViews: Int by mutableIntStateOf(0)
        private set

    fun screenViewed() {
        screenViews++
    }
}
