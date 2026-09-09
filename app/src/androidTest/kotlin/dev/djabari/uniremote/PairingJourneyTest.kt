package dev.djabari.uniremote

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.djabari.uniremote.feature.keyboard.KeyboardViewModel
import dev.djabari.uniremote.feature.pairing.PairingViewModel
import dev.djabari.uniremote.feature.remote.RemoteViewModel
import dev.djabari.uniremote.feature.touchpad.TouchpadViewModel
import dev.djabari.uniremote.model.KeyAction
import dev.djabari.uniremote.model.PointerEvent
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.session.DeviceDiscovery
import dev.djabari.uniremote.session.RemoteSession
import dev.djabari.uniremote.session.TargetRepository
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.transport.network.discovery.MdnsDiscovery
import dev.djabari.uniremote.transport.network.discovery.NetworkDiscovery
import dev.djabari.uniremote.ui.RemoteLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-backed happy path for a Wake-on-LAN-only TV: add a MAC-only generic target,
 * connect it, then send its power-on command from the remote screen.
 */
@RunWith(AndroidJUnit4::class)
class PairingJourneyTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var session: JourneyRemoteSession
    private lateinit var repository: TargetRepository
    private lateinit var remoteViewModel: RemoteViewModel
    private lateinit var touchpadViewModel: TouchpadViewModel
    private lateinit var keyboardViewModel: KeyboardViewModel
    private lateinit var pairingViewModel: PairingViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = TargetRepository(context)
        runBlocking { repository.removeTarget(WAKE_ON_LAN_MAC) }

        session = JourneyRemoteSession()
        val discovery = DeviceDiscovery(
            networkDiscovery = NetworkDiscovery(MdnsDiscovery(context)),
            bluetoothAdapter = null,
        )
        remoteViewModel = RemoteViewModel(session)
        touchpadViewModel = TouchpadViewModel(session)
        keyboardViewModel = KeyboardViewModel(session)
        pairingViewModel = PairingViewModel(session, repository, discovery)

        composeRule.setContent {
            UniRemoteApp(
                remoteViewModel = remoteViewModel,
                touchpadViewModel = touchpadViewModel,
                keyboardViewModel = keyboardViewModel,
                pairingViewModel = pairingViewModel,
                layout = RemoteLayout.COMPACT,
            )
        }
    }

    @After
    fun tearDown() {
        runBlocking { repository.removeTarget(WAKE_ON_LAN_MAC) }
    }

    @Test
    fun addMacOnlyGenericTarget_connectAndPowerOn() {
        // The navigation item merges its icon and label, so the icon's description
        // is only reachable in the unmerged tree.
        composeRule.onNodeWithContentDescription("Pairing", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("No TV Connected").assertIsDisplayed()

        composeRule.onNodeWithTag("pairing-add-tv").performClick()
        composeRule.onNodeWithText("Add TV Target").assertIsDisplayed()

        composeRule.onNodeWithTag("pairing-tv-name").performTextInput("Bedroom TV")
        composeRule.onNodeWithTag("pairing-brand").performClick()
        composeRule.onNodeWithText("GENERIC").performClick()
        composeRule.onNodeWithTag("pairing-mac-address").performTextInput(WAKE_ON_LAN_MAC)
        composeRule.onNodeWithTag("pairing-add-and-connect").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            session.connectedTarget?.id == WAKE_ON_LAN_MAC
        }

        val target = requireNotNull(session.connectedTarget)
        assertEquals("Bedroom TV", target.displayName)
        assertEquals(TvBrand.GENERIC, target.brand)
        assertNull(target.ipAddress)
        assertEquals(WAKE_ON_LAN_MAC, target.macAddress)
        composeRule.onNodeWithText("Connected: Bedroom TV").assertIsDisplayed()

        // The navigation item merges its icon and label, so the icon's description
        // is only reachable in the unmerged tree.
        composeRule.onNodeWithContentDescription("Remote", useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription("Power On (Wake-on-LAN)").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            session.lastPressedKey == RemoteKey.POWER_ON
        }
    }

    private class JourneyRemoteSession : RemoteSession {
        private val _state = MutableStateFlow<TransportState>(TransportState.Idle)
        override val state: StateFlow<TransportState> = _state

        private val _capabilities = MutableStateFlow<Set<TransportCapability>>(emptySet())
        override val capabilities: StateFlow<Set<TransportCapability>> = _capabilities

        private val _activeTarget = MutableStateFlow<RemoteTarget?>(null)
        override val activeTarget: StateFlow<RemoteTarget?> = _activeTarget

        @Volatile
        var connectedTarget: RemoteTarget? = null
            private set

        @Volatile
        var lastPressedKey: RemoteKey? = null
            private set

        override suspend fun connect(target: RemoteTarget): Result<Unit> {
            connectedTarget = target
            _activeTarget.value = target
            _capabilities.value = setOf(TransportCapability.POWER_ON)
            _state.value = TransportState.Connected(target)
            return Result.success(Unit)
        }

        override suspend fun reconnect(): Result<Unit> = Result.success(Unit)

        override suspend fun disconnect() {
            connectedTarget = null
            _activeTarget.value = null
            _capabilities.value = emptySet()
            _state.value = TransportState.Idle
        }

        override suspend fun press(key: RemoteKey, action: KeyAction): Result<Unit> {
            if (action == KeyAction.TAP) {
                lastPressedKey = key
            }
            return Result.success(Unit)
        }

        override suspend fun type(text: String): Result<Unit> = Result.success(Unit)

        override suspend fun pointer(event: PointerEvent): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val WAKE_ON_LAN_MAC = "02:00:00:00:00:01"
    }
}
