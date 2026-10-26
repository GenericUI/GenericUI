package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.layout.DynamicFullscreenController
import com.nexustools.genericui.component.layout.HybridBreakpointSpec
import com.nexustools.genericui.component.layout.HybridColumnLayout
import com.nexustools.genericui.component.layout.HybridColumnSpec
import com.nexustools.genericui.component.layout.HybridFoldMode
import com.nexustools.genericui.component.layout.HybridFullscreenPolicy
import com.nexustools.genericui.component.layout.HybridLayoutMode
import com.nexustools.genericui.component.layout.HybridTabPlacement
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.fragment.FragmentState
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [Fragment] that hosts a [HybridColumnLayout] and coordinates child column [Fragment] lifecycles,
 * responsive viewport/configuration changes, Picture-in-Picture transitions, and optional [DynamicFullscreenController]
 * back-navigation handling.
 */
open class HybridColumnFragment(
    foldMode: HybridFoldMode = HybridFoldMode.PIN_FIRST_COLUMN_TOP,
    tabPlacement: HybridTabPlacement = HybridTabPlacement.TOP_TABS,
    breakpointSpec: HybridBreakpointSpec = HybridBreakpointSpec.DEFAULT,
    fullscreenPolicy: HybridFullscreenPolicy = HybridFullscreenPolicy.DISABLED,
    fullscreenTargetColumnIndex: Int = 0
) : Fragment() {

    val hybridColumnLayout: HybridColumnLayout = HybridColumnLayout(
        foldMode = foldMode,
        tabPlacement = tabPlacement,
        breakpointSpec = breakpointSpec,
        fullscreenPolicy = fullscreenPolicy,
        fullscreenTargetColumnIndex = fullscreenTargetColumnIndex
    )

    private val childFragments = linkedMapOf<String, Fragment>()

    val fullscreenController: DynamicFullscreenController?
        get() = hybridColumnLayout.fullscreenController

    val layoutMode: HybridLayoutMode
        get() = hybridColumnLayout.layoutMode

    val inColumnMode: Boolean
        get() = hybridColumnLayout.inColumnMode

    var foldMode: HybridFoldMode
        get() = hybridColumnLayout.foldMode
        set(value) {
            hybridColumnLayout.foldMode = value
        }

    var tabPlacement: HybridTabPlacement
        get() = hybridColumnLayout.tabPlacement
        set(value) {
            hybridColumnLayout.tabPlacement = value
        }

    var breakpointSpec: HybridBreakpointSpec
        get() = hybridColumnLayout.breakpointSpec
        set(value) {
            hybridColumnLayout.breakpointSpec = value
        }

    var fullscreenPolicy: HybridFullscreenPolicy
        get() = hybridColumnLayout.fullscreenPolicy
        set(value) {
            hybridColumnLayout.fullscreenPolicy = value
        }

    init {
        addChild(hybridColumnLayout)
    }

    /**
     * Opts into dynamic fullscreen behavior with the given [policy].
     */
    fun enableDynamicFullscreen(
        policy: HybridFullscreenPolicy = HybridFullscreenPolicy.AUTO_PHONE_LANDSCAPE_AND_TABLET_LOCKED
    ): DynamicFullscreenController {
        val controller = hybridColumnLayout.enableDynamicFullscreen(
            policy = policy,
            window = hostingWindow
        )
        super.setFullscreen(hybridColumnLayout.isFullscreen, getDisplayCutoutMode())
        return controller
    }

    /**
     * Disables dynamic fullscreen behavior and exits fullscreen if active.
     */
    fun disableDynamicFullscreen() {
        hybridColumnLayout.disableDynamicFullscreen()
        super.setFullscreen(false, getDisplayCutoutMode())
    }

    /**
     * Registers a component column in [hybridColumnLayout].
     */
    fun addColumn(spec: HybridColumnSpec, component: AbstractComponent): HybridColumnFragment = apply {
        hybridColumnLayout.addColumn(spec, component)
    }

    /**
     * Convenience overload for registering a component column by parameters.
     */
    fun addColumn(
        id: String,
        title: String = id,
        weight: Float = 1.0f,
        colMd: Int? = null,
        colLg: Int? = null,
        colXl: Int? = null,
        icon: String? = null,
        compactHeightPx: Int? = null,
        compactAspectRatio: Float? = null,
        component: AbstractComponent
    ): HybridColumnFragment = apply {
        hybridColumnLayout.addColumn(
            id = id,
            title = title,
            weight = weight,
            colMd = colMd,
            colLg = colLg,
            colXl = colXl,
            icon = icon,
            compactHeightPx = compactHeightPx,
            compactAspectRatio = compactAspectRatio,
            component = component
        )
    }

    /**
     * Registers a child [Fragment] as a column in [hybridColumnLayout] and synchronizes its lifecycle
     * (`performCreate`, `performStart`, `performResume`, `performPause`, `performStop`, `performDestroy`)
     * with this [HybridColumnFragment].
     */
    fun addFragmentColumn(spec: HybridColumnSpec, childFragment: Fragment): HybridColumnFragment = apply {
        childFragments[spec.id] = childFragment
        syncChildFragmentLifecycleToCurrentState(childFragment)
        hybridColumnLayout.addColumn(spec, childFragment)
    }

    /**
     * Selects the tab corresponding to [columnId] when columns are collapsed into tabs.
     */
    fun selectTab(columnId: String): Boolean =
        hybridColumnLayout.selectTab(columnId)

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        super.setFullscreen(fullscreen, cutoutMode)
        if (hybridColumnLayout.isFullscreen != fullscreen) {
            hybridColumnLayout.isFullscreen = fullscreen
        }
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        if (hybridColumnLayout.parent !== this) {
            addChild(hybridColumnLayout)
        }
        hybridColumnLayout.fullscreenController?.targetWindow = hostingWindow
        for (child in childFragments.values) {
            child.performCreate(savedState)
        }
    }

    override fun onStart() {
        super.onStart()
        for (child in childFragments.values) {
            child.performStart()
        }
    }

    override fun onResume() {
        super.onResume()
        hybridColumnLayout.fullscreenController?.targetWindow = hostingWindow
        for (child in childFragments.values) {
            child.performResume()
        }
    }

    override fun onPause() {
        for (child in childFragments.values) {
            child.performPause()
        }
        super.onPause()
    }

    override fun onStop() {
        for (child in childFragments.values) {
            child.performStop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        for (child in childFragments.values) {
            child.performDestroy()
        }
        childFragments.clear()
        super.onDestroy()
    }

    override fun onResize(viewportWidth: Int, viewportHeight: Int) {
        super.onResize(viewportWidth, viewportHeight)
        hybridColumnLayout.updateViewport(viewportWidth, viewportHeight)
        if (super.isFullscreen() != hybridColumnLayout.isFullscreen) {
            super.setFullscreen(hybridColumnLayout.isFullscreen, getDisplayCutoutMode())
        }
        for (child in childFragments.values) {
            child.onResize(viewportWidth, viewportHeight)
        }
    }

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        hybridColumnLayout.applyWindowConfiguration(newConfig)
        if (super.isFullscreen() != hybridColumnLayout.isFullscreen) {
            super.setFullscreen(hybridColumnLayout.isFullscreen, getDisplayCutoutMode())
        }
        for (child in childFragments.values) {
            child.onConfigurationChanged(newConfig)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        hybridColumnLayout.isInPictureInPictureMode = isInPictureInPictureMode
        for (child in childFragments.values) {
            child.onPictureInPictureModeChanged(isInPictureInPictureMode)
        }
    }

    override fun onBackPressed(): Boolean {
        if (hybridColumnLayout.fullscreenController?.onBackPressed() == true) {
            super.setFullscreen(false, getDisplayCutoutMode())
            return true
        }
        if (hybridColumnLayout.isFullscreen) {
            hybridColumnLayout.isFullscreen = false
            super.setFullscreen(false, getDisplayCutoutMode())
            return true
        }
        for (child in childFragments.values) {
            if (child.isVisible && child.onBackPressed()) {
                return true
            }
        }
        return super.onBackPressed()
    }

    private fun syncChildFragmentLifecycleToCurrentState(child: Fragment) {
        child.attachedWindow = attachedWindow
        child.attachedIWindow = attachedIWindow
        when (state) {
            FragmentState.CREATED -> child.performCreate(arguments)
            FragmentState.STARTED, FragmentState.PAUSED -> {
                child.performCreate(arguments)
                child.performStart()
            }
            FragmentState.RESUMED -> {
                child.performCreate(arguments)
                child.performStart()
                child.performResume()
            }
            else -> {}
        }
    }
}
