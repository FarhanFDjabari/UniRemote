package dev.djabari.tvremote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.djabari.tvremote.model.RemoteKey

/**
 * Directional pad.
 *
 * Sizes itself from available space rather than a fixed dp — that is the whole reason the
 * layout stays adaptive. Min 160dp so the individual targets never drop under 48dp;
 * max 320dp so it does not become a dinner plate on a tablet.
 *
 * Built from five discrete buttons, not a custom canvas with invisible hit regions:
 * TalkBack has to be able to find and label each direction.
 */
@Composable
fun DpadControl(
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight).coerceIn(160.dp, 320.dp)
        Box(Modifier.size(side)) {
            // TODO(phase2): 5 RemoteButtons positioned in a 3x3 grid; centre = DPAD_CENTER.
            //   Long-press on the four directions starts a repeat ramp (500ms, then 80ms).
            //   Haptic on every press.
            Text("D-pad ${side.value.toInt()}dp", Modifier.align(Alignment.Center))
        }
    }
}
