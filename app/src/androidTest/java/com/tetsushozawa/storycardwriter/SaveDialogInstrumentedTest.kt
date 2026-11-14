package com.tetsushozawa.storycardwriter

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class SaveDialogInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<WriterActivity>()

    @Test
    fun newSave_requiresNonBlankNameAndCancelClosesWithoutCreatingDocument() {
        composeRule.onNodeWithTag("save-current-button")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("save-name-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("save-with-timestamp").assertIsDisplayed()
        composeRule.onNodeWithTag("save-name-input").performTextInput("秋の夕暮れ")
        composeRule.onNodeWithTag("save-name-confirm").assertIsEnabled()
        composeRule.onAllNodesWithTag("save-with-timestamp").assertCountEquals(0)

        composeRule.onNodeWithTag("save-name-cancel").performClick()
        composeRule.onAllNodesWithTag("save-name-input").assertCountEquals(0)
    }
}
