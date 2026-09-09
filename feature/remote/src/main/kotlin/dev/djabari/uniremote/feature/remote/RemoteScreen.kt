package dev.djabari.uniremote.feature.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.ui.DpadControl
import dev.djabari.uniremote.ui.RemoteButton

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier,
) {
    val connection by viewModel.connection.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val isConnected = connection is TransportState.Connected
    var showNumpad by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Bar Controls: Power, Numpad Toggle, Info/Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Power Off
            RemoteButton(
                onClick = { viewModel.onKeyTap(RemoteKey.POWER_OFF) },
                enabled = isConnected && capabilities.contains(TransportCapability.POWER_OFF),
                contentDescription = "Power Off",
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
            }

            // Numpad Toggle
            if (capabilities.contains(TransportCapability.NUMPAD)) {
                RemoteButton(
                    onClick = { showNumpad = !showNumpad },
                    enabled = isConnected,
                    contentDescription = "Toggle Number Pad",
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (showNumpad) "Hide 123" else "123")
                }
            }

            // Power On (WoL - only available when transport supports POWER_ON)
            if (capabilities.contains(TransportCapability.POWER_ON)) {
                RemoteButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.POWER_ON) },
                    contentDescription = "Power On (Wake-on-LAN)",
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) {
                    Text("ON")
                }
            }
        }

        // Expandable Numpad
        AnimatedVisibility(
            visible = showNumpad && capabilities.contains(TransportCapability.NUMPAD),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            NumpadSection(
                onKey = { viewModel.onKeyTap(it) },
                enabled = isConnected,
            )
        }

        // Navigation Row: Back, Home, Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteButton(
                onClick = { viewModel.onKeyTap(RemoteKey.BACK) },
                enabled = isConnected,
                contentDescription = "Back",
                shape = CircleShape,
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }

            RemoteButton(
                onClick = { viewModel.onKeyTap(RemoteKey.HOME) },
                enabled = isConnected,
                contentDescription = "Home",
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
            }

            RemoteButton(
                onClick = { viewModel.onKeyTap(RemoteKey.MENU) },
                enabled = isConnected,
                contentDescription = "Menu",
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Menu, contentDescription = null)
            }
        }

        // Central D-pad Control
        DpadControl(
            onKey = { viewModel.onKeyTap(it) },
            enabled = isConnected && capabilities.contains(TransportCapability.DPAD),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )

        // Volume & Channel Controls
        if (capabilities.contains(TransportCapability.VOLUME) || capabilities.contains(TransportCapability.CHANNEL)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Volume Column
                if (capabilities.contains(TransportCapability.VOLUME)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RemoteButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.VOLUME_UP) },
                            enabled = isConnected,
                            repeatable = true,
                            contentDescription = "Volume Up",
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                        }
                        RemoteButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.MUTE) },
                            enabled = isConnected,
                            contentDescription = "Mute",
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Icon(Icons.Default.VolumeMute, contentDescription = null)
                        }
                        RemoteButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.VOLUME_DOWN) },
                            enabled = isConnected,
                            repeatable = true,
                            contentDescription = "Volume Down",
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        ) {
                            Icon(Icons.Default.VolumeDown, contentDescription = null)
                        }
                    }
                }

                // Channel Column
                if (capabilities.contains(TransportCapability.CHANNEL)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RemoteButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.CHANNEL_UP) },
                            enabled = isConnected,
                            repeatable = true,
                            contentDescription = "Channel Up",
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                        ) {
                            Text("CH +")
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                        RemoteButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.CHANNEL_DOWN) },
                            enabled = isConnected,
                            repeatable = true,
                            contentDescription = "Channel Down",
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        ) {
                            Text("CH -")
                        }
                    }
                }
            }
        }

        // Media Playback Controls
        if (capabilities.contains(TransportCapability.MEDIA_KEYS)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.PREV_TRACK) },
                    enabled = isConnected,
                    contentDescription = "Previous Track",
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null)
                }

                RemoteButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.REWIND) },
                    enabled = isConnected,
                    repeatable = true,
                    contentDescription = "Rewind",
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = null)
                }

                RemoteButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.PLAY_PAUSE) },
                    enabled = isConnected,
                    contentDescription = "Play or Pause",
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                }

                RemoteButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.FAST_FORWARD) },
                    enabled = isConnected,
                    repeatable = true,
                    contentDescription = "Fast Forward",
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null)
                }

                RemoteButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.NEXT_TRACK) },
                    enabled = isConnected,
                    contentDescription = "Next Track",
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun NumpadSection(
    onKey: (RemoteKey) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf(RemoteKey.NUM_1 to "1", RemoteKey.NUM_2 to "2", RemoteKey.NUM_3 to "3"),
        listOf(RemoteKey.NUM_4 to "4", RemoteKey.NUM_5 to "5", RemoteKey.NUM_6 to "6"),
        listOf(RemoteKey.NUM_7 to "7", RemoteKey.NUM_8 to "8", RemoteKey.NUM_9 to "9"),
        listOf(null to "", RemoteKey.NUM_0 to "0", null to ""),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { (key, label) ->
                    if (key != null) {
                        RemoteButton(
                            onClick = { onKey(key) },
                            enabled = enabled,
                            contentDescription = "Digit $label",
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Spacer(modifier = Modifier.size(56.dp))
                    }
                }
            }
        }
    }
}
