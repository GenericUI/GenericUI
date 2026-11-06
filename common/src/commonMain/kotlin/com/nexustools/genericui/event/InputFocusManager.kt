package com.nexustools.genericui.event

import com.nexustools.genericui.core.AbstractComponent

/**
 * Tracks active text-input focus across components and native widgets so global or
 * container shortcut key listeners do not fire when the user is typing in a text field or text area.
 */
object InputFocusManager {
    var focusedComponent: AbstractComponent? = null

    var isNativeTextInputFocused: Boolean = false

    val isTypingInTextInput: Boolean
        get() = isNativeTextInputFocused || (focusedComponent?.isTextInputComponent == true)

    fun onComponentFocusChanged(component: AbstractComponent, focused: Boolean) {
        if (focused) {
            focusedComponent = component
        } else if (focusedComponent === component) {
            focusedComponent = null
        }
    }

    fun shouldIgnoreShortcutKeyEvent(event: KeyEvent, listenerOwner: AbstractComponent? = null): Boolean {
        if (listenerOwner?.isTextInputComponent == true) return false
        val ignoreWhenTyping = listenerOwner?.ignoreKeyEventsWhenTypingInTextInput ?: true
        return ignoreWhenTyping && (event.isTargetTextInput || isTypingInTextInput)
    }

    fun reset() {
        focusedComponent = null
        isNativeTextInputFocused = false
    }
}
