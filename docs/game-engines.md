# GenericUI Cross-Platform 2D & 3D Game Engines Guide (`:extensions:game:engine`, `engine2d`, `engine3d`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Master Docs Index**: [**`docs/README.md`**](./README.md)  
> **Related Guides**: [Gamepad & UI Navigation (`gamepad.md`)](./gamepad.md) · [First-Party Extensions (`extensions.md`)](./extensions.md) · [Box2D Physics (`../extensions/docs/box2d.md`)](../extensions/docs/box2d.md) · [Box3D Physics (`../extensions/docs/box3d.md`)](../extensions/docs/box3d.md) · [Three.js 3D Engine (`../extensions/docs/threejs.md`)](../extensions/docs/threejs.md) · [Canvas2D (`../extensions/docs/canvas2d.md`)](../extensions/docs/canvas2d.md)

GenericUI includes a modular three-layer game engine suite under `extensions/game/` covering pure-UI/vector games, hardware-accelerated 2D canvas games with `Box2D`, and full 3D PBR/Three.js games with `Box3D`:

| Extension Module | DSL Module | Gradle DSL Method | Transitive Dependencies | Core Capabilities |
| :--- | :--- | :--- | :--- | :--- |
| **`:extensions:game:engine`** | **`:extensions:game:engine:dsl`** | `engine()` | `:common`, `:extensions:game:gamepad` | Core `GameWorld` / `GameScene` / `SceneEntity` / `EntityBehavior` hierarchy, dedicated Game Logic Tick Thread (`PlatformGameLogicThread`), VBlank `AnimationScheduler` + 31 curves + cubic Bezier / Hermite splines / spring dampers / waves, `Skeleton2D` SVG skeletal rigging + `TwoBoneIkSolver2D`, Xbox-mapped `InputActionMap`, and pure-UI `UiStageHost` mode. |
| **`:extensions:game:engine2d`** | **`:extensions:game:engine2d:dsl`** | `engine2d()` | `:extensions:game:engine`, `:extensions:graphics:canvas2d`, `:extensions:graphics:imageprocessing`, `:extensions:physics:box2d` | `Game2DCanvasHost` hardware `Canvas2D` host, `Camera2DModule`, `Sprite2DModule`, `SpriteSheetAnimationModule`, `SvgCharacter2DModule` (`SvgCharacter2DRenderer`), `Tilemap2DModule`, `ParticleEmitter2DModule`, and `PhysicsWorld2DSubsystem` (`World2D` + `Box2DDebugDrawer`). |
| **`:extensions:game:engine3d`** | **`:extensions:game:engine3d:dsl`** | `engine3d()` | `:extensions:game:engine`, `:extensions:graphics:canvas3d`, `:extensions:graphics:threejs`, `:extensions:physics:box3d` | `Game3DCanvasHost` (`Canvas3D` + Three.js `WebGLRenderer` & `Scene`), `SceneGraph3DSubsystem`, `MeshRenderer3DModule`, lights (`DirectionalLight3DModule`, `PointLight3DModule`), cameras (`OrbitCamera3DModule`, `FollowCamera3DModule`), `PhysicsWorld3DSubsystem` (`World3D` + `Box3DDebugRenderer`), `CharacterController3DModule`, and `ProceduralHumanoidRig3D` / `SkeletalModel3DModule`. |

---

## 1. Core Game Engine (`:extensions:game:engine`)

### 1.1 Dedicated Game Logic Tick Thread vs. VBlank Render vs. UI Thread

GenericUI's core game engine cleanly separates three execution lanes via [`GameLoopScheduler`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/threading/GameLoopScheduler.kt), [`PlatformGameLogicThread`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/threading/PlatformGameLogicThread.kt), and [`VBlankAnimationDriver`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/animation/VBlankAnimationDriver.kt):

