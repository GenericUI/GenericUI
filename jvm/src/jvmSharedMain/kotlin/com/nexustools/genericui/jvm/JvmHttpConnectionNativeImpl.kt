package com.nexustools.genericui.jvm

import com.nexustools.genericui.nativeimpl.net.DefaultHttpConnectionNativeImpl
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse

/**
 * Shared JVM and Android (`minSdk >= 24`) implementation of
 * [com.nexustools.genericui.nativeimpl.net.HttpConnectionNativeImpl] representing a duplex
 * HTTP exchange over `java.net.HttpURLConnection` / `javax.net.ssl.HttpsURLConnection`.
 */
open class JvmHttpConnectionNativeImpl(
    request: HttpRequest,
    response: HttpResponse
) : DefaultHttpConnectionNativeImpl(
    request = request,
    response = response
) {
    open val nativeApi: String = "java_net_HttpURLConnection"
}
