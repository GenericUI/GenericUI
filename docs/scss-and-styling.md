# GenericUI SCSS, `style.scss` vs. `theme.scss`, & Backend `@` Directives Guide

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Components & Styling (`components-and-docking.md`)](./components-and-docking.md) · [Application & `BackendTarget` (`application.md`)](./application.md) · [Resources (`resources.md`)](./resources.md) · [Gradle Plugin (`gradle-plugin.md`)](./gradle-plugin.md) · [Web Backend (`../web/docs/web-backend.md`)](../web/docs/web-backend.md) · [Platform Backends (`backends.md`)](./backends.md)

This guide is the authoritative reference for GenericUI's SCSS/CSS styling architecture across all **9 target backends** (`Windows`, `MacOS`, `Gtk`, `Kde`, `Swing`, `Android`, `IOS`, `IPadOS`, `Web`), covering `style.scss` vs. `theme.scss`, backend-scoped `@` directives, Web CSS stripping/unwrapping, O(1) `BackendStyleMask` runtime partitioning, and live SCSS tooling.

---

## 1. `style.scss` vs. `theme.scss` Architecture

GenericUI separates **structural/behavioral component rules** from **consumer palette tokens** so `:common` and first-party extensions remain 100% library-generic:

| Aspect | `style.scss` (`:common`, Extensions, & Apps) | `theme.scss` (Library Templates & Consumer Theme) |
| :--- | :--- | :--- |
| **Purpose** | Structural layout (`display`, `position`, `z-index`, `pointer-events`, `transform`, `padding`, `border-radius`), interactive states (`:hover`, `:focus`, `:active`, `:disabled`), cursors, and `@keyframes` animations (e.g. `.hidden` fades). | Color palettes, focus rings, accent fills, and theme-derived states consuming `$primary`, `$secondary`, `$accent`, `$background`, `$surface`, `$surface-elevated`, `$text`, `$text-primary`, `$text-secondary`, `$text-muted`, `$border`, `$success`, `$danger`, `$error`, `$warning`, `$info`, `$light`, `$dark`, `$on-primary`. |
| **Library Artifacts (`:common`, `:extensions:*`)** | Compiled into module `PrecompiledStyleSheet` / CSS resources (`Resource.style.style`) and registered via `StyleSheetRegistry` / `ThemeStyleSheetRegistry`. **Must never reference `$theme` variables.** | Packaged **raw and uncompiled** inside `META-INF/genericui/themes/<scopeId>.scss` and `META-INF/genericui/module-resources/<scopeId>.json`. Prebuilt libraries **never** bake compiled `theme.scss` output into `.jar`/`.klib` artifacts. |
| **Consumer App Compilation** | Consumer `.scss` files in `src/commonMain/resources/styles/` compile via `compileGenericUiSass` into `Resource.style.<name>` (`PrecompiledStyleSheet` Kotlin objects on `:system` and `.css` bundles on `:web`). | Consumer app's `compileGenericUiSass` (`GenericUiSassCompileTask`) extracts upstream library `theme.scss` templates + extension SPI chunks + optional consumer `theme.scss`, substitutes `genericui { theme { ... } }` light/dark palettes, and emits `GenericUiGeneratedThemeStyleSheet` (`:system`) and `gui/genericui-theme.css` (`:web`). |

### Styling Invariants
1. **Zero Direct Static Component Styles in Kotlin**: Static visuals, hover/focus/active/disabled states, `@keyframes`, and layout positioning live in `.scss` stylesheets—only genuinely dynamic per-frame/per-value percentages (such as `StyledSlider` progress width and knob `left`) are updated in Kotlin.
2. **Zero String-Keyed Style APIs**: `:common` forbids string-keyed `setStyleProperty(String, String)` or raw declaration maps. Runtime style mutations use strongly-typed [`ComponentStyle`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/ComponentStyle.kt) (`component.getStyle()`) and [`ComponentBootstrap`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/bootstrap/ComponentBootstrap.kt) (`component.getBootstrap()`).
3. **System Backend Widget Restraints**: On all 8 system targets (`Windows`, `MacOS`, `Gtk`, `Kde`, `Swing`, `Android`, `IOS`, `IPadOS`), full CSS/SCSS aesthetics (backgrounds, gradients, borders, radii, `box-shadow`, `backdrop-filter`) apply to `StyledContainer`, `StyledComponent`, `StyledButton`, `Composite` (including `StyledSlider` and `SeekBar`), `:extensions:ui:alien`, and semantic surfaces; typography components honor font/color rules; standard OS controls retain native platform chrome unless styled via `Button` `BootstrapType` or `Alien`.

