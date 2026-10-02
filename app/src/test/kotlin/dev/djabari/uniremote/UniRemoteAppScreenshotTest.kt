package dev.djabari.uniremote

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.djabari.uniremote.feature.keyboard.KeyboardViewModel
import dev.djabari.uniremote.feature.pairing.ConnectionGuideScreen
import dev.djabari.uniremote.feature.pairing.PairingViewModel
import dev.djabari.uniremote.feature.remote.RemoteViewModel
import dev.djabari.uniremote.feature.touchpad.TouchpadViewModel
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.session.DeviceDiscovery
import dev.djabari.uniremote.session.TargetRepository
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.transport.network.discovery.MdnsDiscovery
import dev.djabari.uniremote.transport.network.discovery.NetworkDiscovery
import dev.djabari.uniremote.ui.RemoteLayout
import dev.djabari.uniremote.ui.theme.UniRemoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One golden per window class x connection state. The layout regime is injected — in
 * production it comes from a real window + fold posture, which a JVM test cannot reach.
 *
 * Goldens live in src/test/roborazzi; record with
 *   ./gradlew recordRoborazziDebug
 * and check them with
 *   ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UniRemoteAppScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val target = RemoteTarget(
        id = "tv-1",
        displayName = "Living room TV",
        brand = TvBrand.SAMSUNG_TIZEN,
        bluetoothAddress = "AA:BB:CC:DD:EE:FF",
    )

    /** Everything the Bluetooth HID transport claims: the full-control connected state. */
    private val fullHidCapabilities = setOf(
        TransportCapability.DPAD,
        TransportCapability.NUMPAD,
        TransportCapability.VOLUME,
        TransportCapability.CHANNEL,
        TransportCapability.MEDIA_KEYS,
        TransportCapability.POINTER,
        TransportCapability.TEXT_INPUT,
        TransportCapability.POWER_OFF,
    )

    private val session = FakeRemoteSession()

    private fun capture(layout: RemoteLayout, connected: Boolean) {
        if (connected) {
            session.setConnected(target, fullHidCapabilities)
        } else {
            session.setDisconnected()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            UniRemoteApp(
                remoteViewModel = RemoteViewModel(session),
                touchpadViewModel = TouchpadViewModel(session),
                keyboardViewModel = KeyboardViewModel(session),
                pairingViewModel = PairingViewModel(
                    session,
                    TargetRepository(context),
                    DeviceDiscovery(NetworkDiscovery(MdnsDiscovery(context)), null),
                ),
                layout = layout,
            )
        }
        composeRule.onRoot().captureRoboImage(goldenPath(layout, connected))
    }

    private fun goldenPath(layout: RemoteLayout, connected: Boolean) =
        "src/test/roborazzi/UniRemoteApp_${layout.name}_${if (connected) "connected" else "disconnected"}.png"

    @Test
    @Config(qualifiers = "w411dp-h891dp-mdpi")
    fun compact_disconnected() = capture(RemoteLayout.COMPACT, connected = false)

    @Test
    @Config(qualifiers = "w411dp-h891dp-mdpi")
    fun compact_connected() = capture(RemoteLayout.COMPACT, connected = true)

    @Test
    @Config(qualifiers = "w891dp-h411dp-mdpi")
    fun medium_disconnected() = capture(RemoteLayout.MEDIUM, connected = false)

    @Test
    @Config(qualifiers = "w891dp-h411dp-mdpi")
    fun medium_connected() = capture(RemoteLayout.MEDIUM, connected = true)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun expanded_disconnected() = capture(RemoteLayout.EXPANDED, connected = false)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun expanded_connected() = capture(RemoteLayout.EXPANDED, connected = true)

    @Test
    @Config(qualifiers = "w411dp-h891dp-mdpi")
    fun tabletop_disconnected() = capture(RemoteLayout.TABLETOP, connected = false)

    @Test
    @Config(qualifiers = "w411dp-h891dp-mdpi")
    fun tabletop_connected() = capture(RemoteLayout.TABLETOP, connected = true)

    @Test
    @Config(qualifiers = "w411dp-h891dp-mdpi")
    fun connection_guide_compact() {
        composeRule.setContent {
            UniRemoteTheme { ConnectionGuideScreen(onBack = {}) }
        }
        composeRule.onRoot().captureRoboImage("src/test/roborazzi/ConnectionGuide_compact.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-mdpi")
    fun open_source_licenses_compact() {
        composeRule.setContent {
            UniRemoteTheme { OpenSourceLicensesScreen(onBack = {}) }
        }
        composeRule.onRoot().captureRoboImage("src/test/roborazzi/OpenSourceLicenses_compact.png")
    }
}
