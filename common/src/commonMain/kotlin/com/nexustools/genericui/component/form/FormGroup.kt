package com.nexustools.genericui.component.form

import com.nexustools.genericui.component.typography.ILabelTarget
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Labeled form field container (`<div class="mb-3 form-group">`) pairing a [Label],
 * form control, and optional validation feedback text.
 *
 * Automatically binds `label.target = control` whenever [control] implements [ILabelTarget]
 * (both during construction and whenever [control] is reassigned).
 */
class FormGroup(
    labelText: String = "",
    control: AbstractComponent? = null,
    helpText: String? = null
) : AbstractContainer() {
    val label: Label = Label(labelText)
    var helpLabel: Label? = helpText?.let { Label(it, tagName = "div", classPrefix = "form-text") }

    var control: AbstractComponent? = control
        set(value) {
            if (field === value) return
            val previous = field
            field = value
            if (previous != null && previous !== label && previous !== helpLabel) {
                removeChild(previous)
            }
            if (value != null && value !== label && value !== helpLabel) {
                addChild(value, computeControlInsertionIndex())
            }
            bindLabelTarget(value)
        }

    init {
        htmlTag = "genericui:formgroup"
        if (labelText.isNotEmpty()) addChild(label)
        if (control != null) addChild(control)
        helpLabel?.let { addChild(it) }
        bindLabelTarget(control)
    }

    private fun computeControlInsertionIndex(): Int {
        val labelIdx = children.indexOf(label)
        if (labelIdx >= 0) return labelIdx + 1
        val helpIdx = helpLabel?.let { children.indexOf(it) } ?: -1
        return if (helpIdx >= 0) helpIdx else children.size
    }

    private fun bindLabelTarget(targetControl: AbstractComponent?) {
        label.target = targetControl as? ILabelTarget
    }

    protected override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> =
        factory.createContainer("form-group mb-3", htmlTag)
}