---

## 2. Backend-Scoped `@` Directives Reference

Backend `@` directives scope CSS/SCSS rules to specific [`BackendTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt)s directly inside shared `commonMain` stylesheets—eliminating `-webkit-*` prefix clutter on native targets, desktop-only titlebar/hover rules on mobile/web, and mobile touch-target ergonomics on desktop.

### 2.1 Concrete Backend Directives (9 Targets)

Each concrete directive maps 1:1 to a [`BackendTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt) enum entry:

| Directive | `BackendTarget` | `BackendTarget.id` | Scope |
| :--- | :--- | :--- | :--- |
| **`@windows`** | `BackendTarget.Windows` | `"windows"` | Windows Win32 (`ComCtl32 v6`) native desktop |
| **`@macos`** | `BackendTarget.MacOS` | `"macos"` | macOS AppKit native desktop |
| **`@gtk`** | `BackendTarget.Gtk` | `"gtk"` | Linux GTK4 / Libadwaita native desktop |
| **`@kde`** | `BackendTarget.Kde` | `"kde"` | Linux KDE Plasma (`KF6` / Qt6) native desktop |
| **`@swing`** | `BackendTarget.Swing` | `"swing"` | JVM Swing / AWT desktop |
| **`@android`** | `BackendTarget.Android` | `"android"` | Android mobile / tablet |
| **`@ios`** | `BackendTarget.IOS` | `"ios"` | iPhone iOS (UIKit) |
| **`@ipados`** | `BackendTarget.IPadOS` | `"ipados"` | iPad iPadOS (UIKit / Stage Manager) |
| **`@web`** | `BackendTarget.Web` | `"web"` | Web browser (HTML5 DOM / Wasm / JS) |

### 2.2 Group Backend Directives (13 Aliases Mapped to `BackendTarget`)

Each group directive mirrors the multi-target capability properties on [`BackendTarget`](../common/src/commonMain/kotlin/com/nexustools/genericui/enums/system/BackendTarget.kt):

| Directive | `BackendTarget` Property | Matched Concrete Targets |
| :--- | :--- | :--- |
| **`@system`** | `isSystem` | `Windows`, `MacOS`, `Gtk`, `Kde`, `Swing`, `Android`, `IOS`, `IPadOS` *(all except `Web`)* |
| **`@desktop`** | `isDesktop` | `Windows`, `MacOS`, `Gtk`, `Kde`, `Swing` |
| **`@native-desktop`** | `isNativeDesktop` | `Windows`, `MacOS`, `Gtk`, `Kde` |
| **`@linux`** | `isLinux` | `Gtk`, `Kde` |
| **`@mobile`** | `isMobile` | `Android`, `IOS`, `IPadOS` |
| **`@apple`** | `isApple` | `MacOS`, `IOS`, `IPadOS` |
| **`@ios-family`** / **`@uikit`** | `isIosFamily` | `IOS`, `IPadOS` |
| **`@unix`** | `isUnix` | `MacOS`, `Gtk`, `Kde`, `Android`, `IOS`, `IPadOS` |
| **`@posix`** | `isPosix` | `Windows`, `MacOS`, `Gtk`, `Kde`, `IOS`, `IPadOS` |
| **`@native`** | `isNative` | `Windows`, `MacOS`, `Gtk`, `Kde`, `IOS`, `IPadOS` |
| **`@jvm`** | `isJvm` | `Swing`, `Android` |
| **`@touch`** / **`@touch-primary`** | `isTouchPrimary` | `Android`, `IOS`, `IPadOS` |
| **`@browser`** | `isBrowser` | `Web` |

