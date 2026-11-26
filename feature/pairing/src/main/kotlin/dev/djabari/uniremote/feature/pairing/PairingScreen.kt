package dev.djabari.uniremote.feature.pairing

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.transport.TransportError
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.transport.userMessage

/** Failures a full in-app reset can clear by re-registering the HID profile from scratch. */
private val resetConnectionErrors = setOf(
    TransportError.HOST_REJECTED,
    TransportError.CONNECTION_LOST,
    TransportError.HID_REGISTRATION_FAILED,
    TransportError.TARGET_UNREACHABLE,
    TransportError.UNKNOWN,
)

/** Failures where the system Bluetooth settings screen is a useful next step. */
private val bluetoothSettingsErrors = setOf(
    TransportError.BLUETOOTH_DISABLED,
    TransportError.NOT_PAIRED,
    TransportError.HOST_REJECTED,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onOpenGuide: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    onQuit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val connection by viewModel.connection.collectAsState()
    val activeTarget by viewModel.activeTarget.collectAsState()
    val savedTargets by viewModel.savedTargets.collectAsState()
    val pairedDevices by viewModel.pairedBluetoothDevices.collectAsState()

    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Active Connection Header Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (connection) {
                        is TransportState.Connected -> MaterialTheme.colorScheme.primaryContainer
                        is TransportState.Connecting,
                        is TransportState.Preparing,
                        is TransportState.Reconnecting -> MaterialTheme.colorScheme.secondaryContainer
                        is TransportState.Failed -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (val state = connection) {
                                    is TransportState.Connected -> "Connected: ${state.target.displayName}"
                                    is TransportState.Connecting -> "Connecting to ${state.target.displayName}..."
                                    is TransportState.Reconnecting -> "Reconnecting to ${state.target.displayName} (${state.attempt}/${state.maxAttempts})..."
                                    is TransportState.AwaitingHost -> "Waiting for TV to connect..."
                                    is TransportState.Preparing -> "Preparing Bluetooth HID..."
                                    is TransportState.Failed -> "Couldn't connect"
                                    is TransportState.Idle -> "No TV Connected"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (connection is TransportState.AwaitingHost) {
                                Text(
                                    text = "Make sure the TV is on and nearby. First time? Pair this phone from the " +
                                        "TV's Bluetooth settings first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            (connection as? TransportState.Failed)?.let { failed ->
                                Text(
                                    text = failed.reason.userMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.retry() },
                                        modifier = Modifier.testTag("pairing-retry"),
                                    ) {
                                        Text("Try again")
                                    }
                                    if (failed.reason in resetConnectionErrors) {
                                        OutlinedButton(
                                            onClick = { viewModel.resetConnection() },
                                            modifier = Modifier.testTag("pairing-reset"),
                                        ) {
                                            Text("Reset connection")
                                        }
                                    }
                                    if (failed.reason in bluetoothSettingsErrors) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                                } catch (_: ActivityNotFoundException) {
                                                    // Some TV-box and OEM builds ship without this screen.
                                                }
                                            },
                                            modifier = Modifier.testTag("pairing-bt-settings"),
                                        ) {
                                            Text("Bluetooth settings")
                                        }
                                    }
                                    if (failed.reason == TransportError.PERMISSION_DENIED) {
                                        OutlinedButton(
                                            onClick = {
                                                context.startActivity(
                                                    Intent(
                                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                        Uri.parse("package:${context.packageName}"),
                                                    ),
                                                )
                                            },
                                            modifier = Modifier.testTag("pairing-app-settings"),
                                        ) {
                                            Text("App settings")
                                        }
                                    }
                                }
                            }
                        }

                        if (
                            connection is TransportState.Connecting ||
                            connection is TransportState.Preparing ||
                            connection is TransportState.Reconnecting
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else if (connection is TransportState.Connected) {
                            OutlinedButton(onClick = { viewModel.disconnect() }) {
                                Text("Disconnect")
                            }
                        }
                        if (connection is TransportState.Reconnecting) {
                            OutlinedButton(onClick = { viewModel.disconnect() }) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }

        // Connection Guide Entry
        item {
            OutlinedButton(
                onClick = onOpenGuide,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing-open-guide"),
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("How to connect your TV")
            }
        }

        // Saved TV Targets
        if (savedTargets.isNotEmpty()) {
            item {
                Text("Saved TVs", style = MaterialTheme.typography.titleMedium)
            }
            items(savedTargets) { target ->
                TargetListItem(
                    target = target,
                    isConnected = (connection as? TransportState.Connected)?.target?.id == target.id,
                    onConnect = { viewModel.connect(target) },
                    onDelete = { viewModel.removeTarget(target.id) },
                )
            }
        }

        // Paired Bluetooth Devices
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Paired Bluetooth Devices", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.refreshBluetoothDevices() }) {
                    Text("Refresh")
                }
            }
        }

        if (pairedDevices.isEmpty()) {
            item {
                Text(
                    text = "No paired Bluetooth TVs found. Pair your phone from the TV's Bluetooth settings first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(pairedDevices) { device ->
                TargetListItem(
                    target = device,
                    isConnected = (connection as? TransportState.Connected)?.target?.id == device.id,
                    onConnect = { viewModel.connect(device) },
                )
            }
        }

        // Open-source Licenses Entry
        item {
            TextButton(
                onClick = onOpenLicenses,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing-open-licenses"),
            ) {
                Text("Open-source licenses")
            }
        }

        // Quit Entry
        item {
            TextButton(
                onClick = onQuit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing-quit"),
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Quit UniRemote")
            }
        }
    }
}

@Composable
private fun TargetListItem(
    target: RemoteTarget,
    isConnected: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onConnect() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (target.bluetoothAddress != null) Icons.Default.Bluetooth else Icons.Default.Tv,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(target.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = target.bluetoothAddress ?: target.brand.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isConnected) {
                Text("Active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            } else {
                OutlinedButton(onClick = onConnect) {
                    Text("Connect")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
