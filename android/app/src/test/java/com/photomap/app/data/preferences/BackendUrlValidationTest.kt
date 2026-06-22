package com.photomap.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendUrlValidationTest {
    @Test
    fun httpsUrlIsNormalizedWithTrailingSlash() {
        assertEquals(
            "https://api.example.com/",
            normalizeBackendBaseUrl("  https://api.example.com  "),
        )
    }

    @Test
    fun privateAndLoopbackHttpUrlsAreAllowed() {
        assertEquals("http://10.0.2.2:8080/", normalizeBackendBaseUrl("http://10.0.2.2:8080"))
        assertEquals("http://172.16.1.5/", normalizeBackendBaseUrl("http://172.16.1.5"))
        assertEquals("http://192.168.1.20/", normalizeBackendBaseUrl("http://192.168.1.20"))
        assertEquals("http://localhost:8080/", normalizeBackendBaseUrl("http://localhost:8080"))
        assertEquals("http://127.0.0.1/", normalizeBackendBaseUrl("http://127.0.0.1"))
    }

    @Test
    fun publicHttpUrlIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeBackendBaseUrl("http://example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeBackendBaseUrl("http://8.8.8.8")
        }
    }

    @Test
    fun credentialsPathQueryAndFragmentAreRejected() {
        listOf(
            "https://user:password@example.com",
            "https://example.com/api",
            "https://example.com/?page=1",
            "https://example.com/#fragment",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                normalizeBackendBaseUrl(value)
            }
        }
    }

    @Test
    fun effectiveUrlUsesCustomOnlyWhenEnabled() {
        val defaultConfiguration = backendUrlConfiguration(
            defaultBaseUrl = "https://default.example/",
            useCustomUrl = false,
            customBaseUrl = "https://custom.example/",
        )
        val customConfiguration = backendUrlConfiguration(
            defaultBaseUrl = "https://default.example/",
            useCustomUrl = true,
            customBaseUrl = "https://custom.example/",
        )

        assertEquals("https://default.example/", defaultConfiguration.effectiveBaseUrl)
        assertEquals("https://custom.example/", customConfiguration.effectiveBaseUrl)
    }
}
