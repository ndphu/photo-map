package com.photomap.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetMutationQueueTest {
    @Test
    fun statePayloadRoundTrips() {
        assertTrue(parseStatePayload(statePayload(true)))
        assertFalse(parseStatePayload(statePayload(false)))
    }

    @Test(expected = IllegalStateException::class)
    fun invalidStatePayloadIsRejected() {
        parseStatePayload("{\"value\":null}")
    }
}
