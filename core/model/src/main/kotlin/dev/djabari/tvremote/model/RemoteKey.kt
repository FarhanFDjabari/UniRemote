package dev.djabari.tvremote.model

/**
 * Transport-agnostic vocabulary of everything the remote can ask a TV to do.
 *
 * Nothing above [dev.djabari.tvremote.transport] knows how these become bytes on a wire.
 * A transport translates them; a transport that cannot express one declares the missing
 * [TransportCapability] and the UI hides the control.
 */
enum class RemoteKey(val group: KeyGroup) {
    // Navigation
    DPAD_UP(KeyGroup.DPAD),
    DPAD_DOWN(KeyGroup.DPAD),
    DPAD_LEFT(KeyGroup.DPAD),
    DPAD_RIGHT(KeyGroup.DPAD),
    DPAD_CENTER(KeyGroup.DPAD),
    BACK(KeyGroup.DPAD),
    HOME(KeyGroup.DPAD),
    MENU(KeyGroup.DPAD),

    // Volume
    VOLUME_UP(KeyGroup.VOLUME),
    VOLUME_DOWN(KeyGroup.VOLUME),
    MUTE(KeyGroup.VOLUME),

    // Channel
    CHANNEL_UP(KeyGroup.CHANNEL),
    CHANNEL_DOWN(KeyGroup.CHANNEL),

    // Numpad
    NUM_0(KeyGroup.NUMPAD),
    NUM_1(KeyGroup.NUMPAD),
    NUM_2(KeyGroup.NUMPAD),
    NUM_3(KeyGroup.NUMPAD),
    NUM_4(KeyGroup.NUMPAD),
    NUM_5(KeyGroup.NUMPAD),
    NUM_6(KeyGroup.NUMPAD),
    NUM_7(KeyGroup.NUMPAD),
    NUM_8(KeyGroup.NUMPAD),
    NUM_9(KeyGroup.NUMPAD),

    // Media
    PLAY_PAUSE(KeyGroup.MEDIA),
    STOP(KeyGroup.MEDIA),
    FAST_FORWARD(KeyGroup.MEDIA),
    REWIND(KeyGroup.MEDIA),
    NEXT_TRACK(KeyGroup.MEDIA),
    PREV_TRACK(KeyGroup.MEDIA),

    // Power
    POWER_OFF(KeyGroup.POWER),
    POWER_ON(KeyGroup.POWER),

    // Text editing (reachable from the keyboard screen)
    BACKSPACE(KeyGroup.TEXT),
    ENTER(KeyGroup.TEXT),
    SPACE(KeyGroup.TEXT),
    TAB(KeyGroup.TEXT),
}

enum class KeyGroup { DPAD, VOLUME, CHANNEL, NUMPAD, MEDIA, POWER, TEXT }

enum class KeyAction {
    /** Press followed immediately by release. The default for a tap. */
    TAP,

    /** Press and hold. MUST eventually be matched by [RELEASE]. */
    PRESS,

    /** Release a previously held key. */
    RELEASE,
}