1. **Dedicated Game Logic Tick Thread (`PlatformGameLogicThread` & `GameLogicThreadDispatcher`)**: Runs `onTick(deltaSeconds)`, `FixedStepAccumulator` (`onFixedStep(fixedDeltaSeconds)` physics & deterministic gameplay with `maxDeltaSeconds` / `maxFixedStepsPerTick` spiral-of-death clamping and `interpolationAlpha`), and `onLateTick` on its own dedicated worker thread (`appleMain`, `jvmAndAndroidMain`, `linuxMain`, `mingwMain`; timed scheduler in `jsMain` / `wasmJsMain`), completely decoupled from OS windowing pauses.
2. **Hardware VBlank Render & Animation Lane (`VBlankAnimationDriver` + `AnimationScheduler`)**: Evaluates active `AnimationClip`s, `PropertyTweenAnimation`s, `LambdaAnimationClip`s, `AnimationTimelineGroup`s, and skeletal poses directly at display VBlank (`onRenderVBlank`) for jitter-free frame interpolation.
3. **UI Thread Marshaling (`GenericUiThreading.runOnUi`)**: Any mutation to GenericUI widgets inside pure-UI stage mode (`UiElementBindingModule`) automatically marshals safely to the UI thread (`isOnUiThread() == true`).

### 1.2 Scene Graph, Entity Modules, & Xbox-Mapped Input

- **Hierarchy & Modules**: `GameWorld` manages active [`GameScene`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/scene/GameScene.kt) instances containing `SceneEntity` trees (`SpatialTransform` 2D/3D local + hierarchical world transform, `TagAndLayerMask`, `EventChannel`) composed from reusable [`EntityModule`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/scene/EntityModule.kt) / [`EntityBehavior`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/scene/EntityBehavior.kt) components and registered [`WorldSubsystem`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/scene/WorldSubsystem.kt)s.
- **Input (`GameInputState` & `InputActionMap`)**: Aggregates keyboard (`KeyEvent`), pointer (`PointerEvent` / `WheelEvent`), and `:extensions:game:gamepad` (`GamepadManager`) snapshots so games query named semantic actions (`"move"`, `"jump"`, `"attack"`, `"dash"`) seamlessly across keyboard/mouse and PlayStation/Nintendo/Xbox controllers.

### 1.3 Animation Curves, 2D SVG Skeletal Rigging, & Two-Bone Inverse Kinematics

- **Parametric & Physics Curves (`AnimationCurve`)**:
  - `EasingFunction`: 31 curves (`LINEAR` plus `QUAD`, `CUBIC`, `QUART`, `QUINT`, `SINE`, `EXPO`, `CIRC`, `BACK`, `ELASTIC`, `BOUNCE` in `IN`, `OUT`, `IN_OUT`).
  - `CubicBezierCurve` (`EASE`, `EASE_IN`, `EASE_OUT`, `EASE_IN_OUT`, plus arbitrary `(x1, y1, x2, y2)` solved via Newton-Raphson + bisection).
  - `HermiteSplineCurve` (keyframe tangents or `useCatmullRomAutoTangents`), `SpringDamperCurve` (analytic underdamped/critically-damped/overdamped harmonic spring), and `WaveMathCurve` (`WaveShape.SINE`, `COSINE`, `TRIANGLE`, `DAMPED_SINE`).
- **2D SVG Skeletal Animation & 2-Bone IK (`com.nexustools.genericui.ext.engine.svg.*`)**:
  - `Skeleton2D` + hierarchical `Bone2D` transforms (`updateWorldTransforms()`, SVG export via `exportSvgMarkup()`).
  - [`HumanoidSkeleton2DFactory`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/svg/HumanoidSkeleton2DFactory.kt): constructs an articulated 11-bone humanoid rig (`pelvis`, `torso`, `head`, `upperArmL`/`R`, `forearmL`/`R`, `thighL`/`R`, `shinL`/`R`) with default `SvgBonePart` vector shapes (`SvgRectPrimitive`, `SvgCirclePrimitive`, `SvgPolygonPrimitive`, `SvgPathPrimitive`) and built-in `createWalkClip` / `createWaveClip`.
  - [`SkeletalAnimator2D`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/svg/SkeletalAnimator2D.kt) & [`TwoBoneIkSolver2D`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/svg/TwoBoneIkSolver2D.kt): cross-fade FK clip playback (`SkeletalAnimationClip2D`, `BonePoseTrack2D`) optionally driven directly by `AnimationScheduler`, layered with analytic law-of-cosines 2-bone IK foot/hand planting (`setIkTarget`, `applyToBones`, `bendDirection`, `weight`).

