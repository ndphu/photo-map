package com.photomap.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedAccountPolicyTest {
    @Test
    fun sameAccountKeepsCachedData() {
        assertFalse(shouldResetCachedAccount(cachedUserId = "user-a", authenticatedUserId = "user-a"))
    }

    @Test
    fun differentAccountResetsCachedData() {
        assertTrue(shouldResetCachedAccount(cachedUserId = "user-a", authenticatedUserId = "user-b"))
    }

    @Test
    fun unknownOwnerResetsCachedData() {
        assertTrue(shouldResetCachedAccount(cachedUserId = null, authenticatedUserId = "user-a"))
    }

    @Test
    fun activeSessionAdoptsExistingCacheAfterUpgrade() {
        assertEquals("user-a", resolveCachedUserId(cachedUserId = null, activeUserId = "user-a"))
        assertEquals("user-b", resolveCachedUserId(cachedUserId = "user-b", activeUserId = "user-a"))
    }
}
