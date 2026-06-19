package com.photomap.app.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.photomap.app.data.network.LoginRequest
import com.photomap.app.data.network.PhotoMapApi
import com.photomap.app.data.network.RegisterDeviceRequest
import com.photomap.app.data.network.RegisterRequest
import com.photomap.app.data.security.SecureTokenStore

class AuthRepository(
    private val context: Context,
    private val api: PhotoMapApi,
    private val tokenStore: SecureTokenStore,
) {
    fun isLoggedIn(): Boolean = tokenStore.accessToken() != null

    suspend fun login(email: String, password: String) {
        val response = api.login(LoginRequest(email.trim(), password))
        tokenStore.saveSession(response.accessToken, response.user.id)
        registerDevice()
    }

    suspend fun register(email: String, password: String, displayName: String) {
        val response = api.register(RegisterRequest(email.trim(), password, displayName.trim()))
        tokenStore.saveSession(response.accessToken, response.user.id)
        registerDevice()
    }

    suspend fun registerDevice() {
        val fingerprint = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: "android-${Build.MANUFACTURER}-${Build.MODEL}"
        val device = api.registerDevice(
            RegisterDeviceRequest(
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                deviceFingerprint = fingerprint,
            ),
        )
        tokenStore.saveDeviceId(device.id)
    }

    fun logout() = tokenStore.clear()
}
