package edu.cs312.netshift.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigationShowsHonestEmptyStatesWithoutStartingTraffic() {
        if (compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Get started").performClick()
        }
        compose.onNodeWithText("Connection overview").assertIsDisplayed()
        compose.onNodeWithText("Test").performClick()
        compose.onNodeWithText("Start test").assertIsNotEnabled()
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithText("No saved tests").assertIsDisplayed()
        compose.onNodeWithText("Compare networks").performClick()
        compose.onNodeWithText("Compare your connections").assertIsDisplayed()
        compose.onNodeWithText("Open comparisons").performClick()
        compose.onNodeWithText("No comparison yet").assertIsDisplayed()
        compose.onNodeWithText("Handoffs").performClick()
        compose.onNodeWithText("Start tracking").assertIsNotEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Handoff log").assertIsDisplayed()
    }
}
