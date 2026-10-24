package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentContainer
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.SplitPane
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [Fragment] that displays a Master list alongside a Detail [Fragment] in a [SplitPane]
 * on wide/tablet/desktop screens (`widthPx >= splitThresholdPx`), and switches to a single-pane
 * stacked flow (handling [onBackPressed] to pop back to the Master list) on narrow/phone screens.
 */
open class MasterDetailFragment(
    val masterContent: Container = Container("master-pane"),
    val splitThresholdPx: Int = 700,
    dividerRatio: Float = 0.35f
) : Fragment() {

    val detailFragmentContainer: FragmentContainer = FragmentContainer()
    private val splitPane = SplitPane(Orientation.HORIZONTAL, dividerRatio)
    private var isSplitMode: Boolean = true
    private var showingDetailOnPhone: Boolean = false

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        updateLayoutForWidth(attachedWindow?.getWindowSize()?.width ?: 1280)
    }

    fun showDetailFragment(detail: Fragment) {
        detailFragmentContainer.setFragment(detail)
        if (!isSplitMode) {
            showingDetailOnPhone = true
            clearChildren()
            addChild(detailFragmentContainer)
        }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        updateLayoutForWidth(newConfig.widthPx)
    }

    override fun onBackPressed(): Boolean {
        if (!isSplitMode && showingDetailOnPhone) {
            showingDetailOnPhone = false
            clearChildren()
            addChild(masterContent)
            return true
        }
        return super.onBackPressed()
    }

    private fun updateLayoutForWidth(widthPx: Int) {
        val wide = widthPx >= splitThresholdPx
        isSplitMode = wide
        (masterContent.parent as? com.nexustools.genericui.core.AbstractContainer)?.removeChild(masterContent)
        (detailFragmentContainer.parent as? com.nexustools.genericui.core.AbstractContainer)?.removeChild(detailFragmentContainer)
        clearChildren()

        if (wide) {
            splitPane.setPanes(masterContent, detailFragmentContainer)
            addChild(splitPane)
        } else if (showingDetailOnPhone && detailFragmentContainer.currentFragment != null) {
            addChild(detailFragmentContainer)
        } else {
            addChild(masterContent)
        }
    }
}
