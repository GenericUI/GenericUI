package com.nexustools.genericui.component.action

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl

/**
 * Interactive push button component (`<button class="btn">`) supporting dual-mode
 * Bootstrap Enum styling ([type], [size], [outline]) and SASS/CSS classes.
 */
open class Button(
    text: String = "",
    type: BootstrapType = BootstrapType.PRIMARY,
    size: BootstrapSize = BootstrapSize.MD,
    outline: Boolean = false,
    private val classPrefix: String = "btn"
) : AbstractComponent() {
    var text: String = text
        set(value) {
            if (field != value) {
                field = value
                notifyParentCacheDirty()
                typedNative<ButtonNativeImpl<*>>()?.setText(value)
            }
        }

    var icon: String? = null
        set(value) {
            if (field != value) {
                field = value
                notifyParentCacheDirty()
                typedNative<ButtonNativeImpl<*>>()?.setIcon(value)
            }
        }

    init {
        this.type = type
        this.size = size
        this.outline = outline
    }

    /**
     * `kotlinx.html`-style unary plus operator to set/append button caption text.
     */
    operator fun String.unaryPlus() {
        text = if (text.isEmpty()) this else text + this
    }

    override fun createNativeImpl(factory: NativeFactory): ButtonNativeImpl<*> =
        factory.createButton(classPrefix)

    override fun syncStateToNative() {
        super.syncStateToNative()
        val impl = typedNative<ButtonNativeImpl<*>>() ?: return
        impl.setText(text)
        impl.setIcon(icon)
    }
}
