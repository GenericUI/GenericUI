package com.nexustools.genericui.component.form

import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Labeled form field container (`<div class="mb-3 form-group">`) pairing a [Label],
 * form control, and optional validation feedback text.
 */
class FormGroup(
    labelText: String = "",
    control: AbstractComponent? = null,
    helpText: String? = null
) : AbstractContainer() {
    val label: Label = Label(labelText)
    var helpLabel: Label? = helpText?.let { Label(it, tagName = "div", classPrefix = "form-text") }

    init {
        if (labelText.isNotEmpty()) addChild(label)
        if (control != null) addChild(control)
        helpLabel?.let { addChild(it) }
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("form-group mb-3")
}
