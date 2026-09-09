package dev.djabari.uniremote.feature.touchpad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.djabari.uniremote.transport.TransportCapability
import dev.djabari.uniremote.transport.TransportState
import dev.djabari.uniremote.ui.RemoteButton
import dev.djabari.uniremote.ui.TouchpadSurface

@Composable
fun TouchpadScreen(
    viewModel: TouchpadViewModel,
    modifier: Modifier = Modifier,
) {
    val connection by viewModel.connection.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()

    val isConnected = connection is TransportState.Connected
    val isSupported = capabilities.contains(TransportCapability.POINTER)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isSupported) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "The connected transport or TV does not support pointer mouse navigation. Bluetooth HID is required.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Touchpad Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            TouchpadSurface(
                onDelta = { viewModel.onPointerDelta(it) },
                onTap = { viewModel.onTap() },
                onSecondaryTap = { viewModel.onSecondaryTap() },
                onScroll = { viewModel.onScroll(it) },
                sensitivity = sensitivity,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Physical Click Buttons Fallback
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RemoteButton(
                onClick = { viewModel.onTap() },
                enabled = isConnected && isSupported,
                contentDescription = "Left Click",
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text("Left Click", style = MaterialTheme.typography.titleMedium)
            }

            RemoteButton(
                onClick = { viewModel.onSecondaryTap() },
                enabled = isConnected && isSupported,
                contentDescription = "Right Click",
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) {
                Text("Right Click", style = MaterialTheme.typography.titleMedium)
            }
        }

        // Sensitivity Control
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Speed", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = sensitivity,
                onValueChange = { viewModel.setSensitivity(it) },
                valueRange = 0.5f..3.5f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
