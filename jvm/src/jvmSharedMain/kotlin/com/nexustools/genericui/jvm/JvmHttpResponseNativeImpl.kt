package com.nexustools.genericui.jvm

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.net.DefaultHttpResponseNativeImpl

/**
 * Shared JVM and Android (`minSdk >= 24`) implementation of
 * [com.nexustools.genericui.nativeimpl.net.HttpResponseNativeImpl] backed by
 * `java.net.HttpURLConnection` / `javax.net.ssl.HttpsURLConnection` input streaming.
 */
open class JvmHttpResponseNativeImpl(
    statusCode: Int,
    statusText: String = "",
    headers: Map<String, String> = emptyMap(),
    initialBodyBytes: ByteArray = ByteArray(0),
    url: String = "",
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : DefaultHttpResponseNativeImpl(
    statusCode = statusCode,
    statusText = statusText,
    headers = headers,
    initialBodyBytes = initialBodyBytes,
    url = url,
    byteOrder = byteOrder
) {
    open val nativeApi: String = "java_net_HttpURLConnection_InputStream"
}
