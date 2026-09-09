package dev.djabari.uniremote.transport.network.adapters

import dev.djabari.uniremote.model.RemoteKey

/** Tizen `SendRemoteKey` names. Unmapped keys are simply not offered by the adapter. */
internal object TizenKeys {
    private val map = mapOf(
        RemoteKey.DPAD_UP to "KEY_UP",
        RemoteKey.DPAD_DOWN to "KEY_DOWN",
        RemoteKey.DPAD_LEFT to "KEY_LEFT",
        RemoteKey.DPAD_RIGHT to "KEY_RIGHT",
        RemoteKey.DPAD_CENTER to "KEY_ENTER",
        RemoteKey.BACK to "KEY_RETURN",
        RemoteKey.HOME to "KEY_HOME",
        RemoteKey.MENU to "KEY_MENU",
        RemoteKey.VOLUME_UP to "KEY_VOLUP",
        RemoteKey.VOLUME_DOWN to "KEY_VOLDOWN",
        RemoteKey.MUTE to "KEY_MUTE",
        RemoteKey.CHANNEL_UP to "KEY_CHUP",
        RemoteKey.CHANNEL_DOWN to "KEY_CHDOWN",
        RemoteKey.NUM_0 to "KEY_0",
        RemoteKey.NUM_1 to "KEY_1",
        RemoteKey.NUM_2 to "KEY_2",
        RemoteKey.NUM_3 to "KEY_3",
        RemoteKey.NUM_4 to "KEY_4",
        RemoteKey.NUM_5 to "KEY_5",
        RemoteKey.NUM_6 to "KEY_6",
        RemoteKey.NUM_7 to "KEY_7",
        RemoteKey.NUM_8 to "KEY_8",
        RemoteKey.NUM_9 to "KEY_9",
        RemoteKey.PLAY_PAUSE to "KEY_PLAY_BACK",
        RemoteKey.STOP to "KEY_STOP",
        RemoteKey.FAST_FORWARD to "KEY_FF",
        RemoteKey.REWIND to "KEY_REWIND",
        RemoteKey.NEXT_TRACK to "KEY_NEXT",
        RemoteKey.PREV_TRACK to "KEY_PREVIOUS",
        RemoteKey.POWER_OFF to "KEY_POWER",
        RemoteKey.ENTER to "KEY_ENTER",
    )

    operator fun get(key: RemoteKey): String? = map[key]
}

/**
 * webOS is split across two channels: navigation goes over the pointer input socket as
 * button names, everything else is an ssap:// URI on the main socket.
 */
internal object WebOsKeys {
    private val buttons = mapOf(
        RemoteKey.DPAD_UP to "UP",
        RemoteKey.DPAD_DOWN to "DOWN",
        RemoteKey.DPAD_LEFT to "LEFT",
        RemoteKey.DPAD_RIGHT to "RIGHT",
        RemoteKey.DPAD_CENTER to "ENTER",
        RemoteKey.BACK to "BACK",
        RemoteKey.HOME to "HOME",
        RemoteKey.MENU to "MENU",
        RemoteKey.NUM_0 to "0",
        RemoteKey.NUM_1 to "1",
        RemoteKey.NUM_2 to "2",
        RemoteKey.NUM_3 to "3",
        RemoteKey.NUM_4 to "4",
        RemoteKey.NUM_5 to "5",
        RemoteKey.NUM_6 to "6",
        RemoteKey.NUM_7 to "7",
        RemoteKey.NUM_8 to "8",
        RemoteKey.NUM_9 to "9",
    )

    private val uris = mapOf(
        RemoteKey.VOLUME_UP to "ssap://audio/volumeUp",
        RemoteKey.VOLUME_DOWN to "ssap://audio/volumeDown",
        RemoteKey.MUTE to "ssap://audio/setMute",
        RemoteKey.CHANNEL_UP to "ssap://tv/channelUp",
        RemoteKey.CHANNEL_DOWN to "ssap://tv/channelDown",
        RemoteKey.PLAY_PAUSE to "ssap://media.controls/play",
        RemoteKey.STOP to "ssap://media.controls/stop",
        RemoteKey.FAST_FORWARD to "ssap://media.controls/fastForward",
        RemoteKey.REWIND to "ssap://media.controls/rewind",
        RemoteKey.POWER_OFF to "ssap://system/turnOff",
        RemoteKey.ENTER to "ssap://com.webos.service.ime/sendEnterKey",
        RemoteKey.BACKSPACE to "ssap://com.webos.service.ime/deleteCharacters",
    )

    fun button(key: RemoteKey): String? = buttons[key]

    fun uri(key: RemoteKey): String? = uris[key]

    val supported: Set<RemoteKey> get() = buttons.keys + uris.keys
}
