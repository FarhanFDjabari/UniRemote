package dev.djabari.tvremote.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

/**
 * The four layout regimes. Every screen branches on this and nothing else — no
 * `Configuration.orientation`, no hardcoded dp breakpoints scattered across features.
 */
enum class RemoteLayout {
    /** Phone portrait. One column, pager between Remote / Touchpad / Keyboard. */
    COMPACT,

    /** Phone landscape or small tablet. Two panes side by side, navigation rail. */
    MEDIUM,

    /** Tablet or unfolded foldable. Three panes. */
    EXPANDED,

    /** Foldable half-opened, hinge horizontal. D-pad above the fold, touchpad below. */
    TABLETOP,
}

@Composable
fun rememberRemoteLayout(isTabletop: Boolean = false): RemoteLayout {
    val info = currentWindowAdaptiveInfo()
    val width = info.windowSizeClass.windowWidthSizeClass
    val height = info.windowSizeClass.windowHeightSizeClass
    return remember(width, height, isTabletop) {
        when {
            isTabletop -> RemoteLayout.TABLETOP
            width == WindowWidthSizeClass.EXPANDED -> RemoteLayout.EXPANDED
            width == WindowWidthSizeClass.MEDIUM -> RemoteLayout.MEDIUM
            height == WindowHeightSizeClass.COMPACT -> RemoteLayout.MEDIUM // phone landscape
            else -> RemoteLayout.COMPACT
        }
    }
}