---

## 3. Compound Boolean Expressions: `@backend(...)`, `@platform(...)`, & `@target(...)`

When a single concrete or group directive isn't expressive enough, use **`@backend(...)`**, **`@platform(...)`**, or **`@target(...)`** with boolean expressions:

- **Identifiers**: Any of the 9 concrete target names (`windows`, `macos`, `gtk`, `kde`, `swing`, `android`, `ios`, `ipados`, `web`) or 13 group names (`system`, `desktop`, `native-desktop`, `linux`, `mobile`, `apple`, `ios-family`, `uikit`, `unix`, `posix`, `native`, `jvm`, `touch`, `touch-primary`, `browser`).
- **Operators**:
  - **`not`** — unary negation (highest precedence)
  - **`and`** — logical intersection
  - **`or`** or **`,`** — logical union (comma acts as `or`, matching CSS media-query lists)
  - **`(...)`** — parenthesized grouping

```scss
// Native desktop backends excluding Swing
@backend(desktop and not swing) {
    .gui-overlay-titlebar-blurred {
        backdrop-filter: blur(22px) saturate(1.4);
    }
}

// Apple platforms or Windows
@platform(apple or windows) {
    .custom-toolbar {
        padding-top: 4px;
    }
}

// Comma-separated target list (equivalent to `linux or macos`)
@target(linux, macos) {
    .scroll-gutter {
        width: 10px;
    }
}

// Parenthesized boolean expression
@backend((macos or ios-family) and not ipados) {
    .compact-header {
        height: 44px;
    }
}
```

---

## 4. Nesting, Composition, & Zero Specificity Side-Effects

Backend `@` directives are completely orthogonal to CSS selector matching and compose cleanly with every SCSS feature:

1. **Top-Level Grouping & Nested Selectors**:
   Wrap whole desktop-only blocks (such as `.gui-overlay-titlebar*` or `.pip-restore-btn-titlebar`), or nest a directive directly inside a component rule to isolate platform declarations without duplicating the parent selector:
   ```scss
   genericui:styled-slider,
   .styled-slider {
       height: 24px;
       user-select: none;
       @web {
           -webkit-user-select: none;
       }

       @mobile {
           height: 36px;

           .styled-slider-knob {
               width: 20px;
               height: 20px;
           }
       }
   }
   ```
2. **Nesting Directives Within Directives**:
   Nested backend blocks intersect (`and`) their masks—for example, `@macos` nested inside `@desktop` targets `Desktop ∩ MacOS = MacOS`:
   ```scss
   @desktop {
       .gui-overlay-titlebar-native-controls {
           height: 28px;
       }

       @macos {
           .gui-overlay-titlebar-native-controls .gui-overlay-titlebar-leading {
               padding-left: 78px;
           }
       }
   }
   ```
3. **Composition with `@media`, `@keyframes`, `&`, `>`, & `@mixin`/`@include`**:
   - Backend directives compose freely inside or around `@media` queries (both conditions must hold: backend match **and** viewport breakpoint).
   - `&` parent selectors, `>` direct-child combinators, and `@mixin`/`@include` expansions inside a backend block inherit the active backend mask cleanly.
   - `@keyframes` blocks can be scoped or referenced inside backend blocks.
4. **Zero Specificity Side-Effects**:
   Backend `@` directives do **not** add any selector weight (`id`, `class`, or `tag` count). A rule inside `@mobile { .player-circle-btn { ... } }` has the exact same CSS specificity (`0,1,0`) as `.player-circle-btn { ... }`, so standard CSS source order determines which wins when both match (place platform overrides after base universal defaults).

---

## 5. Automatic Web CSS Stripping & `@web` / `@browser` Unwrapping

Browser CSS engines do not understand `@desktop`, `@macos`, `@mobile`, or `@web` at-rules. GenericUI's build pipeline automatically emits 100% standards-compliant browser CSS:

