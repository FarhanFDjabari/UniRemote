package dev.djabari.tvremote.transport.bthid

/**
 * Composite HID report descriptor: keyboard + mouse + consumer control.
 *
 * Three top-level collections sharing one descriptor, distinguished by Report ID:
 *
 *   ID 1  Keyboard  [modifiers, reserved, kc0..kc5]        8 bytes
 *   ID 2  Mouse     [buttons, dx, dy, wheel]               4 bytes  (int8 relative)
 *   ID 3  Consumer  [usage0_lo, usage0_hi, usage1_lo, hi]  4 bytes  (16-bit array)
 *
 * This is the single most load-bearing artifact in the app. If a TV refuses the
 * descriptor, nothing else works — which is why the Phase 0 spike exists.
 *
 * Verified against the USB HID 1.11 spec and HUT 1.12 usage tables.
 */
object HidDescriptor {

    const val REPORT_ID_KEYBOARD: Byte = 0x01
    const val REPORT_ID_MOUSE: Byte = 0x02
    const val REPORT_ID_CONSUMER: Byte = 0x03

    val BYTES: ByteArray = byteArrayOf(
        // ---------------- Keyboard (Report ID 1) ----------------
        0x05.toByte(), 0x01.toByte(),             // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),             // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),             // Collection (Application)
        0x85.toByte(), 0x01.toByte(),             //   Report ID (1)
        0x05.toByte(), 0x07.toByte(),             //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),             //   Usage Minimum (LeftControl)
        0x29.toByte(), 0xE7.toByte(),             //   Usage Maximum (RightGUI)
        0x15.toByte(), 0x00.toByte(),             //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),             //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),             //   Report Size (1)
        0x95.toByte(), 0x08.toByte(),             //   Report Count (8)
        0x81.toByte(), 0x02.toByte(),             //   Input (Data,Var,Abs)   -> modifier byte
        0x95.toByte(), 0x01.toByte(),             //   Report Count (1)
        0x75.toByte(), 0x08.toByte(),             //   Report Size (8)
        0x81.toByte(), 0x03.toByte(),             //   Input (Cnst,Var,Abs)   -> reserved byte
        0x95.toByte(), 0x06.toByte(),             //   Report Count (6)
        0x75.toByte(), 0x08.toByte(),             //   Report Size (8)
        0x15.toByte(), 0x00.toByte(),             //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), //   Logical Maximum (255)
        0x05.toByte(), 0x07.toByte(),             //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(),             //   Usage Minimum (0)
        0x2A.toByte(), 0xFF.toByte(), 0x00.toByte(), //   Usage Maximum (255)
        0x81.toByte(), 0x00.toByte(),             //   Input (Data,Ary,Abs)   -> 6 keycodes
        0xC0.toByte(),                            // End Collection

        // ---------------- Mouse (Report ID 2) ----------------
        0x05.toByte(), 0x01.toByte(),             // Usage Page (Generic Desktop)
        0x09.toByte(), 0x02.toByte(),             // Usage (Mouse)
        0xA1.toByte(), 0x01.toByte(),             // Collection (Application)
        0x85.toByte(), 0x02.toByte(),             //   Report ID (2)
        0x09.toByte(), 0x01.toByte(),             //   Usage (Pointer)
        0xA1.toByte(), 0x00.toByte(),             //   Collection (Physical)
        0x05.toByte(), 0x09.toByte(),             //     Usage Page (Button)
        0x19.toByte(), 0x01.toByte(),             //     Usage Minimum (Button 1)
        0x29.toByte(), 0x03.toByte(),             //     Usage Maximum (Button 3)
        0x15.toByte(), 0x00.toByte(),             //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),             //     Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),             //     Report Size (1)
        0x95.toByte(), 0x03.toByte(),             //     Report Count (3)
        0x81.toByte(), 0x02.toByte(),             //     Input (Data,Var,Abs)  -> 3 buttons
        0x75.toByte(), 0x05.toByte(),             //     Report Size (5)
        0x95.toByte(), 0x01.toByte(),             //     Report Count (1)
        0x81.toByte(), 0x03.toByte(),             //     Input (Cnst,Var,Abs)  -> 5 bit padding
        0x05.toByte(), 0x01.toByte(),             //     Usage Page (Generic Desktop)
        0x09.toByte(), 0x30.toByte(),             //     Usage (X)
        0x09.toByte(), 0x31.toByte(),             //     Usage (Y)
        0x09.toByte(), 0x38.toByte(),             //     Usage (Wheel)
        0x15.toByte(), 0x81.toByte(),             //     Logical Minimum (-127)
        0x25.toByte(), 0x7F.toByte(),             //     Logical Maximum (127)
        0x75.toByte(), 0x08.toByte(),             //     Report Size (8)
        0x95.toByte(), 0x03.toByte(),             //     Report Count (3)
        0x81.toByte(), 0x06.toByte(),             //     Input (Data,Var,Rel)  -> dx, dy, wheel
        0xC0.toByte(),                            //   End Collection
        0xC0.toByte(),                            // End Collection

        // ---------------- Consumer Control (Report ID 3) ----------------
        0x05.toByte(), 0x0C.toByte(),             // Usage Page (Consumer)
        0x09.toByte(), 0x01.toByte(),             // Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(),             // Collection (Application)
        0x85.toByte(), 0x03.toByte(),             //   Report ID (3)
        0x15.toByte(), 0x00.toByte(),             //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x03.toByte(), //   Logical Maximum (0x3FF)
        0x19.toByte(), 0x00.toByte(),             //   Usage Minimum (0)
        0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(), //   Usage Maximum (0x3FF)
        0x75.toByte(), 0x10.toByte(),             //   Report Size (16)
        0x95.toByte(), 0x02.toByte(),             //   Report Count (2)
        0x81.toByte(), 0x00.toByte(),             //   Input (Data,Ary,Abs)  -> 2 usages
        0xC0.toByte(),                            // End Collection
    )
}
