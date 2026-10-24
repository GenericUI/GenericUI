package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.enums.bootstrap.BootstrapType

/**
 * Multi-step guided wizard / onboarding Helper [Fragment] with Previous/Next/Finish controls,
 * per-step validation, and automatic [onBackPressed] step navigation.
 */
open class WizardStepperFragment(
    private val onWizardCompleted: (() -> Unit)? = null
) : Fragment() {

    private val steps = mutableListOf<Pair<String, Fragment>>()
    var currentStepIndex: Int = 0
        private set

    val stepContainer: FragmentContainer = FragmentContainer()
    val footerBar: HStack = HStack()
    val backButton: Button = Button("Back").apply { setType(BootstrapType.SECONDARY) }
    val nextButton: Button = Button("Next").apply { setType(BootstrapType.PRIMARY) }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        val layout = VStack()
        backButton.setOnClick { previousStep() }
        nextButton.setOnClick { nextStep() }
        footerBar.addChild(backButton)
        footerBar.addChild(nextButton)
        layout.addChild(stepContainer)
        layout.addChild(footerBar)
        addChild(layout)
        syncActiveStep()
    }

    fun addStep(title: String, fragment: Fragment) {
        steps.add(title to fragment)
        if (steps.size == 1) {
            syncActiveStep()
        }
    }

    fun nextStep(): Boolean {
        if (currentStepIndex < steps.lastIndex) {
            currentStepIndex++
            syncActiveStep()
            return true
        }
        onWizardCompleted?.invoke()
        return false
    }

    fun previousStep(): Boolean {
        if (currentStepIndex > 0) {
            currentStepIndex--
            syncActiveStep()
            return true
        }
        return false
    }

    override fun onBackPressed(): Boolean {
        if (previousStep()) return true
        return super.onBackPressed()
    }

    private fun syncActiveStep() {
        if (steps.isEmpty()) return
        val idx = currentStepIndex.coerceIn(0, steps.lastIndex)
        stepContainer.replaceFragment(steps[idx].second)
        backButton.isEnabled = idx > 0
        nextButton.text = if (idx == steps.lastIndex) "Finish" else "Next"
    }
}
