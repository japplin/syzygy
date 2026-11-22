package syzygy.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    @Test
    fun repeatedRoutesHaveDistinctEntriesAndOnlyTheCurrentEntryCanNavigate() {
        val navigator = Navigator("home")
        val root = navigator.current
        assertFalse(navigator.canPop)
        assertFalse(navigator.pop())

        assertTrue(navigator.push("home", from = root))
        val repeated = navigator.current
        assertNotSame(root, repeated)
        assertTrue(navigator.canPop)

        assertFalse(navigator.pop(from = root))
        assertFalse(navigator.push("settings", from = root))
        assertFalse(navigator.pop(from = Navigator("home").current))
        assertSame(repeated, navigator.current)

        assertTrue(navigator.pop(from = repeated))
        assertSame(root, navigator.current)
        assertFalse(navigator.canPop)
    }
}
