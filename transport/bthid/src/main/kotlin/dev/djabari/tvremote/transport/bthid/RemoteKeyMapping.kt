package dev.djabari.tvremote.transport.bthid

import dev.djabari.tvremote.model.RemoteKey

/**
 * How each [RemoteKey] reaches a TV over HID.
 *
 * The choice of page matters more than it looks:
 *  - Volume goes over the **consumer** page. Keyboard volume keycodes are widely ignored by TVs.
 *  - D-pad goes over the **keyboard** page as plain arrows. Universally understood.
 *  - Back/Home go over the consumer page (AC Back / AC Home); Escape is the documented
 *    fallback for Tizen, applied by [BACK_FALLBACK].
 */
sealed interface HidBinding {
    data class Keyboard(val usage: Int, val modifiers: Int = KeyboardModifier.NONE) : HidBinding
    data class Consumer(val usage: Int) : HidBinding
}

object RemoteKeyMapping {

    /** Sent in addition to the primary binding when the TV brand is known to need it. */
    val BACK_FALLBACK = HidBinding.Keyboard(KeyboardUsage.ESCAPE)

    private val map: Map<RemoteKey, HidBinding> = mapOf(
        RemoteKey.DPAD_UP to HidBinding.Keyboard(KeyboardUsage.UP_ARROW),
        RemoteKey.DPAD_DOWN to HidBinding.Keyboard(KeyboardUsage.DOWN_ARROW),
        RemoteKey.DPAD_LEFT to HidBinding.Keyboard(KeyboardUsage.LEFT_ARROW),
        RemoteKey.DPAD_RIGHT to HidBinding.Keyboard(KeyboardUsage.RIGHT_ARROW),
        RemoteKey.DPAD_CENTER to HidBinding.Keyboard(KeyboardUsage.ENTER),
        RemoteKey.BACK to HidBinding.Consumer(ConsumerUsage.AC_BACK),
        RemoteKey.HOME to HidBinding.Consumer(ConsumerUsage.AC_HOME),
        RemoteKey.MENU to HidBinding.Keyboard(KeyboardUsage.APPLICATION),

        RemoteKey.VOLUME_UP to HidBinding.Consumer(ConsumerUsage.VOLUME_UP),
        RemoteKey.VOLUME_DOWN to HidBinding.Consumer(ConsumerUsage.VOLUME_DOWN),
        RemoteKey.MUTE to HidBinding.Consumer(ConsumerUsage.MUTE),

        RemoteKey.CHANNEL_UP to HidBinding.Consumer(ConsumerUsage.CHANNEL_UP),
        RemoteKey.CHANNEL_DOWN to HidBinding.Consumer(ConsumerUsage.CHANNEL_DOWN),

        RemoteKey.NUM_0 to HidBinding.Keyboard(KeyboardUsage.NUM_0),
        RemoteKey.NUM_1 to HidBinding.Keyboard(KeyboardUsage.NUM_1),
        RemoteKey.NUM_2 to HidBinding.Keyboard(KeyboardUsage.NUM_1 + 1),
        RemoteKey.NUM_3 to HidBinding.Keyboard(KeyboardUsage.NUM_1 + 2),
        RemoteKey.NUM_4 to HidBinding.Keyboard(KeyboardUsage.NUM_1 + 3),
        RemoteKey.NUM_5 to HidBinding.Keyboard(KeyboardUsage.NUM_1 + 4),
        RemoteKey.NUM_6 to HidBinding.Keyboard(KeyboardUsage.NUM_1 + 5),
        RemoteKey.NUM_7 to HidBinding.Keyboard(KeyboardUsage.NUM_1 + 6),
        RemoteKey.NUM_8 to HidBinding.Keyboard(KeyboardUsage.NUM_1 + 7),
        RemoteKey.NUM_9 to HidBinding.Keyboard(KeyboardUsage.NUM_1 + 8),

        RemoteKey.PLAY_PAUSE to HidBinding.Consumer(ConsumerUsage.PLAY_PAUSE),
        RemoteKey.STOP to HidBinding.Consumer(ConsumerUsage.STOP),
        RemoteKey.FAST_FORWARD to HidBinding.Consumer(ConsumerUsage.FAST_FORWARD),
        RemoteKey.REWIND to HidBinding.Consumer(ConsumerUsage.REWIND),
        RemoteKey.NEXT_TRACK to HidBinding.Consumer(ConsumerUsage.SCAN_NEXT),
        RemoteKey.PREV_TRACK to HidBinding.Consumer(ConsumerUsage.SCAN_PREV),

        // POWER_ON is deliberately absent: a TV with its radio off cannot receive HID.
        // The BT transport does not advertise the POWER_ON capability.
        RemoteKey.POWER_OFF to HidBinding.Consumer(ConsumerUsage.POWER),

        RemoteKey.BACKSPACE to HidBinding.Keyboard(KeyboardUsage.BACKSPACE),
        RemoteKey.ENTER to HidBinding.Keyboard(KeyboardUsage.ENTER),
        RemoteKey.SPACE to HidBinding.Keyboard(KeyboardUsage.SPACE),
        RemoteKey.TAB to HidBinding.Keyboard(KeyboardUsage.TAB),
    )

    operator fun get(key: RemoteKey): HidBinding? = map[key]

    val supportedKeys: Set<RemoteKey> get() = map.keys
}
