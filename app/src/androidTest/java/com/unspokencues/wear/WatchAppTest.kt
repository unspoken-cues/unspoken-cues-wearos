package com.unspokencues.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.unspokencues.wear.model.*
import com.unspokencues.wear.ui.WatchApp
import org.junit.Rule
import org.junit.Test

class WatchAppTest {
    @get:Rule val compose = createComposeRule()
    private fun launch() {
        compose.setContent {
            var state by remember { mutableStateOf(WatchState(CueStatus.YELLOW,
                SharedProfile("Alex", "0123456789abcdef0123456789abcdef", true), receivedAt = 1)) }
            WatchApp(state, onStateChange = { state = it })
        }
    }
    @Test fun swipeDoesNotChangeLockedStatus() {
        launch()
        compose.onNodeWithTag("status").performTouchInput { swipeLeft(); swipeRight() }
        compose.onNodeWithText("Yellow").assertIsDisplayed()
        compose.onNodeWithText("Color locked").assertIsDisplayed()
    }
    @Test fun sharingWorksWhileLockedAndTimesOutToSameColor() {
        launch()
        compose.onNodeWithText("Share profile").performScrollTo().performClick()
        compose.onNodeWithTag("profile-qr").assertExists()
        compose.mainClock.advanceTimeBy(30_100)
        compose.onNodeWithText("Yellow").assertIsDisplayed()
        compose.onNodeWithText("Color locked").assertExists()
    }
    @Test fun colorChangeRequiresConfirmationAndRelocks() {
        launch()
        compose.onNodeWithText("Color locked").performScrollTo().performClick()
        compose.onNodeWithText("Unlock color").performScrollTo().performClick()
        compose.onNodeWithText("Red").performScrollTo().performClick()
        compose.onNodeWithText("Red").assertIsDisplayed()
        compose.onNodeWithText("Color locked").assertExists()
    }
}
