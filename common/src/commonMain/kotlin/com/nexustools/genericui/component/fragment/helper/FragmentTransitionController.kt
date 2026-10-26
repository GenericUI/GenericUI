package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.fragment.FragmentState

/**
 * Coordinates enter and exit transitions between outgoing and incoming [Fragment]s
 * inside [FragmentHostSystem] using `:common`'s lifecycle-scoped `setTimeout` / `clearTimeout`,
 * re-applying CSS stylesheets at each transition phase (`.created`, active, `.destroyed`)
 * and cleaning up outgoing fragments when their exit timer completes or is superseded.
 */
class FragmentTransitionController(
    val stageContainer: Container
) : Disposable {

    private class ExitingEntry(
        val fragment: Fragment,
        val timerId: Int,
        val completeAction: () -> Unit
    )

    private var enterActivationTimerId: Int = -1
    private var pendingEnterFragment: Fragment? = null
    private var pendingEnterAction: (() -> Unit)? = null
    private val exitingEntries = linkedMapOf<Fragment, ExitingEntry>()

    /** `true` while an enter activation timer or an outgoing exit timer is pending. */
    val isTransitioning: Boolean
        get() = enterActivationTimerId != -1 || exitingEntries.isNotEmpty()

    /** Outgoing fragments currently kept mounted in [stageContainer] during their exit transition. */
    val exitingFragments: List<Fragment>
        get() = exitingEntries.keys.toList()

    /**
     * Runs a transition from [outgoing] to [incoming] according to [spec].
     */
    fun runTransition(
        outgoing: Fragment?,
        incoming: Fragment,
        spec: FragmentTransitionSpec,
        destroyOutgoing: Boolean,
        mountIncoming: (Fragment) -> Unit,
        finalizeOutgoing: (Fragment, Boolean) -> Unit,
        applyStylesTo: (Fragment) -> Unit,
        onIncomingActivated: (Fragment) -> Unit
    ) {
        // Cancel any pending enter activation from an earlier transition
        if (enterActivationTimerId != -1) {
            stageContainer.clearTimeout(enterActivationTimerId)
            enterActivationTimerId = -1
        }
        pendingEnterFragment = null
        pendingEnterAction = null

        // If the incoming fragment was mid-exit, cancel its exit timer so it is not destroyed/removed
        val incomingExiting = exitingEntries.remove(incoming)
        if (incomingExiting != null) {
            if (incomingExiting.timerId != -1) {
                stageContainer.clearTimeout(incomingExiting.timerId)
            }
            incoming.removeClass("destroyed")
        }

        val hasAnimatedTransition = spec.preset != FragmentTransitionPreset.NONE && spec.durationMs > 0

        // 1. Handle outgoing fragment exit
        if (outgoing != null && outgoing !== incoming) {
            // If outgoing already had an exit timer, complete it first
            exitingEntries.remove(outgoing)?.let { prev ->
                if (prev.timerId != -1) {
                    stageContainer.clearTimeout(prev.timerId)
                }
            }

            if (!hasAnimatedTransition) {
                spec.onExitBegin?.invoke(outgoing, 0) ?: outgoing.onBeginExitTransition(0)
                spec.onExitComplete?.invoke(outgoing) ?: outgoing.onFinishExitTransition()
                finalizeOutgoing(outgoing, destroyOutgoing)
            } else {
                val exitBeginHook = spec.onExitBegin
                if (exitBeginHook != null) {
                    exitBeginHook(outgoing, spec.durationMs)
                } else {
                    outgoing.removeClass("created")
                    outgoing.addClass("destroyed")
                    outgoing.pointerEventsEnabled = false
                    outgoing.zIndex = 0
                    outgoing.onBeginExitTransition(spec.durationMs)
                }
                applyStylesTo(outgoing)
                if (exitBeginHook != null) {
                    exitBeginHook(outgoing, spec.durationMs)
                } else {
                    outgoing.onBeginExitTransition(spec.durationMs)
                }

                if (!spec.keepOutgoingMountedDuringExit) {
                    spec.onExitComplete?.invoke(outgoing) ?: outgoing.onFinishExitTransition()
                    finalizeOutgoing(outgoing, destroyOutgoing)
                } else {
                    val completeExit = {
                        exitingEntries.remove(outgoing)
                        val completeHook = spec.onExitComplete
                        if (completeHook != null) {
                            completeHook(outgoing)
                        } else {
                            outgoing.onFinishExitTransition()
                        }
                        finalizeOutgoing(outgoing, destroyOutgoing)
                    }
                    val timerId = stageContainer.setTimeout(spec.durationMs) {
                        completeExit()
                    }
                    if (timerId > 0) {
                        exitingEntries[outgoing] = ExitingEntry(
                            fragment = outgoing,
                            timerId = timerId,
                            completeAction = completeExit
                        )
                    } else {
                        completeExit()
                    }
                }
            }
        }

        // 2. Handle incoming fragment enter
        if (!hasAnimatedTransition) {
            incoming.removeClass("created")
            incoming.removeClass("destroyed")
            incoming.pointerEventsEnabled = true
            mountIncoming(incoming)
            applyStylesTo(incoming)
            onIncomingActivated(incoming)
        } else {
            val enterBeginHook = spec.onEnterBegin
            if (enterBeginHook != null) {
                enterBeginHook(incoming, spec.durationMs)
            } else {
                incoming.removeClass("destroyed")
                incoming.addClass("created")
                incoming.pointerEventsEnabled = false
                incoming.zIndex = 0
                incoming.onBeginEnterTransition(spec.durationMs)
            }

            mountIncoming(incoming)
            applyStylesTo(incoming)

            if (incoming.hasClass("created")) {
                if (enterBeginHook != null) {
                    enterBeginHook(incoming, spec.durationMs)
                } else {
                    incoming.onBeginEnterTransition(spec.durationMs)
                }
            }

            val activateIncoming = {
                enterActivationTimerId = -1
                pendingEnterFragment = null
                pendingEnterAction = null
                if (!incoming.hasClass("destroyed") && incoming.state != FragmentState.DESTROYED) {
                    val activateHook = spec.onEnterActivate
                    if (activateHook != null) {
                        activateHook(incoming, spec.durationMs)
                    } else {
                        incoming.removeClass("created")
                        incoming.pointerEventsEnabled = true
                        incoming.zIndex = 5
                        incoming.onActivateEnterTransition(spec.durationMs)
                    }
                    applyStylesTo(incoming)
                    onIncomingActivated(incoming)
                }
            }

            if (spec.activationDelayMs > 0) {
                pendingEnterFragment = incoming
                pendingEnterAction = activateIncoming
                val timerId = stageContainer.setTimeout(spec.activationDelayMs) {
                    activateIncoming()
                }
                if (timerId > 0) {
                    enterActivationTimerId = timerId
                } else {
                    activateIncoming()
                }
            } else {
                activateIncoming()
            }
        }
    }

    /**
     * Immediately completes any pending enter activation and outgoing exit transitions.
     */
    fun completeAllTransitionsImmediately() {
        if (enterActivationTimerId != -1) {
            stageContainer.clearTimeout(enterActivationTimerId)
            enterActivationTimerId = -1
        }
        val enterAction = pendingEnterAction
        pendingEnterFragment = null
        pendingEnterAction = null
        enterAction?.invoke()

        val snapshot = exitingEntries.values.toList()
        exitingEntries.clear()
        for (entry in snapshot) {
            if (entry.timerId != -1) {
                stageContainer.clearTimeout(entry.timerId)
            }
            entry.completeAction()
        }
    }

    override fun dispose() {
        completeAllTransitionsImmediately()
    }
}
