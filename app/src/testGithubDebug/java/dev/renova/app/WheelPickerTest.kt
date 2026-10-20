package dev.renova.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.ui.WheelPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WheelPickerTest {
    @get:Rule val compose = createComposeRule()

    private var picked = -1

    private fun show(start: Int) {
        compose.setContent {
            var value by remember { mutableIntStateOf(start) }
            WheelPicker((0..55 step 5).toList(), value, onSelected = { value = it; picked = it }, modifier = Modifier.testTag("roda"))
        }
    }

    @Test fun tapSelects() {
        show(15)
        compose.onNodeWithText("20").performClick()
        compose.waitForIdle()
        assertEquals(20, picked)
    }

    @Test fun swipeSelectsALaterValue() {
        show(15)
        compose.onNodeWithTag("roda").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("escolheu $picked", picked > 15 && picked % 5 == 0)
    }
}
