package com.nexustools.genericui.res

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Cross-platform RFC 2397 Data-URL (`data:<mimeType>;base64,...`) encoder, decoder, and MIME type resolver.
 */
@OptIn(ExperimentalEncodingApi::class)
object DataUrlCodec {

    /**
     * Encodes raw [bytes] into a base64 Data-URL with the given [mimeType]
     * (`"data:$mimeType;base64,${encodeBase64(bytes)}"`).
     */
    fun encodeBytes(bytes: ByteArray, mimeType: String): String {
        val normalizedMime = mimeType.trim().ifEmpty { "application/octet-stream" }
        return "data:$normalizedMime;base64,${encodeBase64(bytes)}"
    }

    /**
     * Encodes UTF-8 [text] into a base64 Data-URL with the given [mimeType]
     * (`"data:$mimeType;base64,${encodeBase64(text.encodeToByteArray())}"`).
     */
    fun encodeUtf8Text(text: String, mimeType: String = "text/plain;charset=utf-8"): String {
        val normalizedMime = mimeType.trim().ifEmpty { "text/plain;charset=utf-8" }
        return "data:$normalizedMime;base64,${encodeBase64(text.encodeToByteArray())}"
    }

    /**
     * Decodes the binary payload of a `data:` URL, returning `null` if [dataUrl] is not a valid Data-URL.
     */
    fun decodeBytes(dataUrl: String): ByteArray? {
        val trimmed = dataUrl.trim()
        if (!isDataUrl(trimmed)) return null
        val commaIndex = trimmed.indexOf(',')
        if (commaIndex < 5) return null

        val header = trimmed.substring(5, commaIndex)
        val payload = trimmed.substring(commaIndex + 1)
        val isBase64 = header.split(';').any { it.trim().equals("base64", ignoreCase = true) }

        return if (isBase64) {
            decodeBase64(payload)
        } else {
            decodePercentEncodedUtf8(payload)
        }
    }

    /**
     * Decodes a `data:` URL payload as a UTF-8 string, returning `null` if [dataUrl] is not a valid Data-URL.
     */
    fun decodeUtf8Text(dataUrl: String): String? =
        decodeBytes(dataUrl)?.decodeToString()

    /**
     * Extracts the MIME type (for example `"text/css"` or `"image/png"`) from [dataUrl],
     * or returns `null` if [dataUrl] is not a valid Data-URL.
     */
    fun extractMimeType(dataUrl: String): String? {
        val trimmed = dataUrl.trim()
        if (!isDataUrl(trimmed)) return null
        val commaIndex = trimmed.indexOf(',')
        if (commaIndex < 5) return null

        val header = trimmed.substring(5, commaIndex).trim()
        if (header.isEmpty()) return "text/plain"

        val parts = header.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("base64", ignoreCase = true) }

        val mediaType = parts.firstOrNull { it.contains('/') }
        return mediaType ?: "text/plain"
    }

    /**
     * Returns `true` if [url] starts with the `"data:"` scheme prefix (case-insensitive).
     */
    fun isDataUrl(url: String): Boolean = url.startsWith("data:", ignoreCase = true)

    /**
     * Infers the MIME type from a file path, asset name, or extension [pathOrName], falling back
     * to [resourceType] (`"style"`, `"drawable"`, `"raw"`, `"font"`, `"xml"`, etc.) when no known
     * file extension is present.
     */
    fun guessMimeType(pathOrName: String, resourceType: String? = null): String {
        val clean = pathOrName.trim()
            .substringBefore('?')
            .substringBefore('#')
            .replace('\\', '/')
            .trim()

        val ext = if (clean.contains('.')) {
            clean.substringAfterLast('.').lowercase()
        } else {
            clean.lowercase()
        }

        when (ext) {
            "css", "scss", "sass" -> return "text/css"
            "png" -> return "image/png"
            "jpg", "jpeg" -> return "image/jpeg"
            "gif" -> return "image/gif"
            "webp" -> return "image/webp"
            "svg", "svgz" -> return "image/svg+xml"
            "ico", "cur" -> return "image/x-icon"
            "bmp" -> return "image/bmp"
            "avif" -> return "image/avif"
            "woff2" -> return "font/woff2"
            "woff" -> return "font/woff"
            "ttf" -> return "font/ttf"
            "otf" -> return "font/otf"
            "eot" -> return "application/vnd.ms-fontobject"
            "json", "webmanifest" -> return "application/json"
            "xml" -> return "application/xml"
            "txt", "text", "md", "csv" -> return "text/plain"
            "html", "htm" -> return "text/html"
            "js", "mjs" -> return "text/javascript"
            "mp3" -> return "audio/mpeg"
            "wav" -> return "audio/wav"
            "ogg", "oga" -> return "audio/ogg"
            "mp4", "m4v" -> return "video/mp4"
            "webm" -> return "video/webm"
            "wasm" -> return "application/wasm"
            "pdf" -> return "application/pdf"
        }

        val inferredType = resourceType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: inferResourceTypeFromUri(clean)

        return when (inferredType) {
            "style", "styles", "stylesheet", "css", "scss" -> "text/css"
            "drawable", "image", "icon", "mipmap" -> "image/png"
            "font", "fonts" -> "font/ttf"
            "xml", "layout", "menu" -> "application/xml"
            "string", "text" -> "text/plain"
            "json" -> "application/json"
            "raw" -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }

    private fun inferResourceTypeFromUri(cleanPath: String): String? {
        val lower = cleanPath.lowercase()
        if (lower.startsWith("res://")) {
            return lower.removePrefix("res://").substringBefore('/').takeIf { it.isNotEmpty() }
        }
        if (lower.startsWith("@") && lower.contains('/')) {
            return lower.removePrefix("@").substringBefore('/').takeIf { it.isNotEmpty() }
        }
        val firstSegment = lower.removePrefix("/").substringBefore('/', "")
        if (firstSegment in setOf("styles", "style", "drawable", "font", "fonts", "xml", "raw")) {
            return firstSegment
        }
        if (lower.startsWith("assets/")) {
            val secondSegment = lower.removePrefix("assets/").substringBefore('/', "")
            if (secondSegment.isNotEmpty()) return secondSegment
        }
        return null
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.encode(bytes)

    private fun decodeBase64(base64Payload: String): ByteArray? {
        val compact = buildString(base64Payload.length) {
            for (ch in base64Payload) {
                if (!ch.isWhitespace()) append(ch)
            }
        }
        return try {
            Base64.decode(compact)
        } catch (_: Throwable) {
            try {
                Base64.UrlSafe.decode(compact)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun decodePercentEncodedUtf8(payload: String): ByteArray {
        val out = ArrayList<Byte>(payload.length)
        var i = 0
        while (i < payload.length) {
            val ch = payload[i]
            if (ch == '%' && i + 2 < payload.length) {
                val hex = payload.substring(i + 1, i + 3)
                val value = hex.toIntOrNull(16)
                if (value != null) {
                    out.add(value.toByte())
                    i += 3
                    continue
                }
            }
            for (b in ch.toString().encodeToByteArray()) {
                out.add(b)
            }
            i++
        }
        return out.toByteArray()
    }
}
