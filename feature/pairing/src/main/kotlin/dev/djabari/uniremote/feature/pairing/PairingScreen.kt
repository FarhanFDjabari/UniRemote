package dev.djabari.uniremote.feature.pairing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.djabari.uniremote.model.RemoteTarget
import dev.djabari.uniremote.model.TvBrand
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.transport.userMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onOpenGuide: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val connection by viewModel.connection.collectAsState()
    val activeTarget by viewModel.activeTarget.collectAsState()
    val savedTargets by viewModel.savedTargets.collectAsState()
    val pairedDevices by viewModel.pairedBluetoothDevices.collectAsState()
    val discoveredTargets by viewModel.discoveredTargets.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

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
                        is TransportState.Connecting, is TransportState.Preparing -> MaterialTheme.colorScheme.secondaryContainer
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
                                    text = "On your TV: go to Settings > Remotes & Accessories > Add Accessory",
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
                            }
                        }

                        if (connection is TransportState.Connecting || connection is TransportState.Preparing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else if (connection is TransportState.Connected) {
                            OutlinedButton(onClick = { viewModel.disconnect() }) {
                                Text("Disconnect")
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
                    text = "No paired Bluetooth TVs found. Pair your phone with your TV first or add a network TV below.",
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

        // TVs found on the network
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TVs on this network", style = MaterialTheme.typography.titleMedium)
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    TextButton(onClick = { viewModel.scanNetwork() }) {
                        Text("Scan")
                    }
                }
            }
        }

        if (discoveredTargets.isEmpty()) {
            item {
                Text(
                    text = "Scan to find Roku, Samsung and LG TVs over Wi-Fi. Android TVs are best controlled over Bluetooth.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(discoveredTargets) { target ->
                TargetListItem(
                    target = target,
                    isConnected = (connection as? TransportState.Connected)?.target?.id == target.id,
                    onConnect = { viewModel.connect(target) },
                )
            }
        }

        // Add Manual TV button
        item {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing-add-tv"),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Add TV by IP / Brand (Wi-Fi)")
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
    }

    if (showAddDialog) {
        AddTargetDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, brand, ip, mac ->
                viewModel.addManualTarget(name, brand, ip, mac, null)
                showAddDialog = false
            },
        )
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
                    text = target.ipAddress ?: target.bluetoothAddress ?: target.brand.name,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTargetDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, brand: TvBrand, ip: String?, mac: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf(TvBrand.ROKU) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add TV Target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("TV Name (e.g. Living Room TV)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing-tv-name"),
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedBrand.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Brand / Protocol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("pairing-brand"),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        TvBrand.values().forEach { brand ->
                            DropdownMenuItem(
                                text = { Text(brand.name) },
                                onClick = {
                                    selectedBrand = brand
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP Address (e.g. 192.168.1.50)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing-ip-address"),
                )

                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("MAC Address for WoL (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing-mac-address"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, selectedBrand, ip.ifBlank { null }, mac.ifBlank { null }) },
                modifier = Modifier.testTag("pairing-add-and-connect"),
            ) {
                Text("Add & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
