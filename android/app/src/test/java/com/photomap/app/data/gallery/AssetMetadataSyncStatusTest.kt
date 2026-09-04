package com.photomap.app.data.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssetMetadataSyncStatusTest {
    @Test
    fun progressIsIndeterminateBeforeBackendReportsRemainingCount() {
        val status = AssetMetadataSyncStatus(
            isSyncing = true,
            completedCount = 500,
            remainingCount = null,
        )

        assertNull(status.totalCount)
        assertNull(status.percent)
    }

    @Test
    fun progressUsesCommittedAndRemainingChangeCounts() {
        val status = AssetMetadataSyncStatus(
            isSyncing = true,
            completedCount = 750,
            remainingCount = 250,
        )

        assertEquals(1_000L, status.totalCount)
        assertEquals(75, status.percent)
    }

    @Test
    fun emptyBacklogIsComplete() {
        val status = AssetMetadataSyncStatus(
            isSyncing = true,
            completedCount = 0,
            remainingCount = 0,
        )

        assertEquals(0L, status.totalCount)
        assertEquals(100, status.percent)
    }
}