### 1.4 Pure-UI Stage Mode (`UiStageHost`)

[`UiStageHost`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/ui/UiStageHost.kt) + [`UiElementBindingModule`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/ui/UiElementBindingModule.kt) + [`UiCollision2DHelper`](../extensions/game/engine/src/commonMain/kotlin/com/nexustools/genericui/ext/engine/ui/UiCollision2DHelper.kt) let you mount full ECS scenes (`uiStageHost { ... }`) directly onto ordinary GenericUI containers—driving real native widgets, `ImageView` SVG skeletal rigs, and 2D AABB collisions on any backend without requiring a `Canvas2D` or `Canvas3D`.

---

## 2. 2D Hardware Canvas Engine (`:extensions:game:engine2d` + `:dsl`)

[`Game2DCanvasHost`](../extensions/game/engine2d/src/commonMain/kotlin/com/nexustools/genericui/ext/engine2d/Game2DCanvasHost.kt) mounts a `GameScene` onto a hardware-accelerated `Canvas2D` via [`Engine2DSceneRenderer`](../extensions/game/engine2d/src/commonMain/kotlin/com/nexustools/genericui/ext/engine2d/Engine2DSceneRenderer.kt):

- **Cameras & Layers**: `Camera2DModule` (zoom, follow offset, viewport framing), `Tilemap2DModule` (grid maps & tile palettes), `Sprite2DModule`, `SpriteSheetAnimationModule`, and `ParticleEmitter2DModule` (`Particle2D`).
- **Articulated 2D Vector Characters**: [`SvgCharacter2DModule`](../extensions/game/engine2d/src/commonMain/kotlin/com/nexustools/genericui/ext/engine2d/SvgCharacter2DModule.kt) renders any `Skeleton2D` + `SkeletalAnimator2D` hierarchy straight into `Canvas2D` via `SvgCharacter2DRenderer` (with optional `drawBoneOverlay` IK/skeleton debugging).
- **2D Box2D Integration**: [`PhysicsWorld2DSubsystem`](../extensions/game/engine2d/src/commonMain/kotlin/com/nexustools/genericui/ext/engine2d/PhysicsWorld2DSubsystem.kt), `RigidBody2DModule`, `Collider2DModule`, and `Joint2DModule` synchronize `:extensions:physics:box2d` (`World2D`) rigid bodies with `SceneEntity` transforms automatically on each fixed step and draw debug wireframes via `Box2DDebugDrawer`.

```kotlin
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.ext.box2d.BodyType2D
import com.nexustools.genericui.ext.box2d.Vec2
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import com.nexustools.genericui.ext.engine.animation.AnimationWrapMode
import com.nexustools.genericui.ext.engine.svg.HumanoidSkeleton2DFactory
import com.nexustools.genericui.ext.engine2d.dsl.*
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.style.color.RgbaColor

val root = VStack()
val gameHost = root.game2DCanvas(width = 1280, height = 720, autoStart = true) {
    clearColor = RgbaColor.fromHex("#0F172A")
    physics2D(gravity = Vec2(0f, 980f), debugDraw = true)

    gamepadBindings {
        axis2D("move")
        button("jump", GamepadButton.SOUTH)
        key("jump", "Space")
    }

    entity(name = "Player", tag = "Hero") {
        position2D(320f, 200f)
        rigidBody2D(bodyType = BodyType2D.DYNAMIC, fixedRotation = true)
        boxCollider2D(width = 36f, height = 72f)
        val rig = svgCharacter2D(drawBoneOverlay = false) {
            animator.play(HumanoidSkeleton2DFactory.createWalkClip())
        }
        behavior {
            onTick = { dt ->
                // Read input actions or update two-bone IK foot placement via rig.animator.setIkTarget(...)
            }
        }
    }
}
```

