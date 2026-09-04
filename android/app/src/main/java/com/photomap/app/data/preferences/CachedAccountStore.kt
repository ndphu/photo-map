package com.photomap.app.data.preferences

import android.content.Context

class CachedAccountStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    fun cachedUserId(): String? = preferences.getString(KEY_CACHED_USER_ID, null)

    fun save(userId: String) {
        preferences.edit().putString(KEY_CACHED_USER_ID, userId).apply()
    }

    fun adoptIfMissing(userId: String?) {
        val currentUserId = cachedUserId()
        val resolvedUserId = resolveCachedUserId(currentUserId, userId)
        if (currentUserId == null && resolvedUserId != null) save(resolvedUserId)
    }

    fun clear() {
        preferences.edit().remove(KEY_CACHED_USER_ID).apply()
    }

    private companion object {
        const val FILE_NAME = "cached_account"
        const val KEY_CACHED_USER_ID = "cached_user_id"
    }
}

internal fun shouldResetCachedAccount(cachedUserId: String?, authenticatedUserId: String): Boolean =
    cachedUserId != authenticatedUserId

internal fun resolveCachedUserId(cachedUserId: String?, activeUserId: String?): String? =
    cachedUserId ?: activeUserId
