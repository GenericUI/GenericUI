package com.nexustools.genericui.component.navigation

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Multi-step progress indicator bar used by [com.nexustools.genericui.component.fragment.helper.WizardStepperFragment].
 */
class Stepper(
    var steps: List<String> = emptyList(),
    var activeStepIndex: Int = 0
) : AbstractContainer() {
    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("stepper")
}
