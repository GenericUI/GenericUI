package com.nexustools.genericui.component.action

import com.nexustools.genericui.component.media.Image
import com.nexustools.genericui.component.typography.ILabelTarget
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.resources.ResourceID

/**
 * Common button contract implemented by both standard system [Button] and explicitly styled
 * [com.nexustools.genericui.component.action.styled.StyledButton].
 */
interface IButton : ILabelTarget {
    var text: String
    var icon: String?
    var image: Image?
    var textRes: ResourceID?
    var iconRes: ResourceID?
    var tintImageToTheme: Boolean
    val isIconOnly: Boolean

    var type: BootstrapType?
    var size: BootstrapSize?
    var outline: Boolean

    var isEnabled: Boolean
    var isDisabled: Boolean
        get() = !isEnabled
        set(value) {
            isEnabled = !value
        }

    var tooltipText: String?
    var onClick: (() -> Unit)?

    fun setText(resId: ResourceID, vararg formatArgs: Any)
    fun setIcon(resId: ResourceID)
    fun setImage(image: Image?, tintToTheme: Boolean = this.tintImageToTheme)
    fun setImage(resId: ResourceID, tintToTheme: Boolean = this.tintImageToTheme)
    fun setImage(src: String, tintToTheme: Boolean = this.tintImageToTheme)
    fun triggerClick()
    fun click() = triggerClick()
}

fun IButton.setOnClick(listener: (() -> Unit)?) {
    onClick = listener
}
