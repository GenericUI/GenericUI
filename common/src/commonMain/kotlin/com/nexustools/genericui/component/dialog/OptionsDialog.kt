package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.overlay.Modal
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BootstrapWidth
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.css.CssLength

/**
 * Modal options dialog (`OptionsDialog`) displaying a [Spacer]-separated stack of full-width
 * [BootstrapType] action buttons in the modal body and a `.modal-footer` button bar at the bottom.
 *
 * Renders the dialog [title] once in the modal header (never adding a duplicate heading in the body).
 */
open class OptionsDialog(
    title: String,
    options: List<OptionDialogItem> = emptyList(),
    val optionSpacingPx: Int = 8,
    defaultBottomButtonText: String? = "Cancel",
    defaultBottomButtonType: BootstrapType = BootstrapType.SECONDARY,
    defaultBottomButtonOutline: Boolean = false
) : Modal(title = title) {

    val bodyStack: VStack = VStack().apply {
        setPaddingAll(SpacingScale.S3)
        getBootstrap().width = BootstrapWidth.W_100
    }

    val optionsStack: VStack = VStack().apply {
        getBootstrap().width = BootstrapWidth.W_100
        getStyle().setMinWidth(CssLength.percent(100))
    }

    var defaultBottomButton: Button? = null
        private set

    init {
        bodyStack.addChild(optionsStack)
        addChild(bodyStack)
        for (item in options) {
            addOption(item)
        }
        if (!defaultBottomButtonText.isNullOrBlank()) {
            setDefaultBottomButton(
                text = defaultBottomButtonText,
                type = defaultBottomButtonType,
                outline = defaultBottomButtonOutline
            )
        }
    }

    fun addOption(item: OptionDialogItem): Button {
        if (optionsStack.children.isNotEmpty() && optionSpacingPx > 0) {
            optionsStack.addChild(Spacer.vertical(optionSpacingPx))
        }
        val button = Button(
            text = item.text,
            type = item.type,
            size = item.size,
            outline = item.outline
        ).apply {
            getBootstrap().width = BootstrapWidth.W_100
            getStyle().setWhiteSpace(com.nexustools.genericui.enums.css.CssWhiteSpace.NOWRAP)
            onClick = {
                item.onClick()
                if (item.closeOnClick) {
                    close()
                }
            }
        }
        optionsStack.addChild(button)
        return button
    }

    fun addOption(
        text: String,
        type: BootstrapType = BootstrapType.SECONDARY,
        outline: Boolean = true,
        size: BootstrapSize = BootstrapSize.SM,
        closeOnClick: Boolean = true,
        onClick: () -> Unit
    ): Button = addOption(
        OptionDialogItem(
            text = text,
            type = type,
            outline = outline,
            size = size,
            closeOnClick = closeOnClick,
            onClick = onClick
        )
    )

    fun clearOptions() {
        optionsStack.clearChildren()
    }

    fun addLeftBottomButton(component: AbstractComponent): AbstractComponent =
        addLeftButton(component)

    fun addLeftBottomButton(
        text: String,
        type: BootstrapType = BootstrapType.SECONDARY,
        size: BootstrapSize = BootstrapSize.SM,
        outline: Boolean = false,
        onClick: () -> Unit
    ): Button = addLeftButton(text, type, size, outline, onClick)

    fun addRightBottomButton(component: AbstractComponent): AbstractComponent =
        addRightButton(component)

    fun addRightBottomButton(
        text: String,
        type: BootstrapType = BootstrapType.SECONDARY,
        size: BootstrapSize = BootstrapSize.SM,
        outline: Boolean = false,
        onClick: () -> Unit
    ): Button = addRightButton(text, type, size, outline, onClick)

    fun setDefaultBottomButton(
        text: String?,
        type: BootstrapType = BootstrapType.SECONDARY,
        outline: Boolean = false,
        onClick: (() -> Unit)? = null
    ): Button? {
        if (text.isNullOrBlank()) {
            defaultBottomButton?.let { existing ->
                rightButtonBarGroup.removeChild(existing)
            }
            defaultBottomButton = null
            if (leftButtonBarGroup.children.isEmpty() && rightButtonBarGroup.children.isEmpty()) {
                buttonBar.isVisible = false
            }
            return null
        }

        val existing = defaultBottomButton
        if (existing != null && rightButtonBarGroup.children.contains(existing)) {
            existing.text = text
            existing.type = type
            existing.outline = outline
            existing.onClick = {
                onClick?.invoke()
                close()
            }
            buttonBar.isVisible = true
            return existing
        }

        val created = addCloseButton(
            text = text,
            type = type,
            outline = outline,
            onClose = onClick
        )
        defaultBottomButton = created
        return created
    }
}
