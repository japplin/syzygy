package syzygy.navigation

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackHostTest {
    @Test
    fun backUsesTheSelectedSiblingAndFallsThroughToItsParent() = transformTest {
        val host = BackHost()
        val actions = mutableListOf<String>()
        var detailFocused by mutableStateOf(true)
        var detailCanGoBack by mutableStateOf(true)

        transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
            host.Content {
                BackScope(onBackPress = { actions += "parent" }) {
                    BackScope(active = detailFocused) {
                        BackScope(onBackPress = if (detailCanGoBack) ({ actions += "detail" }) else null) {}
                    }
                    BackScope(active = !detailFocused, onBackPress = { actions += "list" }) {
                        BackOverlay(order = 0, active = detailFocused, onBackPress = { actions += "outgoing" }) {}
                    }
                }
            }
        }

        advance()
        assertTrue(host.canHandleBack)
        assertTrue(host.dispatchBack())

        detailCanGoBack = false
        advance()
        assertTrue(host.dispatchBack())

        detailFocused = false
        advance()
        assertTrue(host.dispatchBack())
        assertEquals(listOf("detail", "parent", "list"), actions)
    }

    @Test
    fun backFollowsOverlayOrderAndStopsAtAModalWithoutAnAction() = transformTest {
        val host = BackHost()
        val actions = mutableListOf<String>()
        var topOrder by mutableStateOf(2)
        var blockingModal by mutableStateOf(false)

        transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
            host.Content {
                BackScope(onBackPress = { actions += "screen" }) {}
                BackOverlay(order = topOrder, onBackPress = { actions += "first" }) {}
                BackOverlay(order = 1, onBackPress = { actions += "second" }) {}
                BackOverlay(order = 3, modal = blockingModal) {}
            }
        }

        advance()
        assertTrue(host.dispatchBack())

        topOrder = 0
        advance()
        assertTrue(host.dispatchBack())

        blockingModal = true
        advance()
        assertTrue(host.canHandleBack)
        assertTrue(host.dispatchBack())
        assertEquals(listOf("first", "second"), actions)
    }

    @Test
    fun removingAScopeUnregistersItAndRecompositionUsesTheLatestCallback() = transformTest {
        val host = BackHost()
        val actions = mutableListOf<String>()
        var shown by mutableStateOf(true)
        var label by mutableStateOf("old")

        transform(initialValue = Unit, scope = backgroundScope, dispatcher = testDispatcher) {
            host.Content {
                if (shown) {
                    val current = label
                    BackScope(onBackPress = { actions += current }) {}
                }
            }
        }

        advance()
        assertTrue(host.canHandleBack)

        label = "new"
        advance()
        assertTrue(host.dispatchBack())

        shown = false
        advance()
        assertFalse(host.canHandleBack)
        assertFalse(host.dispatchBack())
        assertEquals(listOf("new"), actions)
    }
}
