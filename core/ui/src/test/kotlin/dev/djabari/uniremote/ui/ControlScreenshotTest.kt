package dev.djabari.uniremote.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.djabari.uniremote.ui.theme.UniRemoteTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the shared control library. Record with
 *   ./gradlew :core:ui:recordRoborazziDebug
 * and check them with
 *   ./gradlew :core:ui:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class ControlScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun golden(name: String) = "src/test/roborazzi/$name.png"

    @Test
    fun remoteButton_enabledAndDisabled() {
        composeRule.setContent {
            UniRemoteTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RemoteButton(onClick = {}, contentDescription = "Enabled Button") {
                        Text("OK")
                    }
                    RemoteButton(onClick = {}, enabled = false, contentDescription = "Disabled Button") {
                        Text("Mute")
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(golden("RemoteButton_enabled_disabled"))
    }

    @Test
    fun remoteButton_singleEnabled_andPressed() {
        val interactionSource = MutableInteractionSource()
        composeRule.setContent {
            UniRemoteTheme {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                    RemoteButton(
                        onClick = {},
                        contentDescription = "Pressable Button",
                        interactionSource = interactionSource,
                    ) {
                        Text("OK")
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage(golden("RemoteButton_single_enabled"))

        // Drive the pressed state through the interaction source: deterministic and
        // independent of the touch pipeline.
        val press = PressInteraction.Press(Offset(100f, 100f))
        runBlocking { interactionSource.emit(press) }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(golden("RemoteButton_pressed"))
        runBlocking { interactionSource.emit(PressInteraction.Release(press)) }
        composeRule.waitForIdle()
    }

    @Test
    fun dpadControl_enabled() {
        composeRule.setContent {
            UniRemoteTheme {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(340.dp)) {
                    DpadControl(onKey = {})
                }
            }
        }
        composeRule.onRoot().captureRoboImage(golden("DpadControl_enabled"))
    }

    @Test
    fun dpadControl_disabled() {
        composeRule.setContent {
            UniRemoteTheme {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(340.dp)) {
                    DpadControl(onKey = {}, enabled = false)
                }
            }
        }
        composeRule.onRoot().captureRoboImage(golden("DpadControl_disabled"))
    }
}
