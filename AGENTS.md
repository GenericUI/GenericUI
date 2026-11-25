# GenericUI — Engineering & Agent Rules (`AGENTS.md`)

Mandatory rules and isolation invariants for engineers and AI agents working in `GenericUI`. For system topology, pipeline mechanics, and runtime architecture, read [**`ARCHITECTURE.md`**](ARCHITECTURE.md) and [**`docs/README.md`**](docs/README.md).

---

## 1. File Layout & API Discipline

1. **One Public Type Per `.kt` File**: Every Kotlin source file exports at most one public `class`, `interface`, `object`, or `enum class` named identically to `<TypeName>.kt` (excluding classless `:dsl` builder files). Extract helper enums, data classes, configs, and listener interfaces into dedicated files.
2. **Single Canonical API (Zero Shims or Aliases)**: Never add `typealias`es, duplicate wrapper getters/functions, or deprecated backwards-compatibility shims when refactoring. Update all call sites directly to one canonical API.
3. **Encapsulate `*NativeImpl`**: Native peer interfaces, classes, fields, and lookup helpers must remain `internal`, `protected`, or `private`—never `public` on consumer-facing APIs.
4. **Native-Wrapper-First Retained Peers**: GenericUI acts as a thin wrapper over the host platform's native toolkit whenever possible, wrapping genuine system widgets, native control tinting/theming APIs, and native system visual/compositor effects in each `<Backend><Component>NativeImpl`. Use custom or alien rendering strictly when the backend lacks native support for a required capability or when `:extensions:ui:alien` is explicitly used; never replace native controls with monolithic canvas painters, offscreen sprite dumpers, or giant render switches.

---

## 2. Fail-Fast Diagnostics, Zero Fast-Paths, & Honest Testing

1. **Zero Silent Error Consumption**: Never swallow failures with `runCatching`, empty `catch` blocks, or silent `?.` no-ops on uninitialized peers or unsupported features. Always fail fast using typed `GenericUiException` subclasses.
2. **Pre-Mount Purity**: Pure Kotlin widget constructors and pre-mount property setters must work without an active `Application`/`Window`; only imperative native operations and capability queries throw `NativeUninitializedException` when uninitialized.
3. **Zero App-, Class-, ID-, Label-, or Path-Specific Fast-Paths**: Never branch layout, rendering, scrolling, styling, or binary resolution on consumer class names, IDs, CSS selectors, visible text, or local filesystem paths. Fix the general layout, styling, or input subsystem instead.
4. **Honest Tests**: Every test must enforce explicit timeouts, exercise real code paths without gaming or mocking away the contract under test, and never weaken or skip assertions to pass.
5. **macOS Test Process Dock Isolation**: Kotlin/Native macOS test binaries (`test.kexe`, `--ktest_*`, or `visualTestMode`) must stay at `NSApplicationActivationPolicyAccessory`—never promote them to `NSApplicationActivationPolicyRegular` or call `activateIgnoringOtherApps(true)`—so automated tests never leave `test.kexe` icons in the macOS Dock or steal foreground focus. Test production activation-policy selection via pure policy helpers (e.g., `MacosNativeWindowHost.computeProductionActivationPolicy()`).

---

## 3. Module, Backend, & Extension Isolation

1. **Zero Extension Leakage into Core**: Core modules (`:common`, `:system`, `:jvm`, `:apple`, `:desktop`, `:mobile`, `:web`, `:gradle-plugin`, `:idea-plugin`) must never import, reference, or hardcode any extension class, tag, or behavior. Wire extensions strictly via core registries/hooks (`GenericUiExtensionRegistry`) and build-time contributor SPIs (`GenericUiExtensionContributor`).
2. **Platform-Agnostic `:common`**: Never place platform code, `BackendTarget` branching, or target string parsing inside `:common`. Concrete backend factories return capability flags as compile-time constants.
3. **Strict Sibling & Toolkit Isolation**: Backends only share upward through designated shared source sets; sibling implementations must never cross-import, and optional platform schema or service queries must validate availability before access.
4. **Extension Dependencies & DSL Scope**: Extensions depend only on `:common` and explicitly declared upstream extensions. Generic layout/style/Bootstrap DSL builders belong strictly in `:extensions:ui:dsl` (`:common`-only); extension builders belong in each extension's own `:dsl` submodule. Embedded native JS runtimes apply strictly to desktop capabilities that require them—never mobile or web.

---

## 4. Styling, Themes, `BootstrapType`, & Component Composition

1. **`style.scss`-First & Zero Static Inline Kotlin Styles**: Define static visuals, layout positioning, interactive states, transitions, and `@keyframes` animations in `style.scss` (`src/commonMain/resources/styles/`). Never assign static, state, or theme-derived visuals directly on components in Kotlin; update only genuinely runtime-dynamic per-frame/per-value attributes imperatively, and drive state changes by toggling semantic CSS classes.
2. **Typed Style Bitmasks**: Use strongly-typed `ComponentStyle` and `ComponentBootstrap` only—never expose string-keyed declaration maps or CSS string APIs in `:common`/system targets (string translation belongs solely in `:web`), and never attach pseudo-state rules inside inline styles. Keep user CSS class collections isolated from internal enum-mapped classes.
3. **Library Raw `theme.scss` Templates vs. Consumer-Only Compilation**: GenericUI libraries remain prebuilt and generic; libraries must never compile or embed consumer-resolved `theme.scss` stylesheets or accept hardcoded theme accent overrides in Kotlin constructors. Library modules package only raw uncompiled `theme.scss` templates in artifact metadata, and consumer builds compile resolved theme stylesheets solely into the consumer application.
4. **Strict `BootstrapType` Policy**: Among control components, **only `Button` and subclasses of `Button`** may expose or apply `BootstrapType`, rendered via the backend's native control tinting and theming APIs wherever supported and falling back to custom drawing strictly when the toolkit lacks native tint support; all other controls must neither accept `BootstrapType` in constructors nor apply `BootstrapType` coloring on system backends. Non-control Bootstrap semantic components support `BootstrapType` across all backends using native primitives or custom styled widgets only where equivalent native controls do not exist. Styled primitives and `:extensions:ui:alien` surfaces follow `AestheticStylePolicy`.
5. **`Composite` Encapsulation**: Build multi-part reusable widgets by extending `Composite` with styled primitive children (`StyledContainer`, `StyledComponent`, `StyledButton`), zero custom `*NativeImpl` classes, and zero external container exposure.
6. **Zero Default Tooltips**: Leave `tooltipText` `null` by default everywhere; tooltips are strictly opt-in by consumers and never synthesized from labels.

---

## 5. Threading, Async I/O, & Multi-Agent Workflow

1. **UI vs. Logic Thread Discipline**: Perform native widget mutations, layout, and rendering strictly on the UI thread; dispatch consumer callbacks and timers on the Logic thread when split threading is active. Never block either thread with synchronous I/O or sleep waits.
2. **Async-First I/O**: Expose non-blocking `suspend` and callback-driven APIs across `Readable`, `Writable`, `Stream`, binary codecs, storage, networking, and databases.
3. **Subagent Coordination & Style Integrity**: Run parallel subagents with strictly non-overlapping directory/module scopes, preserve existing unrelated comments/docstrings, and enforce clean, shared helpers without copy-paste duplication.
