package com.photomap.app.data.gallery

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimelineGroupingTest {
    private val utc = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 19)

    @Test
    fun labelsTodayYesterdayAndUnknownDates() {
        assertEquals("Today", timelineHeaderLabel("2026-06-19T10:00:00Z", today, utc))
        assertEquals("Yesterday", timelineHeaderLabel("2026-06-18T23:59:00Z", today, utc))
        assertEquals("Date unknown", timelineHeaderLabel(null, today, utc))
        assertEquals("Date unknown", timelineHeaderLabel("invalid", today, utc))
    }

    @Test
    fun groupKeyIsStableForAssetsOnSameDate() {
        val morning = timelineGroupKey("2026-06-17T01:00:00Z", utc)
        val evening = timelineGroupKey("2026-06-17T22:00:00Z", utc)
        val nextDay = timelineGroupKey("2026-06-18T01:00:00Z", utc)

        assertEquals(morning, evening)
        assertNotEquals(morning, nextDay)
        assertEquals("unknown", timelineGroupKey(null, utc))
    }
}
