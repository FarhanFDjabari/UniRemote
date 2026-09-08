package dev.djabari.tvremote

import androidx.compose.runtime.Composable
import dev.djabari.tvremote.ui.RemoteLayout

/**
 * Adaptive shell. One NavHost; the layout regime decides pane composition, not routing.
 *
 *   COMPACT   pager: Remote | Touchpad | Keyboard, bottom nav
 *   MEDIUM    nav rail + two panes (Remote | Touchpad)
 *   EXPANDED  nav rail + three panes (Status | Remote | Touchpad+Keyboard)
 *   TABLETOP  Remote above the hinge, Touchpad below
 */
@Composable
fun TvRemoteApp(layout: RemoteLayout) {
    // TODO(phase1): NavHost + per-layout scaffolding.
}
