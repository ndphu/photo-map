package com.photomap.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMetadataExtractorTest {
    @Test
    fun parsesIso6709VideoLocation() {
        val location = parseVideoLocation("+10.123000+106.456000/")

        assertEquals(10.123, location?.first ?: 0.0, 0.000001)
        assertEquals(106.456, location?.second ?: 0.0, 0.000001)
    }

    @Test
    fun rejectsOutOfRangeVideoLocation() {
        assertNull(parseVideoLocation("+91.000000+106.000000/"))
    }

    @Test
    fun parsesVideoRecordedDate() {
        assertEquals(
            "2026-06-22T03:30:00Z",
            parseVideoDate("20260622T103000.000+0700")?.toString(),
        )
    }
}