---

## 3. 3D Three.js + Box3D Engine (`:extensions:game:engine3d` + `:dsl`)

[`Game3DCanvasHost`](../extensions/game/engine3d/src/commonMain/kotlin/com/nexustools/genericui/ext/engine3d/Game3DCanvasHost.kt) bridges `GameScene` onto a hardware-accelerated `Canvas3D` (`GpuBackendType.OPENGL_ES` / `VULKAN` — ANGLE on Windows, **MoltenVK** on macOS/iOS/iPadOS, native Vulkan/EGL on Linux/Android, WebGL2/WebGPU on Web) and `:extensions:graphics:threejs`'s `WebGLRenderer` + `Scene`:

- **Scene Graph, PBR Meshes, Lights, & Cameras**: `SceneGraph3DSubsystem`, `Node3DBindingModule`, `MeshRenderer3DModule`, `DirectionalLight3DModule`, `PointLight3DModule`, `OrbitCamera3DModule`, and `FollowCamera3DModule`.
- **3D Physics & Character Control**: [`PhysicsWorld3DSubsystem`](../extensions/game/engine3d/src/commonMain/kotlin/com/nexustools/genericui/ext/engine3d/PhysicsWorld3DSubsystem.kt) (`World3D` + `Box3DDebugRenderer`), `RigidBody3DModule`, `Collider3DModule` (`boxCollider3D`, `sphereCollider3D`), `Joint3DModule`, and [`CharacterController3DModule`](../extensions/game/engine3d/src/commonMain/kotlin/com/nexustools/genericui/ext/engine3d/CharacterController3DModule.kt) (slope limit `maxSlopeAngleRad`, step height `maxStepHeight`, ground/coyote jump state).
- **Articulated 3D Rigs**: [`ProceduralHumanoidRig3D`](../extensions/game/engine3d/src/commonMain/kotlin/com/nexustools/genericui/ext/engine3d/ProceduralHumanoidRig3D.kt) and [`SkeletalModel3DModule`](../extensions/game/engine3d/src/commonMain/kotlin/com/nexustools/genericui/ext/engine3d/SkeletalModel3DModule.kt) create/animate multi-joint 3D character bone hierarchies cleanly inside DSL scenes via `humanoidRig3D()`.

```kotlin
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.ext.box3d.BodyType3D
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.engine.math.Vec3f
import com.nexustools.genericui.ext.engine3d.dsl.*
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.ext.gpu.GpuBackendType

val container = VStack()
val arenaHost = container.game3DCanvas(
    width = 1280,
    height = 720,
    backend = GpuBackendType.OPENGL_ES,
    autoStart = true
) {
    background(0x0B1020)
    physics3D(gravity = Vec3(0f, -9.81f, 0f), debugDraw = true)

    gamepadBindings {
        axis2D("move")
        button("jump", GamepadButton.SOUTH)
    }

    val player = entity(name = "Fighter") {
        position(0f, 1.5f, 0f)
        rigidBody3D(bodyType = BodyType3D.DYNAMIC, lockRotations = true)
        boxCollider3D(halfWidth = 0.4f, halfHeight = 0.9f, halfDepth = 0.4f)
        characterController3D(maxStepHeight = 0.35f)
        humanoidRig3D(colorHex = 0x60A5FA, scale = 1.0f)
    }

    entity(name = "FollowCam") {
        followCamera3D(target = player, offset = Vec3f(0f, 3.5f, 7f), damping = 8f)
    }
    entity(name = "Sun") {
        position(10f, 18f, 10f)
        directionalLight3D(colorHex = 0xFFF7ED, intensity = 1.25f)
    }
}
```
