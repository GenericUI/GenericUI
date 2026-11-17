package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.net.DefaultHttpRequestNativeImpl
import com.nexustools.genericui.net.http.HttpMethod

/**
 * Shared JVM and Android (`minSdk >= 24`) implementation of
 * [com.nexustools.genericui.nativeimpl.net.HttpRequestNativeImpl] backed by
 * `java.net.HttpURLConnection` / `javax.net.ssl.HttpsURLConnection` output streaming.
 */
open class JvmHttpRequestNativeImpl(
    url: String,
    method: HttpMethod = HttpMethod.GET,
    headers: Map<String, String> = emptyMap(),
    initialBodyBytes: ByteArray? = null,
    timeoutMs: Long = 30_000L,
    followRedirects: Boolean = true,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : DefaultHttpRequestNativeImpl(
    url = url,
    method = method,
    headers = headers,
    initialBodyBytes = initialBodyBytes,
    timeoutMs = timeoutMs,
    followRedirects = followRedirects,
    byteOrder = byteOrder
) {
    open val nativeApi: String = "java_net_HttpURLConnection_OutputStream"
}
