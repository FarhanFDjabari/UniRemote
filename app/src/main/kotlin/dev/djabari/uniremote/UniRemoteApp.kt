package dev.djabari.uniremote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.djabari.uniremote.feature.keyboard.KeyboardScreen
import dev.djabari.uniremote.feature.keyboard.KeyboardViewModel
import dev.djabari.uniremote.feature.pairing.PairingScreen
import dev.djabari.uniremote.feature.pairing.PairingViewModel
import dev.djabari.uniremote.feature.remote.RemoteScreen
import dev.djabari.uniremote.feature.remote.RemoteViewModel
import dev.djabari.uniremote.feature.touchpad.TouchpadScreen
import dev.djabari.uniremote.feature.touchpad.TouchpadViewModel
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.ui.RemoteLayout
import dev.djabari.uniremote.ui.rememberRemoteLayout
import dev.djabari.uniremote.ui.theme.UniRemoteTheme

enum class RemoteDestination(
    val title: String,
    val icon: ImageVector,
) {
    REMOTE("Remote", Icons.Default.Tv),
    TOUCHPAD("Touchpad", Icons.Default.Mouse),
    KEYBOARD("Keyboard", Icons.Default.Keyboard),
    PAIRING("Pairing", Icons.Default.Bluetooth),
}

/**
 * @param layout The layout regime. Injected so tests and previews can pin a regime; real
 *   callers get the window-derived [rememberRemoteLayout].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniRemoteApp(
    remoteViewModel: RemoteViewModel,
    touchpadViewModel: TouchpadViewModel,
    keyboardViewModel: KeyboardViewModel,
    pairingViewModel: PairingViewModel,
    modifier: Modifier = Modifier,
    layout: RemoteLayout = rememberRemoteLayout(),
) {
    var currentDestination by remember { mutableStateOf(RemoteDestination.REMOTE) }
    val connection by remoteViewModel.connection.collectAsState()

    UniRemoteTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (layout) {
                RemoteLayout.COMPACT -> CompactScaffold(
                    currentDestination = currentDestination,
                    onDestinationSelected = { currentDestination = it },
                    connection = connection,
                    remoteViewModel = remoteViewModel,
                    touchpadViewModel = touchpadViewModel,
                    keyboardViewModel = keyboardViewModel,
                    pairingViewModel = pairingViewModel,
                )
                RemoteLayout.MEDIUM -> MediumScaffold(
                    currentDestination = currentDestination,
                    onDestinationSelected = { currentDestination = it },
                    connection = connection,
                    remoteViewModel = remoteViewModel,
                    touchpadViewModel = touchpadViewModel,
                    keyboardViewModel = keyboardViewModel,
                    pairingViewModel = pairingViewModel,
                )
                RemoteLayout.EXPANDED -> ExpandedScaffold(
                    connection = connection,
                    remoteViewModel = remoteViewModel,
                    touchpadViewModel = touchpadViewModel,
                    keyboardViewModel = keyboardViewModel,
                    pairingViewModel = pairingViewModel,
                )
                RemoteLayout.TABLETOP -> TabletopScaffold(
                    remoteViewModel = remoteViewModel,
                    touchpadViewModel = touchpadViewModel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactScaffold(
    currentDestination: RemoteDestination,
    onDestinationSelected: (RemoteDestination) -> Unit,
    connection: TransportState,
    remoteViewModel: RemoteViewModel,
    touchpadViewModel: TouchpadViewModel,
    keyboardViewModel: KeyboardViewModel,
    pairingViewModel: PairingViewModel,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("UniRemote", style = MaterialTheme.typography.titleMedium)
                        ConnectionStatusBadge(connection)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                RemoteDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination == dest,
                        onClick = { onDestinationSelected(dest) },
                        icon = { Icon(dest.icon, contentDescription = dest.title) },
                        label = { Text(dest.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (currentDestination) {
                RemoteDestination.REMOTE -> RemoteScreen(remoteViewModel)
                RemoteDestination.TOUCHPAD -> TouchpadScreen(touchpadViewModel)
                RemoteDestination.KEYBOARD -> KeyboardScreen(keyboardViewModel)
                RemoteDestination.PAIRING -> PairingScreen(pairingViewModel)
            }
        }
    }
}

@Composable
private fun MediumScaffold(
    currentDestination: RemoteDestination,
    onDestinationSelected: (RemoteDestination) -> Unit,
    connection: TransportState,
    remoteViewModel: RemoteViewModel,
    touchpadViewModel: TouchpadViewModel,
    keyboardViewModel: KeyboardViewModel,
    pairingViewModel: PairingViewModel,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            header = {
                Box(modifier = Modifier.padding(vertical = 12.dp)) {
                    ConnectionStatusBadge(connection)
                }
            },
        ) {
            RemoteDestination.entries.forEach { dest ->
                NavigationRailItem(
                    selected = currentDestination == dest,
                    onClick = { onDestinationSelected(dest) },
                    icon = { Icon(dest.icon, contentDescription = dest.title) },
                    label = { Text(dest.title) },
                )
            }
        }

        // Left Pane: Remote Controls
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            RemoteScreen(remoteViewModel)
        }

        // Right Pane: Active destination or Touchpad
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        ) {
            when (currentDestination) {
                RemoteDestination.TOUCHPAD -> TouchpadScreen(touchpadViewModel)
                RemoteDestination.KEYBOARD -> KeyboardScreen(keyboardViewModel)
                RemoteDestination.PAIRING -> PairingScreen(pairingViewModel)
                RemoteDestination.REMOTE -> TouchpadScreen(touchpadViewModel)
            }
        }
    }
}

@Composable
private fun ExpandedScaffold(
    connection: TransportState,
    remoteViewModel: RemoteViewModel,
    touchpadViewModel: TouchpadViewModel,
    keyboardViewModel: KeyboardViewModel,
    pairingViewModel: PairingViewModel,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Pane 1: Connection status & target management
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("UniRemote", style = MaterialTheme.typography.titleMedium)
                ConnectionStatusBadge(connection)
            }
            PairingScreen(pairingViewModel)
        }

        // Pane 2: D-pad & Remote Keys
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        ) {
            RemoteScreen(remoteViewModel)
        }

        // Pane 3: Touchpad & Quick Keyboard
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
        ) {
            Box(modifier = Modifier.weight(1.2f)) {
                TouchpadScreen(touchpadViewModel)
            }
            Box(modifier = Modifier.weight(0.8f)) {
                KeyboardScreen(keyboardViewModel)
            }
        }
    }
}

@Composable
private fun TabletopScaffold(
    remoteViewModel: RemoteViewModel,
    touchpadViewModel: TouchpadViewModel,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Upper Half: Remote Buttons & D-pad
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            RemoteScreen(remoteViewModel)
        }

        // Lower Half: Touchpad Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        ) {
            TouchpadScreen(touchpadViewModel)
        }
    }
}

@Composable
private fun ConnectionStatusBadge(state: TransportState) {
    val (color, text) = when (state) {
        is TransportState.Connected -> Color(0xFF4CAF50) to "Connected"
        is TransportState.Connecting, is TransportState.Preparing -> Color(0xFFFFC107) to "Connecting"
        is TransportState.AwaitingHost -> Color(0xFF2196F3) to "Waiting for TV"
        is TransportState.Failed -> Color(0xFFF44336) to "Error"
        is TransportState.Idle -> Color(0xFF9E9E9E) to "Idle"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
