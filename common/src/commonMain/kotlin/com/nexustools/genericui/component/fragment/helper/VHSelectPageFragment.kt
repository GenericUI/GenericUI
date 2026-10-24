package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.content.MasterDetailStepSelect
import com.nexustools.genericui.component.layout.Container

/**
 * Generic master-detail selector step page (`DCGame`'s `CharGenVHSelectPage<T>`) combining
 * [ValidatedStepPageFragment] with a [MasterDetailStepSelect] component.
 */
abstract class VHSelectPageFragment<T>(
    stepNames: List<String>,
    currentStepOrdinal: Int,
    titlePrefix: String = "Character Generator"
) : ValidatedStepPageFragment(stepNames, currentStepOrdinal, titlePrefix) {

    val selector: MasterDetailStepSelect = MasterDetailStepSelect()
    private val loadedItemsById = linkedMapOf<String, T>()

    abstract fun selectItems(
        args: List<Any?>,
        onResult: (Array<T>) -> Unit,
        onError: (String) -> Unit
    )

    open fun idForItem(item: T): String = item.toString()

    open fun nameForItem(item: T): String = idForItem(item)

    open fun perItem(rowContainer: Container, item: T) {}

    abstract fun renderItem(detailContainer: Container, item: T)

    override fun buildStepView(content: Container, args: List<Any?>) {
        content.addChild(selector)
        selector.onItemSelected = { id ->
            val item = loadedItemsById[id]
            if (item != null) {
                selector.detailContainer.clearChildren()
                renderItem(selector.detailContainer, item)
            }
        }
    }

    override fun onStepController(args: List<Any?>) {
        beginLoading()
        selectItems(
            args = args,
            onResult = { items ->
                loadedItemsById.clear()
                selector.clearItems()
                for (item in items) {
                    val id = idForItem(item)
                    val name = nameForItem(item)
                    loadedItemsById[id] = item
                    val row = selector.addItem(id = id, title = name)
                    perItem(row, item)
                }
                endLoading()
            },
            onError = { err ->
                endLoading()
                alert(err)
            }
        )
    }

    fun selectItem(id: String): Boolean = selector.selectItem(id)

    fun clearSelection() = selector.clearSelection()

    fun scrollToTop() = selector.scrollView.scrollToTop()

    fun scrollToBottom() = selector.scrollView.scrollToBottom()
}
