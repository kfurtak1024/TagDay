package dev.krfu.tagday.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.krfu.tagday.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationGestureInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun daySwipeUp_opensWeek_andSwipeDown_returnsToDay() {
        composeRule.onNodeWithTag("day_screen").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("week_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("week_screen").performTouchInput { swipeDown() }
        composeRule.onNodeWithText("No tags for this day").assertIsDisplayed()
    }

    @Test
    fun weekAndMonthVerticalSwipes_followReversedDirectionMapping() {
        composeRule.onNodeWithTag("day_screen").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("week_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("week_screen").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("month_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("month_swipe_area").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("year_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("year_screen").performTouchInput { swipeDown() }
        composeRule.onNodeWithTag("month_screen").assertIsDisplayed()
    }
}
