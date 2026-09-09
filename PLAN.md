# UniRemote — Android (Kotlin + Compose)

**Transport strategy:** Bluetooth Classic HID Device as primary, network control as fallback.
**Target:** phone + tablet + foldable, adaptive layouts throughout.

---

## 1. The core technical bet, and where it breaks

The "no pairing code" requirement maps onto exactly one Android API: **`BluetoothHidDevice`**
(API 28+, `android.bluetooth`). The phone registers itself as a Bluetooth Classic HID
peripheral — a keyboard + mouse + consumer-control composite device. The TV then discovers
it the same way it discovers any BT keyboard. No app on the TV, no 6-digit code, no ADB
pairing dance like the Android TV Remote v2 protocol.

Be clear-eyed about the limits before writing code:

| Limit | Reality | Mitigation |
|---|---|---|
| API level | `BluetoothHidDevice` is API 28+, no compat shim exists | `minSdk = 28`. Non-negotiable. |
| Pairing UX | Still a Bluetooth *pairing*, usually "Just Works" / no PIN. Some TVs still prompt with a passkey confirmation on screen. | Onboarding flow with per-brand illustrated steps ("TV → Settings → Remotes & Accessories → Add accessory") |
| Cold power-on | A powered-off TV has its BT radio off. HID `Power` (Consumer 0x0030) can only reach a TV that is awake or in a warm standby that keeps BT alive. | This is the main reason for the network fallback (WoL + brand APIs). Never promise power-on in the UI; show capability-gated buttons. |
| TV-side support | Android TV / Google TV: excellent. Fire TV: good. Samsung Tizen: good (BT keyboard+mouse). LG webOS: keyboard yes, mouse partial. Roku: **BT HID not accepted** — proprietary only. | Capability probing + fallback transport. Roku ships as network-only. |
| Single connection | `BluetoothHidDevice` supports one connected host at a time in practice | Model it as a single active session; don't build multi-TV concurrency. |
| Vendor quirks | Some TVs drop the HID link after ~30–60s idle | Keep-alive: periodic empty report or `setConnectionPolicy`; reconnect-on-resume logic in the session service. |

**Do the spike first.** Before any UI work, build the Phase 0 spike (below) and test against
real hardware. If the TVs you care about reject the HID role, the whole plan shifts weight to
the network transport and you want to know that in week one, not week six.

---

## 2. Architecture

### 2.1 Module graph

```
                       :app
                        │
        ┌───────────────┼────────────────┬──────────────┐
        ▼               ▼                ▼              ▼
  :feature:pairing :feature:remote :feature:touchpad :feature:keyboard
        └───────────────┴────────────────┴──────────────┘
                        │
                        ▼
                  :data:session          ← ConnectionRepository + foreground service
                        │
                        ▼
                  :transport:api         ← RemoteTransport, TransportCapability, RemoteKey
                   ╱          ╲
      :transport:bthid    :transport:network
                        │
              ┌─────────┴─────────┐
         :core:model         :core:ui  (+ :core:common)
```

Rules:
- Feature modules **never** see `:transport:bthid` or `:transport:network`. They talk to
  `:data:session`, which exposes a `RemoteSession` whose surface is transport-agnostic.
- `:transport:api` owns the vocabulary (`RemoteKey`, `PointerDelta`, `TextInput`,
  `TransportCapability`). Both transports implement the same `RemoteTransport` interface.
- Adding the network transport later must not touch a single line in a feature module.
  If it does, the abstraction is wrong.

### 2.2 The transport abstraction

```kotlin
interface RemoteTransport {
    val id: TransportId
    val state: StateFlow<TransportState>
    val capabilities: Set<TransportCapability>

    suspend fun connect(target: RemoteTarget): Result<Unit>
    suspend fun disconnect()

    suspend fun sendKey(key: RemoteKey, action: KeyAction): Result<Unit>
    suspend fun sendText(text: String): Result<Unit>
    suspend fun sendPointer(delta: PointerDelta): Result<Unit>
}
```

`TransportCapability` is what drives the UI: `DPAD`, `NUMPAD`, `VOLUME`, `POWER_ON`,
`POWER_OFF`, `POINTER`, `TEXT_INPUT`, `MEDIA_KEYS`. The Compose layer reads capabilities and
disables/hides controls rather than sending commands into the void. A Roku over network gets
no touchpad; a BT HID session gets no `POWER_ON`.

**Transport selection:** `TransportSelector` in `:data:session` picks the best available
transport for a saved target. BT HID wins when the target supports it; network is chosen when
BT HID connection fails or the device is on the known-incompatible list.

