package dev.djabari.uniremote.feature.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.djabari.uniremote.model.RemoteKey
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.ui.DpadControl
import dev.djabari.uniremote.ui.RemoteButton

/** Widest the remote ever gets — beyond this it centres instead of stretching. */
private val RemoteMaxWidth = 400.dp
private val SectionSpacing = 20.dp

private val RockerWidth = 88.dp
private val RockerSegmentHeight = 56.dp
private val RockerTopShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val RockerMiddleShape = RoundedCornerShape(4.dp)
private val RockerBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

private val MediaButtonSize = 52.dp
private val MediaPrimarySize = 64.dp
private val NumpadButtonSize = 56.dp

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier,
) {
    val connection by viewModel.connection.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val isConnected = connection is TransportState.Connected
    var showNumpad by remember { mutableStateOf(false) }

    val hasNumpad = capabilities.contains(TransportCapability.NUMPAD)
    val hasVolume = capabilities.contains(TransportCapability.VOLUME)
    val hasChannel = capabilities.contains(TransportCapability.CHANNEL)

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        val sectionModifier = Modifier.widthIn(max = RemoteMaxWidth).fillMaxWidth()

        // Power, numpad toggle, wake
        Row(
            modifier = sectionModifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

            if (hasNumpad) {
                RemoteButton(
                    onClick = { showNumpad = !showNumpad },
                    enabled = isConnected,
                    contentDescription = if (showNumpad) "Hide Number Pad" else "Show Number Pad",
                    shape = CircleShape,
                    colors = if (showNumpad) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                ) {
                    Text("123")
                }
            }
        }

        AnimatedVisibility(
            visible = showNumpad && hasNumpad,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            NumpadSection(
                onKey = { viewModel.onKeyTap(it) },
                enabled = isConnected,
                modifier = sectionModifier,
            )
        }

        // Back, Home, Menu
        Row(
            modifier = sectionModifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
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

        DpadControl(
            onKey = { viewModel.onKeyTap(it) },
            enabled = isConnected && capabilities.contains(TransportCapability.DPAD),
            modifier = sectionModifier.padding(vertical = 4.dp),
        )

        // Volume and channel rockers. Fixed segment sizes keep the two columns aligned
        // whichever of them the transport actually offers.
        if (hasVolume || hasChannel) {
            Row(
                modifier = sectionModifier,
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasVolume) {
                    RockerColumn {
                        RockerButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.VOLUME_UP) },
                            enabled = isConnected,
                            contentDescription = "Volume Up",
                            shape = RockerTopShape,
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                        }
                        RockerButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.MUTE) },
                            enabled = isConnected,
                            repeatable = false,
                            contentDescription = "Mute",
                            shape = RockerMiddleShape,
                        ) {
                            Icon(Icons.Default.VolumeOff, contentDescription = null)
                        }
                        RockerButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.VOLUME_DOWN) },
                            enabled = isConnected,
                            contentDescription = "Volume Down",
                            shape = RockerBottomShape,
                        ) {
                            Icon(Icons.Default.VolumeDown, contentDescription = null)
                        }
                    }
                }

                if (hasChannel) {
                    RockerColumn {
                        RockerButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.CHANNEL_UP) },
                            enabled = isConnected,
                            contentDescription = "Channel Up",
                            shape = RockerTopShape,
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                        }
                        RockerLabel("CH")
                        RockerButton(
                            onClick = { viewModel.onKeyTap(RemoteKey.CHANNEL_DOWN) },
                            enabled = isConnected,
                            contentDescription = "Channel Down",
                            shape = RockerBottomShape,
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                }
            }
        }

        if (capabilities.contains(TransportCapability.MEDIA_KEYS)) {
            Row(
                modifier = sectionModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.PREV_TRACK) },
                    enabled = isConnected,
                    contentDescription = "Previous Track",
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null)
                }

                MediaButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.REWIND) },
                    enabled = isConnected,
                    repeatable = true,
                    contentDescription = "Rewind",
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = null)
                }

                RemoteButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.PLAY_PAUSE) },
                    enabled = isConnected,
                    contentDescription = "Play or Pause",
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(MediaPrimarySize),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                }

                MediaButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.FAST_FORWARD) },
                    enabled = isConnected,
                    repeatable = true,
                    contentDescription = "Fast Forward",
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null)
                }

                MediaButton(
                    onClick = { viewModel.onKeyTap(RemoteKey.NEXT_TRACK) },
                    enabled = isConnected,
                    contentDescription = "Next Track",
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun RockerColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.width(RockerWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

@Composable
private fun RockerButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    shape: Shape,
    repeatable: Boolean = true,
    content: @Composable () -> Unit,
) {
    RemoteButton(
        onClick = onClick,
        enabled = enabled,
        repeatable = repeatable,
        contentDescription = contentDescription,
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(RockerSegmentHeight),
        content = content,
    )
}

@Composable
private fun RockerLabel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RockerSegmentHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    repeatable: Boolean = false,
    content: @Composable () -> Unit,
) {
    RemoteButton(
        onClick = onClick,
        enabled = enabled,
        repeatable = repeatable,
        contentDescription = contentDescription,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(MediaButtonSize),
        content = content,
    )
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
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { (key, label) ->
                    if (key != null) {
                        RemoteButton(
                            onClick = { onKey(key) },
                            enabled = enabled,
                            contentDescription = "Digit $label",
                            shape = CircleShape,
                            modifier = Modifier.size(NumpadButtonSize),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Spacer(modifier = Modifier.size(NumpadButtonSize))
                    }
                }
            }
        }
    }
}
