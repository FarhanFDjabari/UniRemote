# Hardware validation matrix

The only test that settles the Tizen/webOS adapters, the Bluetooth HID report descriptor and
the keep-alive is a real TV. Run this checklist against every supported device **before each
release** and commit the result. There is no substitute — a unit test cannot see a TV's
pairing prompt.

## How to run

1. Install the release-candidate build on the phone you will carry to the TV.
2. One row per physical TV. Fill in brand, model, and the firmware/OS version shown in the
   TV's settings (e.g. "Tizen 7.0", "webOS 23", "Roku OS 12.5", "Android TV 12").
3. Work through the columns in order; a failure in any column means the release is not green
   for that device.
4. Power-off / reconnect checks last — they need the TV to have been on for a while.

## Column semantics

| Check | Passes when |
|---|---|
| HID role accepted | TV lists the phone as a keyboard in its Bluetooth settings and pairs without a PIN (or with the on-screen passkey confirmation). |
| Focus moves | D-pad + OK move focus in the TV's own UI (home screen or settings, not an app). |
| Back semantics | Back exits to the previous screen. Tizen TVs need Escape; Android TV / Fire TV / webOS should honour AC Back. |
| Volume (consumer page) | Volume up/down and mute work from the remote screen. |
| ASCII text | Typing from the keyboard screen lands in a search field. |
| Pointer | Touchpad moves the cursor and left/right click registers. |
| Power off | Power button turns the TV off (HID consumer Power; network `POWER_OFF` where claimed). |
| 5 min idle | After 5 minutes untouched, the link is still alive — validates the keep-alive. |
| Reconnect on resume | Background the app (home screen), wait 10 s, return: connection is silently re-established without re-pairing. |
| Disconnect | Disconnect from the notification stops the session and the TV forgets the link. |

## Matrix

Legend: ✅ pass · ❌ fail · ➖ not applicable (device does not support this path) · ⬜ not run yet

### Bluetooth HID (primary transport)

| Brand | Model | Firmware | HID role accepted | Focus moves | Back semantics | Volume (consumer) | ASCII text | Pointer | Power off | 5 min idle | Reconnect on resume | Disconnect |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | | | | |
| | | | | | | | | | | | | |

### Network fallback (per ecosystem)

| Brand | Model | Firmware | Silent identify (Roku) / generic probe absent | Pairing prompt → token survives app restart | Client key survives app restart | WoL from cold |
|---|---|---|---|---|---|---|
| Roku (network-only) | | | | — | — | |
| Samsung Tizen | | | — | | — | |
| LG webOS | | | — | — | | |
| Android TV / Google TV | | | — | — | — | |

## Notes per ecosystem

- **Roku**: does not accept a Bluetooth HID peripheral — it ships network-only. `GENERIC`
  targets are never blind-probed against Roku ECP; identification is silent
  (`GET /query/device-info`). If a generic probe fails, the app must surface the
  pick-a-brand error, not hang.
- **Tizen**: Back over HID is Escape, not AC Back. First network connect always shows the
  on-screen allow prompt; the token is persisted per target — verify it does not re-prompt
  after a full app restart.
- **LG webOS**: the client key is tied to `appId`; changing the application id invalidates it
  and re-prompts once. Pointer over the network is unsupported — only HID.
- **Android TV / Google TV**: network adapter is WoL-only (no six-digit pairing flow over the
  network; HID covers everything else). WoL needs the MAC — fill it in on the TV's settings
  page or wake from cold fails.
- **Cold power-on**: only reachable over the network (WoL / always-on stack). Never promise
  power-on in the UI for a HID-only session.

## Regression watchlist

Things that break silently and are only caught here:

- A stuck modifier or missing HID release report (TV acts "crazy": typing shifts, focus
  jumps).
- Idle drop followed by a failed silent reconnect (user has to re-pair).
- Phone OEMs shipping broken `BluetoothHidDevice` registrations (notably older
  Xiaomi/Huawei) — the app must detect the failure and say so honestly.
