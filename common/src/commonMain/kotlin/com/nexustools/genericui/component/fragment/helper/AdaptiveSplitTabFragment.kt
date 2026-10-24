package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.layout.Col
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.Row
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.navigation.TabContainer
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Helper [Fragment] that implements the exact 3-mode responsive layout system used by the
 * **Episode Caster** Android, Desktop, and Web clients (`EpisodeScreen.kt` / `MusicScreen.kt`):
 *
 * 1. **Tablet / Desktop Landscape (`isTabletOrDesktop && isLandscape`)**:
 *    Renders a **multi-column layout** ([Row] with configurable [primaryColumnWeight], default `1.8f`,
 *    and [secondaryColumnWeight], default `1.0f`, plus an optional third column).
 * 2. **Phone Landscape (`!isTabletOrDesktop && isLandscape`)**:
 *    When [phoneLandscapeFullscreen] is `true` (default `true`), automatically enters **immersive fullscreen**
 *    (`setFullscreen(true, DisplayCutoutMode.SHORT_EDGES)`), hides any optional `NavBar` and secondary tab columns,
 *    and expands [primaryContent] (e.g., `VideoPlayer` or `GLCanvas`/`VKCanvas` MilkDrop visualizer) to **100% fullscreen**.
 *    When [phoneLandscapeFullscreen] is `false`, renders a compact 2-column landscape layout.
 * 3. **Phone & Tablet Portrait (`!isLandscape`)**:
 *    Renders a **single vertical flow with tabs** ([VStack] containing [primaryContent] at the top
 *    followed by [tabContainer] filling the remainder of the screen).
 *
 * Live orientation or window size changes ([onConfigurationChanged]) re-parent [primaryContent] and [tabContainer]
 * seamlessly between the 3 layout modes without destroying or interrupting media playback or 3D GPU contexts.
 */
open class AdaptiveSplitTabFragment(
    var phoneLandscapeFullscreen: Boolean = true,
    var primaryColumnWeight: Float = 1.8f,
    var secondaryColumnWeight: Float = 1.0f
) : Fragment() {

    val primaryContent: Container = Container("adaptive-primary-pane")
    val tabContainer: TabContainer = TabContainer()
    var optionalThirdColumn: Container? = null
        private set

    private val landscapeRow = Row()
    private val landscapeLeftCol = Col(weight = primaryColumnWeight)
    private val landscapeRightCol = Col(weight = secondaryColumnWeight)
    private val landscapeThirdCol = Col(weight = 1.0f)

    private val portraitStack = VStack()
    private val fullscreenWrapper = Container("adaptive-fullscreen-pane")

    private var currentLayoutMode: LayoutMode? = null

    enum class LayoutMode {
        TABLET_DESKTOP_COLUMNS,
        PHONE_LANDSCAPE_FULLSCREEN,
        PORTRAIT_SINGLE_FLOW_TABS
    }

    override fun onCreate(savedState: FragmentBundle?) {
        super.onCreate(savedState)
        applyAdaptiveLayout(
            WindowConfiguration(
                widthPx = attachedWindow?.getWindowSize()?.width ?: 1280,
                heightPx = attachedWindow?.getWindowSize()?.height ?: 800
            )
        )
    }

    fun setPrimaryView(view: com.nexustools.genericui.core.AbstractComponent) {
        primaryContent.clearChildren()
        primaryContent.addChild(view)
    }

    fun addTab(title: String, content: Container) {
        tabContainer.addTab(title, content)
    }

    fun setThirdColumn(column: Container?) {
        optionalThirdColumn = column
        currentLayoutMode = null
        onConfigurationChanged(
            WindowConfiguration(
                widthPx = attachedWindow?.getWindowSize()?.width ?: 1280,
                heightPx = attachedWindow?.getWindowSize()?.height ?: 800
            )
        )
    }

    fun getActiveLayoutMode(): LayoutMode? = currentLayoutMode

    override fun onConfigurationChanged(newConfig: WindowConfiguration) {
        super.onConfigurationChanged(newConfig)
        applyAdaptiveLayout(newConfig)
    }

    protected fun applyAdaptiveLayout(config: WindowConfiguration) {
        val targetMode = when {
            config.isLandscape && !config.isTabletOrDesktop && phoneLandscapeFullscreen ->
                LayoutMode.PHONE_LANDSCAPE_FULLSCREEN
            config.isLandscape ->
                LayoutMode.TABLET_DESKTOP_COLUMNS
            else ->
                LayoutMode.PORTRAIT_SINGLE_FLOW_TABS
        }

        if (targetMode == currentLayoutMode) return
        currentLayoutMode = targetMode

        // Detach shared subtrees before re-parenting so their NativeImpls remain intact
        (primaryContent.parent as? Container)?.removeChild(primaryContent)
        (tabContainer.parent as? Container)?.removeChild(tabContainer)
        clearChildren()

        when (targetMode) {
            LayoutMode.PHONE_LANDSCAPE_FULLSCREEN -> {
                setFullscreen(true, DisplayCutoutMode.SHORT_EDGES)
                fullscreenWrapper.clearChildren()
                fullscreenWrapper.addChild(primaryContent)
                addChild(fullscreenWrapper)
            }
            LayoutMode.TABLET_DESKTOP_COLUMNS -> {
                setFullscreen(false)
                landscapeRow.clearChildren()
                landscapeLeftCol.clearChildren()
                landscapeRightCol.clearChildren()
                landscapeLeftCol.addChild(primaryContent)
                landscapeRightCol.addChild(tabContainer)
                landscapeRow.addChild(landscapeLeftCol)
                landscapeRow.addChild(landscapeRightCol)
                optionalThirdColumn?.let { third ->
                    landscapeThirdCol.clearChildren()
                    landscapeThirdCol.addChild(third)
                    landscapeRow.addChild(landscapeThirdCol)
                }
                addChild(landscapeRow)
            }
            LayoutMode.PORTRAIT_SINGLE_FLOW_TABS -> {
                setFullscreen(false)
                portraitStack.clearChildren()
                portraitStack.addChild(primaryContent)
                portraitStack.addChild(tabContainer)
                addChild(portraitStack)
            }
        }
    }
}
