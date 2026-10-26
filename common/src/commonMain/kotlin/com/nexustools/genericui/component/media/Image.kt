package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.content.ImageView
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceResolver

/**
 * Cross-platform image component (`<img>`) accepting a URL/path string or a compiled [ResourceID]
 * (`R.drawable.*` / `Resource.drawable.*`).
 */
open class Image(
    src: String = "",
    alt: String = "",
    objectFit: ObjectFit = ObjectFit.CONTAIN
) : ImageView(
    sourceUrl = src.ifEmpty { null },
    objectFit = objectFit
) {
    init {
        if (alt.isNotEmpty()) {
            fallbackText = alt
        }
    }

    constructor(
        resId: ResourceID,
        alt: String = resId.name
    ) : this(
        src = ResourceResolver.getUrl(resId),
        alt = alt
    ) {
        this.resourceId = resId
    }

    override fun setSource(resId: ResourceID) {
        super.setSource(resId)
    }

    fun setSource(url: String) {
        this.resourceId = null
        this.sourceUrl = url
    }
}
