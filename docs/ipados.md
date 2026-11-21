# GenericUI iPadOS Backend Guide (`BackendTarget.IPadOS`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Shared Apple Module (`apple.md`)](./apple.md) · [Platform Backends (`backends.md`)](./backends.md) · [Fragments & Windows (`fragments-and-windows.md`)](./fragments-and-windows.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Hierarchical SourceSets (`sourcesets.md`)](./sourcesets.md)

**iPadOS** (`BackendTarget.IPadOS`) is a dedicated, first-class target backend in GenericUI—distinct from iPhone **iOS (`BackendTarget.IOS`)**—configured in [`mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados) atop UIKit, CoreGraphics, **MoltenVK**, and the shared [`:apple` module](./apple.md).

While sharing UIKit widgets and `:apple` OS primitives with `BackendTarget.IOS`, `BackendTarget.IPadOS` enables desktop-class **multi-window `UIWindowScene` lifecycle management**, **Stage Manager / Split View / Slide Over** freeform geometry handling, native **`UIMenuSystem.mainSystem`** top menu bar integration, **trackpad/mouse pointer interactions**, **secondary context menus**, **popover anchoring**, and **hardware keyboard shortcuts**.

---

## 1. `BackendTarget.IPadOS` & `AppTarget` Specificity

- **`BackendTarget.IPadOS`** (`id = "ipados"`, `displayName = "iPadOS (UIKit)"`, `osFamilyName = "iPadOS"`):
  - Group properties on `BackendTarget` and `IApplication`:
    - `isSystem = true`, `isMobile = true`, `isApple = true`, `isIosFamily = true`, `isUnix = true`, `isPosix = true`, `isNative = true`, `isTouchPrimary = true`, `isDesktop = false`, `isJvm = false`, `isBrowser = false`.
- **`AppTarget` Specificity Resolution (`6 -> 0`)**:
  - `IPADOS (6)` -> `IOS_COMMON (5)` -> `MOBILE_COMMON (3)` -> `APPLE_COMMON (2)` -> `SYSTEM_COMMON (1)` -> `COMMON (0)`.
  - Use `AppTarget.IPADOS` to provide an iPad-exclusive `@MainAppFragment`, or `AppTarget.IOS_COMMON` to share an `@MainAppFragment` or config block between iPhone (`IOS`) and iPad (`IPADOS`) while still customizing for tablet layouts.

---

## 2. Core iPadOS Implementation Architecture (`:mobile`)

| Component | Source File | Responsibility |
| :--- | :--- | :--- |
| **`IpadosLauncher`** | [`IpadosLauncher.kt`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosLauncher.kt) | Boots GenericUI on iPadOS by installing `IpadosNativeFactory()` and launching the UIKit `UIApplication` lifecycle. |
| **`IpadosNativeFactory`** | [`IpadosNativeFactory.kt`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosNativeFactory.kt) | Extends `IosNativeFactory(backendTarget = BackendTarget.IPadOS)`. Uses [`MobileLayoutFactory`](../mobile/src/commonMain/kotlin/com/nexustools/genericui/mobile/layout/MobileLayoutFactory.kt) so hybrid layouts respond fluidly from ~320pt Slide Over up to full-screen Split View and Stage Manager desktops. Sets **`supportsMultipleWindows = true`**, **`supportsToolModalWindows = true`**, **`supportsSystemMenuBar = true`**, and **`supportsSystemTray = false`**. |
| **`IpadosSceneDelegate`** | [`IpadosSceneDelegate.kt`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosSceneDelegate.kt) | `@ExportObjCClass` `UIWindowSceneDelegateProtocol` implementation handling multi-window `UIWindowScene` session creation (`willConnectToSession`, `requestNewWindowScene`), Stage Manager / Split View / Slide Over / iPadOS freeform resizable geometry callbacks (`didUpdateEffectiveGeometry: UIWindowSceneGeometry` & `didUpdateCoordinateSpace`), and `UIMenuSystem.mainSystem` menu bar rebuilds on activation. |
| **`IpadosWindowImpl`** | [`IpadosWindowImpl.kt`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosWindowImpl.kt) | Binds `Window` / `PanelWindow` instances to `UIWindowScene` (`sizeRestrictions`, window level for `setAlwaysOnTop`), enforces windowed-only client-viewport `aspectRatio` clamping when not maximized/fullscreen, feeds `MenuBar` to `IpadosMenuBarBridge`, and exposes iPadOS `DEFAULT_TITLE_CAPABILITIES` (`supportsCustomTitleBar = true`, `supportsCustomComponents = true`, `supportsEmbeddedMenuBar = false`, `supportsNavbarMenuBar = false`, `titleBarRendersAsNavbar = true`). |
| **`IpadosMenuBarImpl` & `IpadosMenuBarBridge`** | [`IpadosMenuBarImpl.kt`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosMenuBarImpl.kt)<br/>[`IpadosMenuBarBridge.kt`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosMenuBarBridge.kt) | Builds native UIKit `UIMenu` hierarchies and wires them into **`UIMenuSystem.mainSystem`** (`buildMenuWithBuilder:`), exposing full application menus, Command (⌘) HUD shortcuts, and submenus without consuming in-viewport navbar space. |
| **`IpadosPointerAndKeyboardInstaller`** | [`IpadosPointerAndKeyboardInstaller.kt`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ipados/IpadosPointerAndKeyboardInstaller.kt) | Installs trackpad/mouse/stylus pointer hover (`UIHoverGestureRecognizer` + `UIPointerInteraction` via [`UikitPointerInteractionDelegate`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ios/UikitPointerInteractionDelegate.kt) with `UIPointerHoverEffect` & `UIPointerLiftEffect`), secondary-click context menus (`UIContextMenuInteraction` via `IosContextMenuImpl`), popover anchoring (`UIPopoverPresentationController`), and hardware keyboard shortcuts & `KeyEvent` dispatch (`GenericUiRootViewController` `pressesBegan`/`pressesEnded` + `UIKeyCommand`). |
| **Metal `backdrop-filter`** | [`UikitBackdropBlurController.kt`](../mobile/src/iosMain/kotlin/com/nexustools/genericui/ios/UikitBackdropBlurController.kt) | Shared by `IosStyledContainerImpl` and `IosStyledButtonImpl` on both iOS and iPadOS (`TextBubble` excluded via `withoutBackdropFilter()`): mounts a Metal-backed `UIVisualEffectView` (`CABackdropLayer`) + paused `UIViewPropertyAnimator` `fractionComplete` curve + `tintOverlayView` while keeping `hostView.backgroundColor = UIColor.clearColor`. |

---

## 3. Multitasking, Stage Manager, Always-On-Top, Aspect-Ratio Locking, & `ServiceWorker` Policy

1. **Multi-Window & Tool Panels**: Unlike single-window iPhone (`BackendTarget.IOS`), `IpadosNativeFactory` reports `supportsMultipleWindows = true` and `supportsToolModalWindows = true`. Multi-window scene activation creates additional `UIWindowScene` windows coordinated through `IpadosSceneDelegate`.
2. **Stage Manager & Freeform Resizing**: When an iPad window resizes interactively in Split View, Slide Over, or Stage Manager, `IpadosSceneDelegate` forwards `windowScene` geometry updates to `IpadosWindowImpl.notifySceneGeometryChanged`, propagating `onResize` and `onConfigurationChanged` throughout active `FragmentHostFragment` / `HybridColumnFragment` / `DockWorkspaceFragment` trees.
3. **Windowed Client-Viewport `aspectRatio` & `alwaysOnTop`**:
   - Verified in [`IosAndIpadosWindowAlwaysOnTopAndAspectRatioTest`](../mobile/src/iosTest/kotlin/com/nexustools/genericui/ios/IosAndIpadosWindowAlwaysOnTopAndAspectRatioTest.kt): setting `window.setAspectRatio(16, 9)` enforces 16:9 client-viewport clamping in freeform `WindowState.NORMAL` mode, while leaving maximized and fullscreen transitions completely unconstrained, and `setAlwaysOnTop(...)` adjusts the underlying `UIWindow` level cleanly (`UIWindowLevelAlert` vs. `UIWindowLevelNormal`).
4. **Adaptive Responsive Breakpoints**:
   - Standard iPad viewports exceed [`HybridBreakpointSpec`](../common/src/commonMain/kotlin/com/nexustools/genericui/component/layout/HybridBreakpointSpec.kt)'s `600dp` tablet threshold (`tabletSmallestWidthDp = 600`) in full screen and wide Split View—expanding `DockWorkspaceFragment`, `HybridColumnFragment`, `AdaptiveNavigationScaffold`, `ResponsiveMasterDetailLayout`, and `TabbedFragment` to multi-column/desktop-tab layouts—and automatically compact to edge drawers or mobile switchers inside narrow ~320pt Slide Over panes.
5. **`ServiceWorkerExtension.isSupported == false` (Zero Simulation)**:
   - Because UIKit enforces aggressive background task suspension and does not expose desktop-style persistent daemons or system trays (`supportsSystemTray = false`), `AppleMobileServiceWorkerRuntime` sets **`ServiceWorkerExtension.isSupported = false`** and `isAutostartSupported = false` on `BackendTarget.IPadOS` (and `BackendTarget.IOS`).
   - `ServiceWorkerExtensionContributor` skips `@MainServiceWorker` codegen/registration on `IPADOS`, so `@MainServiceWorker` classes are never instantiated and zero fake background worker simulation runs. Check `ServiceWorkerExtension.isSupported` at runtime before registering or messaging persistent background workers.

---

## 4. Gradle DSL & Build / Test Tasks (`:gradle-plugin`)

Enable iPadOS alongside or independently of iOS in `build.gradle.kts`:

```kotlin
genericui {
    app {
        id = "com.example.studio"
        name = "Studio"
    }

    backends {
        ios()     // iPhone (UIDeviceFamily = 1)
        ipados()  // iPad (UIDeviceFamily = 2; alias: ipad())
        // Or presets: allMobile(), allExceptSwing(), all()
    }

    // Optional Apple icon overrides (auto-wrapped to solid white full-bleed AppIcon if omitted):
    appleIcon("src/commonMain/resources/drawable/icon_apple.svg")
    iosIcon("src/commonMain/resources/drawable/icon_iphone.svg")
    ipadosIcon("src/commonMain/resources/drawable/icon_ipad.svg") // or ipadIcon(...)

    // Optional iPadOS-specific variant config (inherits appleCommon -> mobileCommon -> iosCommon):
    ipados {
        window {
            aspectRatio(16, 9)
        }
    }
}
```

### Dual iOS + iPadOS Xcode App Generation & Tasks

- **`generateGenericUiIpadosApp`**: Emits a dedicated Xcode wrapper project under `build/genericui/ipadosApp` with **`<key>UIDeviceFamily</key><array><integer>2</integer></array>`** (`Info.plist`) and a 1024×1024 full-bleed white `AppIcon.appiconset`. When both `ios()` and `ipados()` are enabled, `GenericUiMultiplatformConfigurator` compiles separate `ipadosDebugFramework` / `ipadosReleaseFramework` Kotlin/Native frameworks (`compileIpadosKotlinIosArm64`, `linkIpadosDebugFrameworkIosSimulatorArm64`, etc.) so iOS (`UIDeviceFamily = 1`) and iPadOS (`UIDeviceFamily = 2`) never collide.
- **Lifecycle & Test Tasks**:
  - `./gradlew developmentIpados` / `./gradlew productionIpados`
  - `./gradlew developmentIpadosSimulatorArm64` / `./gradlew productionIpadosSimulatorArm64`
  - `./gradlew developmentIpadosArm64` / `./gradlew productionIpadosArm64`
  - `./gradlew testIpados` (`testIpadosSimulatorArm64`, `testIpadosArm64`)
