package dev.djabari.uniremote.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

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
fun rememberRemoteLayout(isTabletop: Boolean = rememberIsTabletop()): RemoteLayout {
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

/**
 * True while the device is half-opened with a horizontal hinge — the posture where the
 * phone stands on a table and the two halves become separate control surfaces.
 */
@Composable
fun rememberIsTabletop(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isTabletop by remember { mutableStateOf(false) }

    LaunchedEffect(activity) {
        val host = activity ?: return@LaunchedEffect
        WindowInfoTracker.getOrCreate(host)
            .windowLayoutInfo(host)
            .collect { info ->
                isTabletop = info.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .any {
                        it.state == FoldingFeature.State.HALF_OPENED &&
                            it.orientation == FoldingFeature.Orientation.HORIZONTAL
                    }
            }
    }

    return isTabletop
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
