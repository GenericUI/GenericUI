# GenericUI Cross-Platform Gamepad, Six-Axis IMU, & UI Navigation Guide (`:extensions:game:gamepad`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Game Engines (`game-engines.md`)](./game-engines.md) · [First-Party Extensions (`extensions.md`)](./extensions.md) · [Declarative Kotlin DSL (`../extensions/docs/dsl.md`)](../extensions/docs/dsl.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md)

The **`:extensions:game:gamepad`** module (`com.nexustools.genericui.ext.gamepad`) provides unified, cross-platform controller input, six-axis gyroscope/accelerometer sensor fusion, vendor-specific controller hardware features, and **zero-config UI controller navigation** across all 9 GenericUI backends depending **only on `:common`** (with zero separate `:dsl` companion module).

- **Maven Coordinates**: `com.nexustools.genericui.extensions.game:gamepad:0.1.0-SNAPSHOT`
- **Gradle Plugin DSL**: `genericui { extensions { gamepad() } }`

---

## 1. Unified Multi-Player Architecture (`GamepadManager`)

[`GamepadManager`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/GamepadManager.kt) tracks up to **`MAX_PLAYERS = 4`** concurrent controllers and exposes both stateful player handles ([`GamepadControllerHandle`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/GamepadControllerHandle.kt)) and immutable per-frame snapshots ([`GamepadFrameSnapshot`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/GamepadFrameSnapshot.kt)):

- **Global & Scoped Instances**: Use `GamepadManager.shared` or construct an isolated `GamepadManager()` instance (for scene-local polling or unit tests).
- **Polling & Edge Detection**: Call `pollAll()` / `poll()` once per frame or tick to sample connected hardware and compute edge transitions (`justPressedButtons`, `justReleasedButtons`, thumbstick deadzones, analog trigger values, and integrated IMU pose).
- **Deterministic Headless Testing**: Inject raw HID / hardware reports via `injectRawHardwareReport(...)` or attach mock gamepads via `setSyntheticController(...)` with zero physical hardware attached.

### Unified Button & Axis Normalization (`GamepadButton` & `GamepadAxis`)

[`GamepadLayoutMapper`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/GamepadLayoutMapper.kt) automatically detects USB/Bluetooth `vendorId` & `productId` via [`ControllerHardwareProfile`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/ControllerHardwareProfile.kt) (`ControllerFamily`: **`XBOX`**, **`PLAYSTATION`**, **`NINTENDO_SWITCH`**, **`GENERIC`**) and maps native OS driver layouts into unified canonical constants:

| Family | Hardware Detected (`ControllerHardwareProfile`) | Button Mapping & Labels (`ControllerButtonLabels`) |
| :--- | :--- | :--- |
| **`ControllerFamily.XBOX`** | Microsoft Xbox One, Xbox Series X\|S, Elite / Elite Series 2 (rear paddles `P1..P4`), Xbox Adaptive (`VID 0x045E`) | `SOUTH` (`A`), `EAST` (`B`), `WEST` (`X`), `NORTH` (`Y`), `LB`, `RB`, `VIEW`, `MENU`, `GUIDE`, `SHARE` |
| **`ControllerFamily.PLAYSTATION`** | Sony DualShock 4 (`0x054C:0x05C4` / `0x09CC`), DualSense (`0x0CE6`), DualSense Edge (`0x0DF2`) (`VID 0x054C`) | `SOUTH` (`Cross`), `EAST` (`Circle`), `WEST` (`Square`), `NORTH` (`Triangle`), `LB` (`L1`), `RB` (`R1`), `VIEW` (`Create`/`Share`), `MENU` (`Options`), `TOUCHPAD_CLICK`, `MUTE` |
| **`ControllerFamily.NINTENDO_SWITCH`** | Nintendo Switch Pro Controller (`0x057E:0x2009`), Joy-Con L/R (`0x2006`/`0x2007`), N64/SNES NSO controllers (`VID 0x057E`) | Supports **both positional** (`SOUTH` = bottom `B` = confirm in UI/action games, default `setSwitchUsePositionalActionButtons(true)`) and **label-matched** (`A`/`B`/`X`/`Y`) mappings, plus `CAPTURE`, `HOME`, `PLUS`, `MINUS`, `SL`/`SR` |
| **`ControllerFamily.GENERIC`** | Standard HID / 8BitDo / Logitech / Valve Steam Deck (`0x28DE`) | Standard Xbox-normalized gamepad axes (`LEFT_X`, `LEFT_Y`, `RIGHT_X`, `RIGHT_Y`, `LEFT_TRIGGER`, `RIGHT_TRIGGER`) and face/shoulder/D-pad buttons |

> [!TIP]
> Use `ControllerButtonLabels.buttonName(button, family)` to display accurate PlayStation (`Cross`/`Circle`), Nintendo (`B`/`A`), or Xbox (`A`/`B`) glyph prompts automatically in UI overlays.

---

