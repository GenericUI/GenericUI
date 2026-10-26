package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.navigation.StepScrollView

/**
 * Single-row-height snapping vertical wheel selector page combining
 * [ValidatedStepPageFragment] with a single-row [StepScrollView].
 */
abstract class VSelectPageFragment<T>(
    stepNames: List<String>,
    currentStepOrdinal: Int,
    var rowHeightPx: Int = 40,
    titlePrefix: String = "Step Wizard"
) : ValidatedStepPageFragment(stepNames, currentStepOrdinal, titlePrefix) {

    val preSelectContainer: Container = Container("div", "vselect-pre")
    val scrollView: StepScrollView = StepScrollView(wrapAround = true, stepHeightPx = rowHeightPx).apply {
        viewportHeightPx = rowHeightPx
    }
    val postSelectContainer: Container = Container("div", "vselect-post")

    var loadedItems: List<T> = emptyList()
        private set

    abstract fun selectItems(
        args: List<Any?>,
        onResult: (List<T>) -> Unit,
        onError: (String) -> Unit
    )

    open fun preSelectView(container: Container, args: List<Any?>) {}

    abstract fun renderRow(rowContainer: Container, item: T, index: Int, args: List<Any?>)

    open fun postSelectView(container: Container, args: List<Any?>) {}

    override fun buildStepView(content: Container, args: List<Any?>) {
        preSelectContainer.clearChildren()
        postSelectContainer.clearChildren()
        preSelectView(preSelectContainer, args)
        postSelectView(postSelectContainer, args)

        content.addChild(preSelectContainer)
        content.addChild(scrollView)
        content.addChild(postSelectContainer)
    }

    override fun onStepController(args: List<Any?>) {
        beginLoading()
        selectItems(
            args = args,
            onResult = { items ->
                loadedItems = items
                scrollView.clearRows()
                scrollView.stepHeightPx = rowHeightPx
                scrollView.viewportHeightPx = rowHeightPx
                scrollView.contentHeightPx = items.size * rowHeightPx
                for (i in items.indices) {
                    val row = Container("tr", "vselect-row")
                    renderRow(row, items[i], i, args)
                    scrollView.addRow(row)
                }
                scrollView.updateButtonStates()
                endLoading()
            },
            onError = { err ->
                endLoading()
                alert(err)
            }
        )
    }

    fun scrollItem(index: Int) {
        scrollView.scrollToItem(index, rowHeightPx)
    }
}
