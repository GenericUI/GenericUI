package com.nexustools.genericui.component.dialog

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.overlay.Modal
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptionsDialogAndModalFooterTest {

    @Test
    fun testDialogWindowAndModalFooterButtonBar() {
        val modal = Modal(title = "Codec Diagnostics")
        assertFalse(modal.buttonBar.isVisible)
        assertTrue(modal.buttonBar.getClasses().contains("modal-footer"))

        val leftBtn1 = modal.addLeftButton("Refresh", type = BootstrapType.SECONDARY) {}
        val leftBtn2 = modal.addLeftButton("Export", type = BootstrapType.INFO) {}
        assertTrue(modal.buttonBar.isVisible)
        assertTrue(modal.children.contains(modal.buttonBar))

        // Verify 8px horizontal spacer is automatically inserted between multiple left buttons
        assertEquals(3, modal.leftButtonBarGroup.children.size)
        assertEquals(leftBtn1, modal.leftButtonBarGroup.children[0])
        val leftSpacer = modal.leftButtonBarGroup.children[1] as? Spacer
        assertTrue(leftSpacer != null && !leftSpacer.isFill && leftSpacer.spacerWidthPx == 8)
        assertEquals(leftBtn2, modal.leftButtonBarGroup.children[2])

        var closeCallbackInvoked = false
        val cancelBtn = modal.addRightButton("Cancel", type = BootstrapType.SECONDARY) {}
        val closeBtn = modal.addCloseButton("Close") { closeCallbackInvoked = true }

        // Verify 8px horizontal spacer is automatically inserted between multiple right buttons
        assertEquals(3, modal.rightButtonBarGroup.children.size)
        assertEquals(cancelBtn, modal.rightButtonBarGroup.children[0])
        val rightSpacer = modal.rightButtonBarGroup.children[1] as? Spacer
        assertTrue(rightSpacer != null && !rightSpacer.isFill && rightSpacer.spacerWidthPx == 8)
        assertEquals(closeBtn, modal.rightButtonBarGroup.children[2])

        // Verify fill spacer separates leftButtonBarGroup and rightButtonBarGroup
        assertEquals(3, modal.buttonBar.children.size)
        assertEquals(modal.leftButtonBarGroup, modal.buttonBar.children[0])
        val middleFillSpacer = modal.buttonBar.children[1] as? Spacer
        assertTrue(middleFillSpacer != null && middleFillSpacer.isFill)
        assertEquals(modal.rightButtonBarGroup, modal.buttonBar.children[2])

        modal.open()
        assertTrue(modal.isWindowShown)
        closeBtn.onClick?.invoke()
        assertTrue(closeCallbackInvoked)
        assertFalse(modal.isWindowShown)

        modal.clearButtons()
        assertFalse(modal.buttonBar.isVisible)
        assertTrue(modal.leftButtonBarGroup.children.isEmpty())
        assertTrue(modal.rightButtonBarGroup.children.isEmpty())
    }

    @Test
    fun testOptionsDialogSingleTitleSpacedButtonsAndFooterCancel() {
        var selectedOptionId: String? = null
        val dialog = OptionsDialog(
            title = "Show Options",
            options = listOf(
                OptionDialogItem(
                    text = "Play Next Episode",
                    type = BootstrapType.SUCCESS,
                    onClick = { selectedOptionId = "play_next" }
                ),
                OptionDialogItem(
                    text = "Browse Seasons & Episodes",
                    type = BootstrapType.PRIMARY,
                    onClick = { selectedOptionId = "browse_seasons" }
                ),
                OptionDialogItem(
                    text = "Disable Show",
                    type = BootstrapType.DANGER,
                    onClick = { selectedOptionId = "disable_show" }
                )
            ),
            defaultBottomButtonText = "Cancel"
        )

        assertEquals("Show Options", dialog.title)

        // Verify body contains bodyStack (no duplicate Heading) and footer buttonBar
        assertEquals(2, dialog.children.size)
        assertEquals(dialog.bodyStack, dialog.children[0])
        assertEquals(dialog.buttonBar, dialog.children[1])

        // 3 option buttons + 2 vertical 8px spacers between them = 5 children in optionsStack
        assertEquals(5, dialog.optionsStack.children.size)
        val firstButton = dialog.optionsStack.children[0] as Button
        val firstSpacer = dialog.optionsStack.children[1] as Spacer
        val secondButton = dialog.optionsStack.children[2] as Button
        val secondSpacer = dialog.optionsStack.children[3] as Spacer
        val thirdButton = dialog.optionsStack.children[4] as Button

        assertEquals("Play Next Episode", firstButton.text)
        assertEquals(BootstrapType.SUCCESS, firstButton.type)
        assertEquals(8, firstSpacer.spacerHeightPx)
        assertEquals("Browse Seasons & Episodes", secondButton.text)
        assertEquals(BootstrapType.PRIMARY, secondButton.type)
        assertEquals(8, secondSpacer.spacerHeightPx)
        assertEquals("Disable Show", thirdButton.text)
        assertEquals(BootstrapType.DANGER, thirdButton.type)

        // Verify Cancel button is mounted inside the modal-footer rightButtonBarGroup
        assertTrue(dialog.buttonBar.isVisible)
        assertEquals(1, dialog.rightButtonBarGroup.children.size)
        val footerCancelBtn = dialog.rightButtonBarGroup.children[0] as Button
        assertEquals("Cancel", footerCancelBtn.text)

        dialog.open()
        assertTrue(dialog.isWindowShown)
        secondButton.onClick?.invoke()
        assertEquals("browse_seasons", selectedOptionId)
        assertFalse(dialog.isWindowShown)
    }
}