## 2. Six-Axis Gyroscope, Accelerometer, & Sensor Fusion

[`SixAxisMotionState`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/SixAxisMotionState.kt) and [`SixAxisSensorFusion`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/SixAxisSensorFusion.kt) turn raw controller gyroscope (`rad/s` / `deg/s`) and 3-axis accelerometer (`g`) streams (on Switch Pro/Joy-Con, DualSense/DualShock 4, Linux `evdev` IMU nodes, Apple `GCMotion`, Android sensors, and Web `Gamepad.pose`) into drift-compensated orientation:

- **Fused Outputs**: `orientation` (pitch/roll/yaw Euler angles & quaternion), `angularVelocityRadPerSec`, `accelerationG`, `gravityVector`, and gravity-subtracted `linearAccelerationG`.
- **Stationary Gyro Bias Calibration**:
  - `gamepadManager.calibrateGyroBias(playerIndex, biasX, biasY, biasZ)` — apply stored manual offsets.
  - `gamepadManager.calibrateGyroFromStationarySamples(playerIndex, samples)` — compute and store gyro zero-rate bias from resting samples.
  - `gamepadManager.resetMotionOrientation(playerIndex)` — recenter yaw/pitch/roll for gyro aiming or motion steering.

---

## 3. Vendor-Specific Controller Hardware Features

Every snapshot exposes optional hardware blocks (`switchFeatures`, `playStation`, `xbox`) paired with typed command methods on `GamepadManager`:

| Controller Family | Feature State & Commands | Hardware Capabilities |
| :--- | :--- | :--- |
| **Nintendo Switch Pro / Joy-Con** | [`SwitchControllerFeatures`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/SwitchControllerFeatures.kt)<br/>[`SwitchHdRumbleCommand`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/SwitchHdRumbleCommand.kt)<br/>[`SwitchHomeLedPattern`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/SwitchHomeLedPattern.kt)<br/>[`SwitchNfcStatus`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/SwitchNfcStatus.kt) | - **HD Rumble (`setSwitchHdRumble`)**: Dual linear-resonant actuator waveform control (`lowBandFreqHz`, `lowBandAmp`, `highBandFreqHz`, `highBandAmp`) for crisp click/texture feedback.<br/>- **Home LED & Player LEDs**: `setSwitchHomeLedPattern(playerIndex, SwitchHomeLedPattern(...))` drives the blue Home ring LED mini-cycles; `setSwitchPlayerLeds(playerIndex, mask)` controls the 4 green player LEDs.<br/>- **NFC / Amiibo & IR Metadata**: Exposes `SwitchNfcStatus` and IR/gyro capability flags. |
| **Sony DualSense / DualShock 4** | [`PlayStationControllerFeatures`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/PlayStationControllerFeatures.kt)<br/>[`AdaptiveTriggerEffect`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/AdaptiveTriggerEffect.kt)<br/>[`TouchpadState`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/TouchpadState.kt) | - **Adaptive Triggers (`setPlayStationAdaptiveTrigger`)**: L2 / R2 motorized resistance profiles (`AdaptiveTriggerEffect.Off`, `ContinuousResistance`, `SectionResistance`, `WeaponFeedback`, `Vibration`).<br/>- **RGB Lightbar, Player LEDs, & Mic Mute LED**: `setPlayStationLightbarColor(playerIndex, r, g, b)`, `setPlayStationPlayerIndicatorLedMask(playerIndex, mask)`, and `setPlayStationMuteLed(...)`.<br/>- **2-Finger Capacitive Touchpad**: Tracks up to 2 simultaneous fingers (`TouchpadFingerState` normalized coordinates, active status, contact ID) + physical click button (`TOUCHPAD_CLICK`). |
| **Xbox One / Series X\|S / Elite** | [`XboxControllerFeatures`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/XboxControllerFeatures.kt)<br/>[`XboxImpulseRumbleCommand`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/XboxImpulseRumbleCommand.kt) | - **Impulse Trigger Rumble (`setXboxImpulseRumble`)**: Independent `leftTriggerRumble` and `rightTriggerRumble` intensities alongside dual `leftGripRumble` / `rightGripRumble` motors.<br/>- **Share & Elite Paddles**: Tracks modern Xbox Series `SHARE` button and Elite `P1`–`P4` rear paddles (`isShareSupported`, `paddleStates`). |

---

## 4. Platform Hardware Backend Matrix (`PlatformGamepadBackend`)

