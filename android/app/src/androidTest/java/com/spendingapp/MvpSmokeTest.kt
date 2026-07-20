package com.spendingapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MvpSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainTabsOpenWithoutCrash() {
        composeRule.onNodeWithText("Sổ chi tiêu của bạn").assertIsDisplayed()

        composeRule.onNodeWithText("Giao dịch").performClick()
        composeRule.onNodeWithText("Thêm giao dịch").assertIsDisplayed()

        composeRule.onNodeWithText("Hạn mức").performClick()
        composeRule.onNodeWithText("Hạn mức tháng").assertIsDisplayed()

        composeRule.onNodeWithText("Mục tiêu").performClick()
        composeRule.onNodeWithText("Mục tiêu tài chính").assertIsDisplayed()

        composeRule.onNodeWithText("Cài đặt").performClick()
        composeRule.onNodeWithText("Ví & nguồn tiền").assertIsDisplayed()
    }
}
