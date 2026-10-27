package com.nexustools.genericui.resources

import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.res.DrawableResource
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Canonical resource identifier alias for [com.nexustools.genericui.res.ResourceId],
 * with convenience extension helpers delegating to [ResourceResolver].
 */
typealias ResourceID = com.nexustools.genericui.res.ResourceId

/** Resolves this [ResourceID] as a localized/formatted string via [ResourceResolver.getString]. */
fun ResourceID.getString(vararg formatArgs: Any): String =
    ResourceResolver.getString(this, *formatArgs)

/** Resolves this [ResourceID] as an [RgbaColor] via [ResourceResolver.getColor]. */
fun ResourceID.getColor(): RgbaColor =
    ResourceResolver.getColor(this)

/** Resolves this [ResourceID] as a pixel dimension (`Float`) via [ResourceResolver.getDimenPx]. */
fun ResourceID.getDimenPx(): Float =
    ResourceResolver.getDimenPx(this)

/** Resolves this [ResourceID] as an integer resource via [ResourceResolver.getInteger]. */
fun ResourceID.getInteger(): Int =
    ResourceResolver.getInteger(this)

/** Resolves this [ResourceID] as a boolean resource via [ResourceResolver.getBool]. */
fun ResourceID.getBool(): Boolean =
    ResourceResolver.getBool(this)

/** Resolves this [ResourceID] to a backend-appropriate URL or resource URI via [ResourceResolver.getUrl]. */
fun ResourceID.getUrl(): String =
    ResourceResolver.getUrl(this)

/** Resolves this [ResourceID] to raw binary bytes via [ResourceResolver.getBytes]. */
fun ResourceID.getBytes(): ByteArray? =
    ResourceResolver.getBytes(this)

/** Decodes this [ResourceID] into an [ImageBuffer] via [ResourceResolver.getImageBuffer]. */
fun ResourceID.getImageBuffer(): ImageBuffer? =
    ResourceResolver.getImageBuffer(this)

/** Resolves this [ResourceID] as a [DrawableResource] via [ResourceResolver.getDrawable]. */
fun ResourceID.getDrawable(): DrawableResource? =
    ResourceResolver.getDrawable(this)

/** Resolves this [ResourceID] as a [com.nexustools.genericui.style.css.PrecompiledStyleSheet] via [ResourceResolver.getStyleSheet]. */
fun ResourceID.getStyleSheet(): com.nexustools.genericui.style.css.PrecompiledStyleSheet? =
    ResourceResolver.getStyleSheet(this)

