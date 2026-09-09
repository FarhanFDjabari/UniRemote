package dev.djabari.uniremote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UniRemote button with haptic feedback, long-press repeat ramp (500ms then 80ms),
 * and accessibility TalkBack metadata.
 */
@Composable
fun RemoteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    repeatable: Boolean = false,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    /**
     * Optional externally-owned interaction source, mirroring Material's Button API.
     * Lets tests/previews drive the pressed state deterministically; null keeps the
     * internal one.
     */
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ownedInteractionSource = remember { MutableInteractionSource() }
    val effectiveInteractionSource = interactionSource ?: ownedInteractionSource
    val isPressed by effectiveInteractionSource.collectIsPressedAsState()

    val containerColor = if (enabled) {
        if (isPressed) colors.containerColor.copy(alpha = 0.8f) else colors.containerColor
    } else {
        colors.disabledContainerColor
    }
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor

    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                this.role = Role.Button
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(repeatable, enabled) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val press = PressInteraction.Press(down.position)
                            // tryEmit: the gesture scope is restricted, and unlike emit this
                            // cannot suspend behind a slow collector and stall the gesture.
                            effectiveInteractionSource.tryEmit(press)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()

                            var repeatJob: Job? = null
                            if (repeatable) {
                                repeatJob = scope.launch {
                                    delay(500)
                                    while (isActive) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onClick()
                                        delay(80)
                                    }
                                }
                            }

                            val upOrCancellation = waitForUpOrCancellation()
                            repeatJob?.cancel()
                            if (upOrCancellation != null) {
                                effectiveInteractionSource.tryEmit(PressInteraction.Release(press))
                                upOrCancellation.consume()
                            } else {
                                effectiveInteractionSource.tryEmit(PressInteraction.Cancel(press))
                            }
                        }
                    }
                } else Modifier
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                Box(
                    modifier = Modifier.padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
    }
}