### 2.3 The HID layer (`:transport:bthid`)

A **composite HID report descriptor** with three report IDs — this is the heart of the app,
and getting it right once means everything else is just byte packing.

| Report ID | Collection | Payload | Serves |
|---|---|---|---|
| `0x01` | Keyboard | `[modifiers, reserved, kc0..kc5]` (8 bytes) | D-pad (arrows), OK (Enter), Back (Esc), numbers, full text input |
| `0x02` | Mouse | `[buttons, dx, dy, wheel]` (4 bytes, relative int8) | Touchpad |
| `0x03` | Consumer | `[usage0_lo, usage0_hi, usage1_lo, usage1_hi]` (4 bytes) | Volume, mute, power, media transport, AC Home / AC Back |

Key mapping decisions:
- **Volume** → Consumer page (`0x00E9` up, `0x00EA` down, `0x00E2` mute). Do *not* use
  keyboard volume keycodes; TVs handle the consumer page far more reliably.
- **D-pad** → keyboard arrows (`0x4F`–`0x52`) + Enter (`0x28`). Universally understood.
- **Back** → Consumer `AC Back` (`0x0224`) with keyboard `Escape` as fallback. Android TV
  respects both; Tizen prefers Escape.
- **Home** → Consumer `AC Home` (`0x0223`).
- **Menu** → keyboard `Application` key (`0x65`), fallback Consumer `Menu` (`0x0040`).
- **Numbers** → keyboard `0x1E`–`0x27`.
- **Power** → Consumer `Power` (`0x0030`) for off; `POWER_ON` capability only from network.

Every key is a **press report followed by a release report**. A stuck modifier or a missing
release report is the #1 source of "the TV went crazy" bugs — the `HidReportSender` owns this
invariant and features never construct raw reports.

Text input: a hidden `BasicTextField` captures IME output, and `AsciiKeyMap` converts each
character to `(modifier, usage)`. Non-ASCII is the known gap — CJK/emoji cannot be expressed
as HID keycodes. For Android TV, the network transport's `IME_TEXT` path handles that; on BT
HID the keyboard screen shows an honest "ASCII only" hint.

Touchpad: `pointerInput` → accumulate raw drag deltas → apply an acceleration curve →
quantise to int8, clamped `-127..127` → emit at a fixed **~60–100 Hz** cadence via a
`conflate`d channel. Never send a report per motion event; the BT link will choke and the
cursor will feel like syrup.

### 2.4 Session lifecycle

- A **foreground service** (`RemoteSessionService`, type `connectedDevice`) holds the
  `BluetoothHidDevice` registration and the active connection. Registration must survive the
  Activity going to background — users switch to a video app and come back.
- The service exposes state through a repository; UI binds via `StateFlow`, never holds the
  transport directly.
- Notification: connection state + a disconnect action. This also earns the FGS the user
  visibility Android requires.
- `unregisterApp()` on service destroy. Leaking a registration is how you end up with a phone
  that can never register again until reboot.

### 2.5 Permissions

```
minSdk 28, targetSdk latest

BLUETOOTH_CONNECT   (runtime, API 31+)  — required
BLUETOOTH_SCAN      (runtime, API 31+)  — discovery; declare neverForLocation
BLUETOOTH_ADVERTISE (runtime, API 31+)  — making the phone discoverable
BLUETOOTH / BLUETOOTH_ADMIN (maxSdkVersion 30)
FOREGROUND_SERVICE + FOREGROUND_SERVICE_CONNECTED_DEVICE
POST_NOTIFICATIONS  (API 33+)
INTERNET + ACCESS_NETWORK_STATE  (network transport, phase 4)
```

No location permission on API 31+ — use `usesPermissionFlags="neverForLocation"` on
`BLUETOOTH_SCAN` and don't derive location from results. Consider `CompanionDeviceManager`
for a cleaner pairing UX that avoids the scan permission entirely; worth evaluating in
Phase 1.

---

## 3. Adaptive UI

Use `WindowSizeClass` from `androidx.compose.material3.adaptive` plus fold/posture awareness
from `WindowInfoTracker`. Four layout regimes, one shared control library:

| Window class | Layout |
|---|---|
| **Compact** (phone portrait) | Bottom nav or swipe pager: Remote / Touchpad / Keyboard. Single column, thumb-reachable D-pad in the lower two-thirds. |
| **Medium** (phone landscape, small tablet) | Two panes: D-pad + volume left, touchpad right. Navigation rail. |
| **Expanded** (tablet, unfolded foldable) | Three panes: transport/device status, full remote, touchpad + keyboard. Navigation rail. |
| **Tabletop foldable** | D-pad above the hinge, touchpad below. `FoldingFeature.State.HALF_OPENED` + hinge bounds drive the split. |

