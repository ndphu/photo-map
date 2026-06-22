package com.photomap.app.data.preferences

import android.content.Context
import com.photomap.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class BackendUrlConfiguration(
    val defaultBaseUrl: String,
    val useCustomUrl: Boolean,
    val customBaseUrl: String,
    val effectiveBaseUrl: String,
)

class BackendUrlStore(
    context: Context,
    defaultBaseUrl: String = BuildConfig.API_BASE_URL,
) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val normalizedDefaultBaseUrl = normalizeBackendBaseUrl(defaultBaseUrl)
    private val _configuration = MutableStateFlow(readConfiguration())

    val configuration: StateFlow<BackendUrlConfiguration> = _configuration.asStateFlow()

    fun currentBaseUrl(): HttpUrl = _configuration.value.effectiveBaseUrl.toHttpUrlOrNull()
        ?: error("Effective backend URL is invalid")

    fun preview(useCustomUrl: Boolean, customBaseUrl: String): BackendUrlConfiguration {
        val normalizedCustomUrl = if (useCustomUrl) {
            normalizeBackendBaseUrl(customBaseUrl)
        } else {
            customBaseUrl.trim()
        }
        return backendUrlConfiguration(
            defaultBaseUrl = normalizedDefaultBaseUrl,
            useCustomUrl = useCustomUrl,
            customBaseUrl = normalizedCustomUrl,
        )
    }

    fun save(configuration: BackendUrlConfiguration) {
        preferences.edit()
            .putBoolean(KEY_USE_CUSTOM_URL, configuration.useCustomUrl)
            .putString(KEY_CUSTOM_BASE_URL, configuration.customBaseUrl)
            .apply()
        _configuration.value = configuration
    }

    private fun readConfiguration(): BackendUrlConfiguration {
        val useCustomUrl = preferences.getBoolean(KEY_USE_CUSTOM_URL, false)
        val customBaseUrl = preferences.getString(KEY_CUSTOM_BASE_URL, "").orEmpty()
        return runCatching { preview(useCustomUrl, customBaseUrl) }
            .getOrElse {
                backendUrlConfiguration(normalizedDefaultBaseUrl, false, "")
            }
    }

    private companion object {
        const val FILE_NAME = "backend_url_settings"
        const val KEY_USE_CUSTOM_URL = "use_custom_url"
        const val KEY_CUSTOM_BASE_URL = "custom_base_url"
    }
}

internal fun backendUrlConfiguration(
    defaultBaseUrl: String,
    useCustomUrl: Boolean,
    customBaseUrl: String,
): BackendUrlConfiguration = BackendUrlConfiguration(
    defaultBaseUrl = defaultBaseUrl,
    useCustomUrl = useCustomUrl,
    customBaseUrl = customBaseUrl,
    effectiveBaseUrl = if (useCustomUrl) customBaseUrl else defaultBaseUrl,
)

fun normalizeBackendBaseUrl(value: String): String {
    val url = value.trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Enter a valid backend URL")
    require(url.username.isEmpty() && url.password.isEmpty()) {
        "Backend URL cannot contain credentials"
    }
    require(url.encodedPath == "/" && url.query == null && url.fragment == null) {
        "Backend URL must not contain a path, query, or fragment"
    }
    require(url.scheme == "https" || (url.scheme == "http" && isPrivateHttpHost(url.host))) {
        "Use HTTPS, or HTTP with localhost or a private IP address"
    }
    return url.toString()
}

private fun isPrivateHttpHost(host: String): Boolean {
    if (host.equals("localhost", ignoreCase = true) || host == "::1") return true
    val octets = host.split('.').map { it.toIntOrNull() ?: return false }
    if (octets.size != IPV4_OCTET_COUNT || octets.any { it !in 0..255 }) return false
    return octets[0] == 10 ||
        octets[0] == 127 ||
        octets[0] == 192 && octets[1] == 168 ||
        octets[0] == 172 && octets[1] in 16..31
}

private const val IPV4_OCTET_COUNT = 4
