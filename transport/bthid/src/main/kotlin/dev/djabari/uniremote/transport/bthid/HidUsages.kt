package dev.djabari.uniremote.transport.bthid

/** USB HID Usage Table 1.12 — Keyboard/Keypad page (0x07). */
object KeyboardUsage {
    const val NONE = 0x00
    const val A = 0x04
    const val Z = 0x1D
    const val NUM_1 = 0x1E   // 0x1E..0x26 = '1'..'9'
    const val NUM_0 = 0x27
    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val MINUS = 0x2D
    const val EQUAL = 0x2E
    const val LEFT_BRACKET = 0x2F
    const val RIGHT_BRACKET = 0x30
    const val BACKSLASH = 0x31
    const val SEMICOLON = 0x33
    const val APOSTROPHE = 0x34
    const val GRAVE = 0x35
    const val COMMA = 0x36
    const val PERIOD = 0x37
    const val SLASH = 0x38
    const val RIGHT_ARROW = 0x4F
    const val LEFT_ARROW = 0x50
    const val DOWN_ARROW = 0x51
    const val UP_ARROW = 0x52
    /** The "context menu" key. Android TV maps this to the D-pad menu action. */
    const val APPLICATION = 0x65
}

/** Modifier bitmask for byte 0 of the keyboard report. */
object KeyboardModifier {
    const val NONE = 0x00
    const val LEFT_CTRL = 0x01
    const val LEFT_SHIFT = 0x02
    const val LEFT_ALT = 0x04
    const val LEFT_GUI = 0x08
}

/** USB HID Usage Table 1.12 — Consumer page (0x0C). 16-bit usages. */
object ConsumerUsage {
    const val NONE = 0x0000
    const val POWER = 0x0030
    const val SLEEP = 0x0032
    const val MENU = 0x0040
    const val MENU_PICK = 0x0041
    const val CHANNEL_UP = 0x009C
    const val CHANNEL_DOWN = 0x009D
    const val PLAY_PAUSE = 0x00CD
    const val SCAN_NEXT = 0x00B5
    const val SCAN_PREV = 0x00B6
    const val STOP = 0x00B7
    const val FAST_FORWARD = 0x00B3
    const val REWIND = 0x00B4
    const val MUTE = 0x00E2
    const val VOLUME_UP = 0x00E9
    const val VOLUME_DOWN = 0x00EA
    const val AC_HOME = 0x0223
    const val AC_BACK = 0x0224
}