- **`compileGenericUiSass` (`GenericUiSassCompileTask`) & Web Packaging (`GenericUiWebDistributionTask`)**:
  When generating Web `.css` bundles (`generateWebCss = true`), consumer stylesheets, and `gui/genericui-theme.css`:
  1. Rules whose resolved backend mask excludes `BackendTarget.Web` (for example `@desktop`, `@native-desktop`, `@macos`, `@mobile`, `@system`, or `@backend(not web)`) are **completely stripped** from the emitted `.css` output—shrinking browser payloads and keeping desktop/mobile chrome rules out of the DOM.
  2. Rules inside `@web { ... }`, `@browser { ... }`, or compound expressions matching `Web` (such as `@backend(web or macos)`) are **unwrapped** into plain CSS rules (preserving any enclosing `@media` queries), so browser-only declarations such as `-webkit-user-select: none;`, `-webkit-backdrop-filter: blur(12px);`, or `.pip-web-floating-root` become standard top-level CSS rules.
- **`WebStyleSheetManager`**:
  [`WebStyleSheetManager`](../web/src/commonMain/kotlin/com/nexustools/genericui/web/WebStyleSheetManager.kt) mounts precompiled/preloaded `.css` bundles, `gui/genericui-theme.css` (`<link id="genericui-build-theme-css">`), and dynamic runtime theme overrides (`<style id="genericui-runtime-theme-css">`) into `<head>` (never inside `<genericui:app>`).

---

## 6. Runtime Evaluation: `BackendStyleMask`, `BlinkRuleBucketMap.forBackend(...)`, & Tooling

### 6.1 `BackendStyleMask` Bitmask & `SassCompiler` / `SassKotlinPrecompiler`
- Each rule compiled by [`SassCompiler`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/sass/SassCompiler.kt) and [`SassKotlinPrecompiler`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/sass/SassKotlinPrecompiler.kt) carries an immutable **`BackendStyleMask`** bitmask (1 bit per concrete `BackendTarget`, with `ALL` set when no backend directive applies).
- When `SassCompiler` compiles without a fixed `targetBackend` (`targetBackend = null`, the default for shared KMP stylesheets and `:common` tests), it **preserves** all rules along with their `BackendStyleMask` so a single `PrecompiledStyleSheet` / `CssStyleSheet` serves every backend at runtime. When a concrete `targetBackend` is supplied (such as Web CSS generation), non-matching rules are pruned at compile time.
- [`CssStyleSheet`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/css/CssStyleSheet.kt) (`resolveStyleForComponent`) evaluates the active backend via `Application.current?.backendTarget ?: GenericUI.activeFactory?.backendTarget`, ensuring rules match only when applicable to the active platform.

### 6.2 O(1) `BlinkRuleBucketMap.forBackend(...)` Pre-Partitioning in `:system`
- On all 8 native/JVM OS backends, `:system`'s [`BlinkRuleBucketMap`](../system/src/commonMain/kotlin/com/nexustools/genericui/system/style/BlinkRuleBucketMap.kt) provides **`forBackend(target: BackendTarget)`** to pre-partition a stylesheet's rules once for the running backend into `idBuckets`, `classBuckets`, `tagBuckets`, and `universalBucket`.
- Because incompatible rules are excluded when building the per-backend `BlinkRuleBucketMap`, per-element style resolution inside `BlinkStyleEngine` executes in **O(1)** bucket lookup with **zero** per-rule backend mask checks in hot layout/hover passes.

### 6.3 Live Editing & IDE Support (`LiveScssStyleEngine` & `ScssLanguageSupport`)
- [`LiveScssStyleEngine`](../common/src/commonMain/kotlin/com/nexustools/genericui/style/sass/LiveScssStyleEngine.kt) supports all concrete, group, and compound backend `@` directives for real-time in-app theme/style previews.
- [`ScssLanguageSupport`](../extensions/ui/codeeditor/src/commonMain/kotlin/com/nexustools/genericui/ext/codeeditor/ScssLanguageSupport.kt) in `:extensions:ui:codeeditor` provides autocomplete suggestions, syntax highlighting, and validation diagnostics for all 9 concrete directives, all 13 group directives, and `@backend(...)` / `@platform(...)` / `@target(...)`.
