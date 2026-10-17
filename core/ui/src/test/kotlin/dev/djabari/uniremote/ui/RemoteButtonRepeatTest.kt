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
 * A button that is disabled while still held must stop repeating and release its hold.
 *
 * Disabling swaps the pointerInput modifier out, which cancels the gesture coroutine — if
 * the repeat job or the release is only handled on the normal path, keys keep firing at the
 * TV forever, or a held key is never let go.
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

    @Test
    fun `release fires once when the press ends`() {
        var presses = 0
        var releases = 0

        composeRule.setContent {
            RemoteButton(
                onClick = { presses++ },
                onRelease = { releases++ },
                contentDescription = "OK",
            ) { Text("OK") }
        }

        composeRule.onRoot().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertThat(presses).isEqualTo(1)
        assertThat(releases).isEqualTo(0)

        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        assertThat(releases).isEqualTo(1)
    }

    @Test
    fun `release fires when the button is disabled mid-hold`() {
        var releases = 0
        var enabled by mutableStateOf(true)

        composeRule.setContent {
            RemoteButton(
                onClick = {},
                onRelease = { releases++ },
                enabled = enabled,
                contentDescription = "OK",
            ) { Text("OK") }
        }

        composeRule.onRoot().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_000)
        enabled = false
        composeRule.waitForIdle()

        assertThat(releases).isEqualTo(1)
    }
}