Non-negotiables:
- **No hardcoded dp for the D-pad.** It sizes from available space with a min touch target of
  48dp and a max of ~280dp, centred. Every control is built as a `@Composable` that reads its
  size from a `BoxWithConstraints`.
- Haptics on every key press (`HapticFeedbackType.LongPress` for repeat start).
- Long-press repeat for volume and D-pad with a ramp (500ms delay, then 80ms interval).
- Full landscape support; `android:configChanges` is **not** the answer — state hoists to
  ViewModels.
- Dark theme first (people use this in a dark room). Dynamic color as an option, but the
  default palette should be low-luminance.
- TalkBack labels on every control; the D-pad is 5 discrete buttons, not a custom canvas with
  invisible hit regions.

---

## 4. Phased roadmap

### Phase 0 — Feasibility spike (3–5 days) ⚠ do this first
Single-module throwaway. Register `BluetoothHidDevice`, hardcode the composite descriptor,
send arrow keys and a mouse delta. Test against every TV brand you intend to support and
write down what happened. **Gate:** at least your primary target TV accepts the HID role and
moves focus.

### Phase 1 — Foundation (1.5 weeks)
Multi-module skeleton, version catalog, Hilt, `:transport:api` contracts, permission flow,
device discovery + pairing UI, `RemoteSessionService`, connection state machine with explicit
`Disconnected / Registering / Advertising / Connecting / Connected / Failed` states.

### Phase 2 — Core remote (2 weeks)
`HidReportSender` with press/release invariants, D-pad, OK/Back/Home/Menu, volume + mute,
numpad, power-off. Long-press repeat. Adaptive compact + medium layouts. This is the point
the app is genuinely useful.

### Phase 3 — Touchpad + keyboard (1.5 weeks)
Mouse reports with acceleration and rate limiting, tap / two-finger tap / two-finger scroll /
drag. Hidden-IME text input with `AsciiKeyMap`. Expanded + tabletop layouts.

### Phase 4 — Network fallback (2–3 weeks)
`:transport:network` with per-brand adapters behind one interface: Android TV (mDNS discovery
`_androidtvremote2._tcp`, TLS + pairing code), Samsung Tizen (WebSocket `8002`), LG webOS
(WebSocket `3000` + client key), Roku ECP (HTTP `8060`, no auth). Wake-on-LAN for power-on.
Transport selection + capability-driven UI gating. **Note:** this is where the pairing codes
you wanted to avoid come back — they're the price of covering Roku and cold power-on, and
they're confined to this module.

### Phase 5 — Polish (1.5 weeks)
Multi-device profiles, per-device button remapping, widget / quick-settings tile, reconnect
on app resume, crash + connection-failure telemetry, Play Store release.

**Total: ~10–12 weeks solo.** Phases 0–3 alone (~5 weeks) give a shippable BT-HID-only app.

---

## 5. Testing

| Layer | Approach |
|---|---|
| Report encoding | Pure JVM unit tests. Byte-exact assertions on every `RemoteKey` → report array. This is where bugs hide and where tests pay off most. |
| Descriptor | A JVM test that walks the descriptor as HID items and asserts collections balance, report IDs are unique, and bit counts per report are byte-aligned. |
| Transport state machine | Turbine over the `StateFlow`, fake `BluetoothHidDevice` behind a thin `HidDeviceProxy` wrapper (the framework class is final and unmockable — wrap it on day one). |
| ViewModels | JUnit + fake transport. |
| Compose | `createAndroidComposeRule`, plus screenshot tests (Roborazzi/Paparazzi) across all four window classes — that's how adaptive layout stays adaptive. |
| Hardware | A manual matrix: brand × model × firmware, run each release. There is no substitute; keep it as a checked-in checklist. |

---

## 6. Risks worth naming now

1. **A target TV rejects the HID role.** Phase 0 exists to find this out cheaply.
2. **Idle disconnects.** Budget time for keep-alive and silent reconnect; users will not
   tolerate re-pairing.
3. **Some phone OEMs (notably older Xiaomi/Huawei builds) ship broken `BluetoothHidDevice`.**
   Detect registration failure and surface an honest message + network fallback.
4. **Non-ASCII text input is impossible over HID.** Design the keyboard screen around that
   rather than discovering it late.
5. **Play Store policy:** a remote app requesting BT permissions is fine, but the store
   listing must not imply universal brand support you haven't tested.
