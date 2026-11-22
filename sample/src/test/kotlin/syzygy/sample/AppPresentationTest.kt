package syzygy.sample

import androidx.appstate.transform.testing.transformTest
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test
import syzygy.projection.ProjectionHost

class AppPresentationTest {
    @Test
    fun returningHomeStartsANewTimer() = transformTest {
        val presentation = createAppPresentation(ProjectionHost(), backgroundScope, testDispatcher)

        advance()
        val home = checkNotNull(presentation.value).screen as HomeState
        assertEquals("Hello, Ada", home.greeting)
        assertEquals(0, home.secondsOnScreen)

        advanceTimeBy(1.seconds)
        advance()
        val updatedHome = checkNotNull(presentation.value).screen as HomeState
        assertEquals(1, updatedHome.secondsOnScreen)

        updatedHome.onOpenProfile()
        advance()
        val profile = checkNotNull(presentation.value)
        assertEquals(Destination.Profile, profile.entry.route)

        checkNotNull(profile.onBack).invoke()
        advance()
        val returnedHome = checkNotNull(presentation.value).screen as HomeState
        assertEquals(0, returnedHome.secondsOnScreen)
    }
}
