package com.photomap.app.data.gallery

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedUrlRefreshTest {
    @Test
    fun detectsForbiddenStatusInCauseChain() {
        val error = IOException("image failed", IOException("HTTP 403"))

        assertTrue(isExpiredSignedUrlError(error))
    }

    @Test
    fun regularNetworkFailureDoesNotTriggerUrlRefresh() {
        assertFalse(isExpiredSignedUrlError(IOException("connection refused")))
    }
}
