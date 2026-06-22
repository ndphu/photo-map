package com.photomap.app.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendUrlInterceptorTest {
    @Test
    fun rewriteChangesServerAndPreservesRequestPathAndQuery() {
        val requestUrl = "http://build-default/assets/asset-id?variant=preview".toHttpUrl()
        val backendUrl = "https://custom.example.com:8443/".toHttpUrl()

        val rewritten = rewriteBackendRequestUrl(requestUrl, backendUrl)

        assertEquals(
            "https://custom.example.com:8443/assets/asset-id?variant=preview",
            rewritten.toString(),
        )
    }
}
