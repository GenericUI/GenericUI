package com.nexustools.genericui.apple

import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.image.ImageFormat
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeBMP
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeGIF
import platform.UniformTypeIdentifiers.UTTypeICO
import platform.UniformTypeIdentifiers.UTTypeJPEG
import platform.UniformTypeIdentifiers.UTTypePNG
import platform.UniformTypeIdentifiers.UTTypeSVG
import platform.UniformTypeIdentifiers.UTTypeTIFF
import platform.UniformTypeIdentifiers.UTTypeWebP
import platform.UniformTypeIdentifiers.conformsToType

/**
 * Resolves extensions, MIME types, and Uniform Type Identifiers using Apple's
 * UniformTypeIdentifiers framework (`UTType`) without any AppKit/UIKit dependency.
 */
object AppleUniformTypeHelper {
    fun normalizeExtension(extension: String): String {
        return extension.trim().removePrefix("*.").removePrefix(".").lowercase()
    }

    fun normalizeMimeType(mimeType: String): String {
        return mimeType.substringBefore(';').trim().lowercase()
    }

    fun extensionFromPath(pathOrFileName: String): String? {
        val lastSegment = pathOrFileName.substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBefore('?')
            .substringBefore('#')
        val dotIndex = lastSegment.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == lastSegment.lastIndex) return null
        return normalizeExtension(lastSegment.substring(dotIndex + 1)).takeIf { it.isNotEmpty() }
    }

    fun utTypeForExtension(extension: String): UTType? {
        val clean = normalizeExtension(extension)
        if (clean.isEmpty() || clean == "*") return null
        return UTType.typeWithFilenameExtension(clean)
    }

    fun utTypeForMimeType(mimeType: String): UTType? {
        val clean = normalizeMimeType(mimeType)
        if (clean.isEmpty() || clean == "*/*") return null
        return UTType.typeWithMIMEType(clean)
    }

    fun utTypeForIdentifier(identifier: String): UTType? {
        val clean = identifier.trim()
        if (clean.isEmpty()) return null
        return UTType.typeWithIdentifier(clean)
    }

    fun mimeTypeForExtension(extension: String): String? {
        return utTypeForExtension(extension)?.preferredMIMEType
    }

    fun mimeTypeForPath(pathOrFileName: String): String? {
        val ext = extensionFromPath(pathOrFileName) ?: return null
        return mimeTypeForExtension(ext)
    }

    fun extensionForMimeType(mimeType: String): String? {
        return utTypeForMimeType(mimeType)?.preferredFilenameExtension
    }

    fun identifierForExtension(extension: String): String? {
        return utTypeForExtension(extension)?.identifier
    }

    fun identifierForMimeType(mimeType: String): String? {
        return utTypeForMimeType(mimeType)?.identifier
    }

    fun conformsTo(extensionOrIdentifier: String, targetIdentifier: String): Boolean {
        val target = utTypeForIdentifier(targetIdentifier) ?: return false
        val candidate = if ('.' in extensionOrIdentifier) {
            utTypeForIdentifier(extensionOrIdentifier)
        } else {
            utTypeForExtension(extensionOrIdentifier)
        } ?: return false
        return candidate.conformsToType(target)
    }

    fun utTypeForImageFormat(format: ImageFormat): UTType? {
        return when (format) {
            ImageFormat.PNG -> UTTypePNG
            ImageFormat.JPEG -> UTTypeJPEG
            ImageFormat.WEBP -> UTTypeWebP
            ImageFormat.GIF -> UTTypeGIF
            ImageFormat.BMP -> UTTypeBMP
            ImageFormat.ICO -> UTTypeICO
            ImageFormat.TIFF -> UTTypeTIFF
            ImageFormat.SVG -> UTTypeSVG
            ImageFormat.PPM, ImageFormat.RAW_RGBA, ImageFormat.UNKNOWN -> null
        }
    }

    fun utiForImageFormat(format: ImageFormat): String? {
        return utTypeForImageFormat(format)?.identifier
    }

    fun imageFormatForUti(utiIdentifier: String?): ImageFormat {
        val uti = utiIdentifier?.let { utTypeForIdentifier(it) } ?: return ImageFormat.UNKNOWN
        return when {
            uti.conformsToType(UTTypePNG) -> ImageFormat.PNG
            uti.conformsToType(UTTypeJPEG) -> ImageFormat.JPEG
            uti.conformsToType(UTTypeWebP) -> ImageFormat.WEBP
            uti.conformsToType(UTTypeGIF) -> ImageFormat.GIF
            uti.conformsToType(UTTypeBMP) -> ImageFormat.BMP
            uti.conformsToType(UTTypeICO) -> ImageFormat.ICO
            uti.conformsToType(UTTypeTIFF) -> ImageFormat.TIFF
            uti.conformsToType(UTTypeSVG) -> ImageFormat.SVG
            else -> ImageFormat.UNKNOWN
        }
    }

    fun utTypesForFilters(filters: List<FileTypeFilter>): List<UTType> {
        if (filters.isEmpty()) return emptyList()
        val seenIds = LinkedHashSet<String>()
        val result = ArrayList<UTType>()
        fun addIfPresent(type: UTType?) {
            if (type != null && seenIds.add(type.identifier)) {
                result.add(type)
            }
        }
        for (filter in filters) {
            for (uti in filter.macOsUniformTypeIdentifiers) {
                addIfPresent(utTypeForIdentifier(uti))
            }
            for (mime in filter.mimeTypes) {
                if (mime == "*/*") {
                    addIfPresent(UTTypeData)
                } else {
                    addIfPresent(utTypeForMimeType(mime))
                }
            }
            for (ext in filter.normalizedExtensions) {
                if (ext == "*") {
                    addIfPresent(UTTypeData)
                } else {
                    addIfPresent(utTypeForExtension(ext))
                }
            }
        }
        return result
    }
}
