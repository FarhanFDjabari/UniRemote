package dev.djabari.uniremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.djabari.uniremote.model.RemoteKey

/**
 * Directional pad.
 *
 * Sizes itself from available space (min 160dp, max 320dp) with 5 discrete buttons
 * positioned in an ergonomic layout, providing full TalkBack accessibility,
 * haptic clicks, and long-press repeat ramp.
 */
@Composable
fun DpadControl(
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight).coerceIn(160.dp, 320.dp)
        val buttonSize = side / 3.2f

        Box(
            modifier = Modifier
                .size(side)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(8.dp),
        ) {
            // UP
            RemoteButton(
                onClick = { onKey(RemoteKey.DPAD_UP) },
                enabled = enabled,
                repeatable = true,
                contentDescription = "D-pad Up",
                shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(width = buttonSize * 1.4f, height = buttonSize)
                    .align(Alignment.TopCenter),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(32.dp))
            }

            // DOWN
            RemoteButton(
                onClick = { onKey(RemoteKey.DPAD_DOWN) },
                enabled = enabled,
                repeatable = true,
                contentDescription = "D-pad Down",
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 40.dp, bottomEnd = 40.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(width = buttonSize * 1.4f, height = buttonSize)
                    .align(Alignment.BottomCenter),
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(32.dp))
            }

            // LEFT
            RemoteButton(
                onClick = { onKey(RemoteKey.DPAD_LEFT) },
                enabled = enabled,
                repeatable = true,
                contentDescription = "D-pad Left",
                shape = RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp, topEnd = 12.dp, bottomEnd = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(width = buttonSize, height = buttonSize * 1.4f)
                    .align(Alignment.CenterStart),
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(32.dp))
            }

            // RIGHT
            RemoteButton(
                onClick = { onKey(RemoteKey.DPAD_RIGHT) },
                enabled = enabled,
                repeatable = true,
                contentDescription = "D-pad Right",
                shape = RoundedCornerShape(topEnd = 40.dp, bottomEnd = 40.dp, topStart = 12.dp, bottomStart = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(width = buttonSize, height = buttonSize * 1.4f)
                    .align(Alignment.CenterEnd),
            ) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(32.dp))
            }

            // CENTER (OK)
            RemoteButton(
                onClick = { onKey(RemoteKey.DPAD_CENTER) },
                enabled = enabled,
                repeatable = false,
                contentDescription = "Select or OK",
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(buttonSize * 1.1f)
                    .align(Alignment.Center),
            ) {
                Text(
                    text = "OK",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
