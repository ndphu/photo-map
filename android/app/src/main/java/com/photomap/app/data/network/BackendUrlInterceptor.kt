package com.photomap.app.data.network

import com.photomap.app.data.preferences.BackendUrlStore
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class BackendUrlInterceptor(
    private val backendUrlStore: BackendUrlStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val rewrittenUrl = rewriteBackendRequestUrl(request.url, backendUrlStore.currentBaseUrl())
        return chain.proceed(request.newBuilder().url(rewrittenUrl).build())
    }
}

internal fun rewriteBackendRequestUrl(requestUrl: HttpUrl, backendBaseUrl: HttpUrl): HttpUrl =
    requestUrl.newBuilder()
        .scheme(backendBaseUrl.scheme)
        .host(backendBaseUrl.host)
        .port(backendBaseUrl.port)
        .build()
