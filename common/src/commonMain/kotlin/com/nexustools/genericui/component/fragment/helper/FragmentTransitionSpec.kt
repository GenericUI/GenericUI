package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment

/**
 * Configures enter and exit transitions between outgoing and incoming [Fragment]s
 * inside [FragmentHostFragment] and [FragmentHostSystem].
 */
data class FragmentTransitionSpec(
    val preset: FragmentTransitionPreset = FragmentTransitionPreset.NONE,
    val durationMs: Int = 500,
    val activationDelayMs: Int = 50,
    val keepOutgoingMountedDuringExit: Boolean = false,
    val onEnterBegin: ((fragment: Fragment, durationMs: Int) -> Unit)? = null,
    val onEnterActivate: ((fragment: Fragment, durationMs: Int) -> Unit)? = null,
    val onExitBegin: ((fragment: Fragment, durationMs: Int) -> Unit)? = null,
    val onExitComplete: ((fragment: Fragment) -> Unit)? = null
) {
    companion object {
        /** Immediate switch with no enter/exit transition delay. */
        val None: FragmentTransitionSpec = FragmentTransitionSpec(
            preset = FragmentTransitionPreset.NONE,
            durationMs = 0,
            activationDelayMs = 0,
            keepOutgoingMountedDuringExit = false
        )

        /** Smooth opacity cross-fade between outgoing and incoming fragments. */
        fun Crossfade(
            durationMs: Int = 250,
            activationDelayMs: Int = 30
        ): FragmentTransitionSpec = FragmentTransitionSpec(
            preset = FragmentTransitionPreset.CROSSFADE,
            durationMs = durationMs,
            activationDelayMs = activationDelayMs,
            keepOutgoingMountedDuringExit = true,
            onEnterBegin = { fragment, dur ->
                fragment.removeClass("destroyed")
                fragment.addClass("created")
                fragment.pointerEventsEnabled = false
                fragment.zIndex = 0
                fragment.transition = "opacity ${dur}ms ease"
                fragment.opacity = 0f
                fragment.onBeginEnterTransition(dur)
            },
            onEnterActivate = { fragment, dur ->
                fragment.removeClass("created")
                fragment.pointerEventsEnabled = true
                fragment.zIndex = 5
                fragment.transition = "opacity ${dur}ms ease"
                fragment.opacity = 1f
                fragment.onActivateEnterTransition(dur)
            },
            onExitBegin = { fragment, dur ->
                fragment.removeClass("created")
                fragment.addClass("destroyed")
                fragment.pointerEventsEnabled = false
                fragment.zIndex = 0
                fragment.transition = "opacity ${dur}ms ease"
                fragment.opacity = 0f
                fragment.onBeginExitTransition(dur)
            },
            onExitComplete = { fragment ->
                fragment.onFinishExitTransition()
            }
        )

        /**
         * Reproduces the `fadein/expand` + `fadeout/shrink` transition lifecycle:
         * - Incoming fragment starts with `.created`, `opacity = 0f`, `zIndex = 0`, and invokes
         *   [Fragment.onBeginEnterTransition] (shrinking `.menu` to `scale(0, 0)` on [CenteredMenuPageFragment]).
         * - After [activationDelayMs], removes `.created`, sets `zIndex = 5`, `opacity = 1f` with
         *   `transition = "opacity ${durationMs}ms ease"`, and invokes [Fragment.onActivateEnterTransition]
         *   (expanding `.menu` to `scale(s, s)`).
         * - Outgoing fragment receives `.destroyed`, `zIndex = 0`, `opacity = 0f`, invokes
         *   [Fragment.onBeginExitTransition] (shrinking `.menu` to `scale(0, 0)`), and remains mounted
         *   in the stage container for the full [durationMs] before [onExitComplete] / destroy.
         */
        fun FadeExpandShrink(
            durationMs: Int = 500,
            activationDelayMs: Int = 50
        ): FragmentTransitionSpec = FragmentTransitionSpec(
            preset = FragmentTransitionPreset.FADE_EXPAND_SHRINK,
            durationMs = durationMs,
            activationDelayMs = activationDelayMs,
            keepOutgoingMountedDuringExit = true,
            onEnterBegin = { fragment, dur ->
                fragment.removeClass("destroyed")
                fragment.addClass("created")
                fragment.pointerEventsEnabled = false
                fragment.zIndex = 0
                fragment.transition = "opacity ${dur}ms ease"
                fragment.opacity = 0f
                fragment.onBeginEnterTransition(dur)
            },
            onEnterActivate = { fragment, dur ->
                fragment.removeClass("created")
                fragment.pointerEventsEnabled = true
                fragment.zIndex = 5
                fragment.transition = "opacity ${dur}ms ease"
                fragment.opacity = 1f
                fragment.onActivateEnterTransition(dur)
            },
            onExitBegin = { fragment, dur ->
                fragment.removeClass("created")
                fragment.addClass("destroyed")
                fragment.pointerEventsEnabled = false
                fragment.zIndex = 0
                fragment.transition = "opacity ${dur}ms ease"
                fragment.opacity = 0f
                fragment.onBeginExitTransition(dur)
            },
            onExitComplete = { fragment ->
                fragment.onFinishExitTransition()
            }
        )

        /** Horizontal slide + cross-fade transition between outgoing and incoming fragments. */
        fun SlideHorizontal(
            durationMs: Int = 300,
            activationDelayMs: Int = 30
        ): FragmentTransitionSpec = FragmentTransitionSpec(
            preset = FragmentTransitionPreset.SLIDE_HORIZONTAL,
            durationMs = durationMs,
            activationDelayMs = activationDelayMs,
            keepOutgoingMountedDuringExit = true,
            onEnterBegin = { fragment, dur ->
                fragment.removeClass("destroyed")
                fragment.addClass("created")
                fragment.pointerEventsEnabled = false
                fragment.zIndex = 0
                fragment.transition = "opacity ${dur}ms ease, transform ${dur}ms ease"
                fragment.opacity = 0f
                fragment.transform = "translateX(24px)"
                fragment.onBeginEnterTransition(dur)
            },
            onEnterActivate = { fragment, dur ->
                fragment.removeClass("created")
                fragment.pointerEventsEnabled = true
                fragment.zIndex = 5
                fragment.transition = "opacity ${dur}ms ease, transform ${dur}ms ease"
                fragment.opacity = 1f
                fragment.transform = "translateX(0px)"
                fragment.onActivateEnterTransition(dur)
            },
            onExitBegin = { fragment, dur ->
                fragment.removeClass("created")
                fragment.addClass("destroyed")
                fragment.pointerEventsEnabled = false
                fragment.zIndex = 0
                fragment.transition = "opacity ${dur}ms ease, transform ${dur}ms ease"
                fragment.opacity = 0f
                fragment.transform = "translateX(-24px)"
                fragment.onBeginExitTransition(dur)
            },
            onExitComplete = { fragment ->
                fragment.onFinishExitTransition()
            }
        )
    }
}
