package com.nexustools.genericui.component.dialog

/**
 * Rich cross-platform file-type filter definition for [FileBrowserDialog], [FileOpenDialog], and [FileSaveDialog].
 *
 * - On Desktop (`:windows`, `:macos`, `:gtk`, `:qt`, `:swing`), populates the native file dialog's
 *   filter dropdown (`description` + extension patterns).
 * - On `:web`, translates all [extensions] and [mimeTypes] into the invisible `<input type="file" accept="...">`
 *   attribute via [toWebAcceptAttribute] and performs post-selection validation.
 * - On `:android`, translates [extensions] and [mimeTypes] into `Intent.EXTRA_MIME_TYPES` for the native
 *   Android system file picker (`Intent.ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`).
 * - On `:ios`, translates [extensions], [mimeTypes], and [macOsUniformTypeIdentifiers] into `UTType`s
 *   for `UIDocumentPickerViewController`.
 */
data class FileTypeFilter(
    val description: String,
    val extensions: List<String> = emptyList(),
    val mimeTypes: List<String> = emptyList(),
    val macOsUniformTypeIdentifiers: List<String> = emptyList()
) {
    val normalizedExtensions: List<String>
        get() = extensions.map { it.trim().trimStart('*', '.').lowercase() }.filter { it.isNotEmpty() }

    fun matchesFileName(fileName: String): Boolean {
        val cleanExts = normalizedExtensions
        if (cleanExts.isEmpty() || cleanExts.contains("*")) return true
        val lower = fileName.lowercase()
        return cleanExts.any { ext -> lower.endsWith(".$ext") }
    }

    fun toDesktopPatternString(): String {
        val cleanExts = normalizedExtensions
        return if (cleanExts.isEmpty()) "*.*" else cleanExts.joinToString(";") { "*.$it" }
    }

    companion object {
        val ALL_FILES = FileTypeFilter("All Files (*.*)", listOf("*"), listOf("*/*"))

        /**
         * Translates a list of [FileTypeFilter]s into a Web HTML `<input type="file" accept="...">` string.
         */
        fun toWebAcceptAttribute(filters: List<FileTypeFilter>): String {
            if (filters.isEmpty() || filters.any { it.normalizedExtensions.contains("*") || it.mimeTypes.contains("*/*") }) {
                return ""
            }
            val tokens = linkedSetOf<String>()
            for (filter in filters) {
                for (ext in filter.normalizedExtensions) {
                    tokens.add(".$ext")
                }
                for (mime in filter.mimeTypes) {
                    if (mime.isNotBlank()) tokens.add(mime.trim())
                }
            }
            return tokens.joinToString(",")
        }

        /**
         * Translates a list of [FileTypeFilter]s into Android `Intent.EXTRA_MIME_TYPES` array.
         */
        fun toAndroidMimeTypes(filters: List<FileTypeFilter>): List<String> {
            if (filters.isEmpty()) return listOf("*/*")
            val mimes = linkedSetOf<String>()
            for (filter in filters) {
                for (mime in filter.mimeTypes) {
                    if (mime.isNotBlank()) mimes.add(mime.trim())
                }
                for (ext in filter.normalizedExtensions) {
                    val inferred = inferMimeTypeFromExtension(ext)
                    if (inferred != null) {
                        mimes.add(inferred)
                    } else {
                        return listOf("*/*")
                    }
                }
            }
            return if (mimes.isEmpty()) listOf("*/*") else mimes.toList()
        }

        fun inferMimeTypeFromExtension(ext: String): String? = when (ext.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            "ico" -> "image/x-icon"
            "tiff", "tif" -> "image/tiff"
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "txt", "md", "vtt", "srt" -> "text/plain"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> null
        }
    }
}
