package com.nexustools.genericui.component.action

import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl

/**
 * Split action button with a primary action click handler and a secondary dropdown caret menu.
 */
class SplitDropdownButton(
    label: String = "",
    onPrimaryAction: (() -> Unit)? = null
) : DropdownButton(label = label) {
    var onPrimaryAction: (() -> Unit)? = onPrimaryAction
        set(value) {
            field = value
            typedNative<DropdownNativeImpl<*>>()?.setOnPrimaryAction(value)
        }

    init {
        htmlTag = "button"
        setAttribute("data-control-variant", "split-dropdown")
    }

    override fun syncStateToNative() {
        setAttribute("data-control-variant", "split-dropdown")
        super.syncStateToNative()
        typedNative<DropdownNativeImpl<*>>()?.setOnPrimaryAction(onPrimaryAction)
    }
}
