package dev.djabari.uniremote.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A repeatable button that is disabled while still held must stop repeating.
 *
 * Disabling swaps the pointerInput modifier out, which cancels the gesture coroutine — if
 * the repeat job is only cancelled on the normal path, it survives and keeps firing keys at
 * the TV forever.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RemoteButtonRepeatTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `repeat stops when the button is disabled mid-press`() {
        var clicks = 0
        var enabled by mutableStateOf(true)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            RemoteButton(
                onClick = { clicks++ },
                enabled = enabled,
                repeatable = true,
                contentDescription = "Volume up",
            ) { Text("+") }
        }

        composeRule.onRoot().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_000) // past the 500ms ramp, into repeats
        assertThat(clicks).isGreaterThan(1)

        enabled = false
        composeRule.mainClock.advanceTimeBy(500)
        val afterDisable = clicks

        composeRule.mainClock.advanceTimeBy(2_000)

        assertThat(clicks).isEqualTo(afterDisable)
    }
}
