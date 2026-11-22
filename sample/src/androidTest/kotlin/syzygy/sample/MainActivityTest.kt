package syzygy.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun incomingProjectionGetsInputAndBackBeforeTheOutgoingProjectionFinishes() {
        waitForText("Open Profile")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Open Profile").performClick()
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Open Settings").fetchSemanticsNodes().isNotEmpty() &&
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.hasEnabledCallbacks() }
        }

        compose.onNodeWithText("Open Profile").assertIsNotEnabled()
        compose.onNodeWithText("Open Settings").assertIsEnabled()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Open Settings").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Open Profile").assertIsEnabled()
    }

    @Test
    fun backDismissesRenameAndSavedNameSurvivesNavigationAndActivityRecreation() {
        waitForText("Hello, Ada")
        compose.onNodeWithText("Open Profile").performClick()
        waitForText("Profile of Ada")
        compose.onNodeWithText("Open Settings").performClick()
        waitForText("Settings")
        compose.onNodeWithText("Rename").performClick()
        waitForText("Rename profile")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Rename profile").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithText("Rename").performClick()
        waitForText("Rename profile")
        compose.onNode(hasSetTextAction()).performTextReplacement("Grace")

        compose.activityRule.scenario.recreate()

        waitForText("Rename profile")
        compose.onNodeWithText("Grace").assertExists()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Rename profile").fetchSemanticsNodes().isEmpty()
        }

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForText("Profile of Grace")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForText("Hello, Grace")
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
