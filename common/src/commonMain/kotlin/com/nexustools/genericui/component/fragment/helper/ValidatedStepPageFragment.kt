package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Multi-step wizard page fragment (`DCGame`'s `CharGenBase` & `CharGenPage`) with:
 * - A top horizontal step navigation bar ([stepBar]) where completed steps are clickable,
 *   the current step is highlighted (`.btn-active`), and future steps are disabled.
 * - Step state array propagation (`args[currentStepOrdinal] = stepData`) via [stepNavigate].
 * - Sequential asynchronous prior-step validation (`0 until currentStepOrdinal`) with automatic
 *   rewind to the offending earlier step if validation fails!
 * - Base64 step state encoding/decoding helpers for save/load & URL query portability.
 */
abstract class ValidatedStepPageFragment(
    val stepNames: List<String>,
    val currentStepOrdinal: Int,
    titlePrefix: String = "Character Generator"
) : CenteredMenuPageFragment(
    title = if (currentStepOrdinal in stepNames.indices) {
        "$titlePrefix - ${stepNames[currentStepOrdinal]}"
    } else {
        titlePrefix
    }
) {

    val stepBar: HStack = HStack().apply {
        addClass("mb-2 justify-content-center")
    }
    val contentContainer: Container = Container("div", "step-content")

    var stepStorage: Any? = null
        protected set

    var stepPageFactory: ((stepOrdinal: Int) -> ValidatedStepPageFragment?)? = null

    private val stepButtons = mutableListOf<Button>()

    override fun menuClass(): String = "chargen"

    open fun buildStepView(content: Container, args: List<Any?>) {}

    open fun onStepController(args: List<Any?>) {}

    /**
     * Validates [stepData] for this step and invokes [response] with `null` on success
     * or an error message string on failure.
     */
    open fun validateStep(stepData: Any?, args: List<Any?>, response: (String?) -> Unit) {
        response(null)
    }

    override fun buildMenuView(menu: Container, args: List<Any?>) {
        stepBar.clearChildren()
        stepButtons.clear()

        for (i in stepNames.indices) {
            val stepTitle = stepNames[i]
            val btn = Button(stepTitle, BootstrapType.SECONDARY, BootstrapSize.SM).apply {
                if (i < stepNames.lastIndex) {
                    addClass("me-1")
                }
                when {
                    i < currentStepOrdinal -> {
                        isEnabled = true
                        setOnClick {
                            val targetPage = createStepPage(i)
                            if (targetPage != null) {
                                stepNavigate(targetPage)
                            }
                        }
                    }
                    i == currentStepOrdinal -> {
                        isEnabled = false
                        addClass("btn-active")
                    }
                    else -> {
                        isEnabled = false
                    }
                }
            }
            stepButtons.add(btn)
            stepBar.addChild(btn)
        }

        contentContainer.clearChildren()
        buildStepView(contentContainer, args)

        menu.addChild(stepBar)
        menu.addChild(contentContainer)
    }

    override fun onMenuController(args: List<Any?>) {
        stepStorage = args.getOrNull(currentStepOrdinal)
        beginLoading()
        runPriorStepsValidation(args, 0)
    }

    private fun runPriorStepsValidation(args: List<Any?>, index: Int) {
        if (index >= currentStepOrdinal) {
            onStepController(args)
            endLoading()
            return
        }

        val priorPage = createStepPage(index)
        if (priorPage == null) {
            runPriorStepsValidation(args, index + 1)
            return
        }

        val priorData = args.getOrNull(index)
        priorPage.validateStep(priorData, args) { err ->
            if (err != null) {
                endLoading()
                alert(err)
                stepNavigate(priorPage)
            } else {
                runPriorStepsValidation(args, index + 1)
            }
        }
    }

    protected open fun createStepPage(stepOrdinal: Int): ValidatedStepPageFragment? =
        stepPageFactory?.invoke(stepOrdinal)

    /**
     * Navigates to [nextPage], storing [stepData] at [currentStepOrdinal] in [pageArgs]
     * (or truncating [pageArgs] when rewinding to an earlier step).
     */
    fun stepNavigate(nextPage: PageFragment, stepData: Any? = null) {
        val nextArgs = pageArgs.toMutableList()
        if (stepData != null) {
            while (nextArgs.size <= currentStepOrdinal) {
                nextArgs.add(null)
            }
            nextArgs[currentStepOrdinal] = stepData
        } else if (nextPage is ValidatedStepPageFragment && nextPage.currentStepOrdinal < nextArgs.size) {
            while (nextArgs.size > nextPage.currentStepOrdinal + 1) {
                nextArgs.removeAt(nextArgs.lastIndex)
            }
        }
        navigateWithArgsList(nextPage, nextArgs)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeStepStateBase64(serializedState: String): String =
        Base64.encode(serializedState.encodeToByteArray())

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeStepStateBase64(base64State: String): String =
        Base64.decode(base64State.trim()).decodeToString()
}
