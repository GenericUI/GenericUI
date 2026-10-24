package com.nexustools.genericui.component.lazy

/**
 * Adapter contract for supplying item count, view types, and lazy creation/binding/unbinding
 * of [ViewHolder]s in a [LazyList] or [LazyGrid].
 */
abstract class LazyAdapter<VH : ViewHolder> {
    private val observers = mutableListOf<() -> Unit>()

    abstract fun getItemCount(): Int

    open fun getItemViewType(position: Int): Int = 0

    open fun getItemId(position: Int): Long = position.toLong()

    abstract fun onCreateViewHolder(viewType: Int): VH

    abstract fun onBindViewHolder(holder: VH, position: Int)

    /**
     * Called when an item scrolls out of the visible viewport window so resources, images,
     * or listeners can be released before the [ViewHolder] is returned to the [ViewHolderPool].
     */
    open fun onUnbindViewHolder(holder: VH, position: Int) {}

    fun registerDataSetObserver(observer: () -> Unit) {
        observers.add(observer)
    }

    fun unregisterDataSetObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    fun notifyDataSetChanged() {
        for (obs in observers) obs()
    }
}
