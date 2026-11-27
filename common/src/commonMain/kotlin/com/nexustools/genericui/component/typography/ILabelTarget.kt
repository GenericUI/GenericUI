package com.nexustools.genericui.component.typography

import com.nexustools.genericui.core.AbstractComponent

/**
 * Contract for focusable or form-associated components that can be targeted by a [Label] (`label.target`).
 *
 * - On Web (`supportsNativeHtmlLabelFor == true`), [ensureLabelTargetId] supplies a stable DOM `id`
 *   so `<label for="...">` natively activates/focuses the underlying control element.
 * - On all non-Web backends, clicking a bound [Label] invokes [focusFromLabel].
 */
interface ILabelTarget {
    val labelTargetComponent: AbstractComponent

    val labelTargetId: String?
        get() = labelTargetComponent.id

    fun ensureLabelTargetId(): String {
        val targetComponent = labelTargetComponent
        val existing = targetComponent.id?.trim()
        if (!existing.isNullOrEmpty()) {
            return existing
        }
        val generated = AbstractComponent.allocateLabelTargetId()
        targetComponent.id = generated
        return generated
    }

    fun focusFromLabel() {
        val targetComponent = labelTargetComponent
        if (targetComponent.isEnabled && targetComponent.isFocusable) {
            targetComponent.isFocused = true
        }
    }
}
