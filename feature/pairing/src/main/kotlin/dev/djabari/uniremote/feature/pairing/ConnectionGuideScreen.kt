package dev.djabari.uniremote.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to connect") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                GuideStepCard(
                    step = 1,
                    title = "Close other keyboard/mouse apps",
                    body = "Android lets only one app at a time act as a Bluetooth keyboard or mouse. " +
                        "Close or force-stop apps like \"Bluetooth Keyboard & Mouse\" before you connect.",
                ) {
                    CloseOtherAppsIllustration()
                }
            }
            item {
                GuideStepCard(
                    step = 2,
                    title = "Pair your phone with the TV",
                    body = "On the TV, open Settings > Remotes & Accessories > Pair accessory. " +
                        "On your phone, open Bluetooth settings so it is visible. Pick your phone on the TV.",
                ) {
                    PairWithTvIllustration()
                }
            }
            item {
                GuideStepCard(
                    step = 3,
                    title = "Confirm the code",
                    body = "The TV and your phone show the same code. Check they match and confirm on both.",
                ) {
                    ConfirmCodeIllustration()
                }
            }
            item {
                GuideStepCard(
                    step = 4,
                    title = "Connect from UniRemote",
                    body = "Open the Pairing tab, tap Refresh, then tap your TV under Paired Bluetooth Devices. " +
                        "The status card turns green when you're connected.",
                ) {
                    ConnectFromAppIllustration()
                }
            }
            item {
                Text("Troubleshooting", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BulletLine(
                            "Remote stopped responding or stuck connecting? Tap Reset connection on the Pairing tab. " +
                                "There's no need to force-stop the app, clear its data or unpair.",
                        )
                        BulletLine(
                            "Phone was paired before? If the TV won't accept the connection, remove the phone " +
                                "in the TV's Bluetooth settings and pair again while UniRemote is open.",
                        )
                        BulletLine(
                            "Android TV and Google TV are controlled over Bluetooth. Over Wi-Fi, UniRemote can " +
                                "only wake them (Wake-on-LAN), which needs the TV's MAC address.",
                        )
                        BulletLine(
                            "Keep the TV within a few metres of your phone with nothing blocking the signal.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    step: Int,
    title: String,
    body: String,
    illustration: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            illustration()
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$step",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
