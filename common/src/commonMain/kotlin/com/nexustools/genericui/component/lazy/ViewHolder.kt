package com.nexustools.genericui.component.lazy

import com.nexustools.genericui.core.AbstractComponent

/**
 * Reusable wrapper around an item component in a [LazyList], [LazyGrid], or [TreeView].
 *
 * Only items inside the active visible viewport window have `isBound == true` and an active
 * `NativeImpl`; when an item scrolls out of the visible viewport, its [ViewHolder] is unbound
 * (`onUnbindViewHolder`) and returned to the [ViewHolderPool].
 */
open class ViewHolder(
    val itemView: AbstractComponent,
    val viewType: Int = 0
) {
    var boundPosition: Int = -1
        internal set

    var isBound: Boolean = false
        internal set
}