| Platform Backend | Source Set | Native Controller Transport |
| :--- | :--- | :--- |
| **Android** | `androidMain` | `android.view.InputDevice`, `AndroidGamepadInputBridge` (buttons, axes, hats, touchpad, IMU sensors), API 31+ `LightsManager`, and `VibratorManager` / `Vibrator`. |
| **macOS, iOS, & iPadOS** | `appleMain` | Apple **`GameController.framework`** (`GCController`, `GCDualSenseGamepad`, `GCDualShockGamepad`, `GCXboxGamepad`, `GCMotion` 6-axis IMU) + **`CoreHaptics`** (`CHHapticEngine` grip & trigger localities). |
| **Windows (`mingwX64`)** | `mingwMain` | Dynamic `XInput` (`xinput1_4.dll`), WinMM `joyGetPosEx` DirectInput fallback (DS4, DualSense, Switch Pro, 8BitDo), and RawInput + `hid.dll` (`HidD_SetOutputReport`) for DualSense lightbar, adaptive triggers, and Switch HID reports. |
| **Linux (`linuxX64`, `linuxArm64`)** | `linuxMain` | `/dev/input/js*` joystick API + sysfs vendor/product detection, paired `/dev/input/event*` (`evdev` 6-axis IMU & `EVIOCSFF` force-feedback rumble), `/sys/class/leds/` PlayStation LEDs, and `/dev/hidraw*` DualSense/Switch output reports. |
| **JVM Swing** | `jvmMain` | `JvmGamepadBridge` + reflective LWJGL 3 GLFW gamepad polling (when LWJGL is present) + Linux `/dev/input/js*` / `/dev/hidraw*` on Linux JVMs. |
| **Web (`js`, `wasmJs`)** | `jsMain`, `wasmJsMain` | W3C `navigator.getGamepads()`, `Gamepad.pose` (when browser exposes IMU), `vibrationActuator.playEffect("dual-rumble")`, and optional paired WebHID (`navigator.hid`) output reports for DualSense lightbar/LEDs. |

---

## 5. Zero-Config UI Controller Navigation (`GamepadUiNavigator`) & Custom Strategies

[`GamepadUiNavigator`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/ui/GamepadUiNavigator.kt) lets any standard GenericUI screen (native buttons, checkboxes, sliders, selects, tabs, scroll views, and modal dialogs) be driven seamlessly with D-pad, thumbstick, shoulder bumpers, and face buttons **with zero manual wiring**:

- **Zero-Config Activation**:
  - Call `enableGamepadUiNavigation(...)` directly ([`GamepadUiNavigationExtensions.kt`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/ui/GamepadUiNavigationExtensions.kt)) on any `AbstractContainer`, `Window`, `DialogWindow`, or `Application` (`rootContainer.enableGamepadUiNavigation()`, `window.enableGamepadUiNavigation()`, `dialogWindow.enableGamepadUiNavigation()`, or `application.enableGamepadUiNavigation()`) and invoke `pollAndUpdate(deltaSeconds)` on frame updates/timers.
- **Automatic Spatial Routing & Widget Actions**:
  - Discovers interactive controls (`Button`, `StyledButton`, `IconButton`, `ToggleButton`, `CheckBox`, `RadioButton`, `Switch`, `Select`, `TextField`, `TextArea`, `RangeSlider`, `Tab`, `ListGroupItem`, or any component marked with `data-gamepad-focusable="true"` / excluded via `"false"`) and synchronizes focus with `InputFocusManager`.
  - Uses directional half-plane + cone weighting across live measured bounds, falling back deterministically to DOM/tree order in headless tests before layout bounds exist.
  - **Built-In Control Bindings**: `A` / `SOUTH` activates buttons/checkboxes/switches or expands `Select`s; `B` / `EAST` cancels (`onCancel`); `LB`/`RB` cycle `TabContainer` tabs; right-stick scrolls active `ScrollView`s; left/right on a focused `RangeSlider` steps its value; and open modal `DialogWindow`s (`attachDialogWindows`) confine navigation cleanly within the modal until dismissed.
- **Customizable & Replaceable Strategy (`GamepadUiNavigationStrategy`)**:
  - Implement [`GamepadUiNavigationStrategy`](../extensions/game/gamepad/src/commonMain/kotlin/com/nexustools/genericui/ext/gamepad/ui/GamepadUiNavigationStrategy.kt) (`discoverFocusableComponents`, `resolveInitialFocus`, `resolveNextFocus`, `onFocusChanged`, `onActivate`, `onCancel`, `onTabCycle`, `onContinuousInput`) or subclass `open class DefaultSpatialGamepadUiNavigationStrategy` to override grid wrap-around, ring menus, audio feedback on focus change, or custom widget activation rules.

```kotlin
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.ext.gamepad.ControllerButtonLabels
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.ext.gamepad.GamepadManager
import com.nexustools.genericui.ext.gamepad.ui.enableGamepadUiNavigation

val menuStack = VStack()
val navigator = menuStack.enableGamepadUiNavigation(
    gamepadManager = GamepadManager.shared,
    playerIndex = 0
)

// On each frame or UI timer tick:
navigator.pollAndUpdate(deltaSeconds = 1f / 60f)

// Read connected family prompt text dynamically:
val snap = GamepadManager.shared.snapshot(0)
val confirmLabel = ControllerButtonLabels.buttonName(GamepadButton.SOUTH, snap.profile.family)
```
