package dev.djabari.tvremote.transport.bthid

/**
 * Character -> (modifier, keyboard usage).
 *
 * HID keyboards transmit *physical key positions*, not characters, so this map is
 * inherently US-QWERTY-shaped and inherently ASCII-only. Anything outside ASCII returns
 * null and the keyboard screen must tell the user so rather than silently dropping input.
 * Unicode text is a network-transport capability ([TransportCapability.UNICODE_TEXT]).
 */
object AsciiKeyMap {

    data class Stroke(val modifiers: Int, val usage: Int)

    private val shifted = mapOf(
        '!' to KeyboardUsage.NUM_1, '@' to KeyboardUsage.NUM_1 + 1, '#' to KeyboardUsage.NUM_1 + 2,
        '$' to KeyboardUsage.NUM_1 + 3, '%' to KeyboardUsage.NUM_1 + 4, '^' to KeyboardUsage.NUM_1 + 5,
        '&' to KeyboardUsage.NUM_1 + 6, '*' to KeyboardUsage.NUM_1 + 7, '(' to KeyboardUsage.NUM_1 + 8,
        ')' to KeyboardUsage.NUM_0,
        '_' to KeyboardUsage.MINUS, '+' to KeyboardUsage.EQUAL,
        '{' to KeyboardUsage.LEFT_BRACKET, '}' to KeyboardUsage.RIGHT_BRACKET,
        '|' to KeyboardUsage.BACKSLASH, ':' to KeyboardUsage.SEMICOLON,
        '"' to KeyboardUsage.APOSTROPHE, '~' to KeyboardUsage.GRAVE,
        '<' to KeyboardUsage.COMMA, '>' to KeyboardUsage.PERIOD, '?' to KeyboardUsage.SLASH,
    )

    private val unshifted = mapOf(
        '-' to KeyboardUsage.MINUS, '=' to KeyboardUsage.EQUAL,
        '[' to KeyboardUsage.LEFT_BRACKET, ']' to KeyboardUsage.RIGHT_BRACKET,
        '\\' to KeyboardUsage.BACKSLASH, ';' to KeyboardUsage.SEMICOLON,
        '\'' to KeyboardUsage.APOSTROPHE, '`' to KeyboardUsage.GRAVE,
        ',' to KeyboardUsage.COMMA, '.' to KeyboardUsage.PERIOD, '/' to KeyboardUsage.SLASH,
        ' ' to KeyboardUsage.SPACE, '\n' to KeyboardUsage.ENTER, '\t' to KeyboardUsage.TAB,
    )

    fun stroke(char: Char): Stroke? = when {
        char in 'a'..'z' ->
            Stroke(KeyboardModifier.NONE, KeyboardUsage.A + (char - 'a'))
        char in 'A'..'Z' ->
            Stroke(KeyboardModifier.LEFT_SHIFT, KeyboardUsage.A + (char - 'A'))
        char == '0' ->
            Stroke(KeyboardModifier.NONE, KeyboardUsage.NUM_0)
        char in '1'..'9' ->
            Stroke(KeyboardModifier.NONE, KeyboardUsage.NUM_1 + (char - '1'))
        shifted.containsKey(char) ->
            Stroke(KeyboardModifier.LEFT_SHIFT, shifted.getValue(char))
        unshifted.containsKey(char) ->
            Stroke(KeyboardModifier.NONE, unshifted.getValue(char))
        else -> null
    }

    fun isTypeable(char: Char): Boolean = stroke(char) != null

    /** Characters in [text] this transport cannot express. Show these to the user. */
    fun untypeable(text: String): Set<Char> = text.filterNot(::isTypeable).toSet()
}
