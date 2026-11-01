package com.jamilcpu.homestudio.ui.rate

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jamilcpu.homestudio.FakeServer
import com.jamilcpu.homestudio.bodyText
import com.jamilcpu.homestudio.ui.theme.HomeStudioTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric does not deliver injected touches to the ModalBottomSheet's dialog window, so taps
 * inside the sheet go through the OnClick semantics action instead (the phone smoke check in Task 8
 * covers real touches).
 */
fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction = performSemanticsAction(SemanticsActions.OnClick)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RateSheetHostTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun opensSavesAndCloses() {
        val server = FakeServer().apply { ok("POST", "/api/ratings") { """{"id":12}""" } }
        val controller = RateSheetController()
        var rated = 0
        rule.setContent { HomeStudioTheme { RateSheetHost(controller, server.api()) { rated++ } } }
        rule.onNodeWithTag("rateSheet").assertDoesNotExist()
        controller.open("v1", "How GPUs Work")
        rule.waitForIdle()
        rule.onNodeWithText("How GPUs Work").assertExists()
        rule.onNodeWithTag("liked_true").tap().assertIsSelected()
        rule.onNodeWithTag("sat_9").tap().assertIsSelected()
        rule.onNodeWithTag("rateSave").tap()
        rule.waitUntil(5_000) { rated == 1 }
        assertEquals("""{"video_id":"v1","liked":true,"satisfaction":9}""", server.calls("POST", "/api/ratings").single().bodyText())
        assertNull(controller.target.value)
        rule.onNodeWithTag("rateSheet").assertDoesNotExist()
    }
}
