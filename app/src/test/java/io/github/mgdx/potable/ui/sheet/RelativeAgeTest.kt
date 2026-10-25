package io.github.mgdx.potable.ui.sheet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RelativeAgeTest {
    /** 2026-10-25 11:00 in Paris (UTC+1, winter time since that morning). */
    private val now = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC)

    private fun ageOf(date: String) = RelativeAge.of(Instant.parse(date), now)

    @Test
    fun `a sample taken today is today`() {
        assertEquals(RelativeAge.Today, ageOf("2026-10-25T06:00:00Z"))
    }

    @Test
    fun `a future date is today, not a negative age`() {
        assertEquals(RelativeAge.Today, ageOf("2026-11-02T06:00:00Z"))
    }

    @Test
    fun `the sample date is the published one, read in UTC`() {
        // "22:30Z" is French legal time wrongly suffixed with Z: still the 24th.
        assertEquals(RelativeAge.Days(1), ageOf("2026-10-24T22:30:00Z"))
        assertEquals(RelativeAge.Days(1), ageOf("2026-10-24T23:59:00Z"))
        assertEquals(RelativeAge.Today, ageOf("2026-10-25T00:00:00Z"))
    }

    @Test
    fun `today is the current date in France`() {
        // 23:30 UTC on the 24th is already the 25th in Paris (UTC+2 then).
        val lateEvening = Clock.fixed(Instant.parse("2026-10-24T22:30:00Z"), ZoneOffset.UTC)
        assertEquals(
            RelativeAge.Today,
            RelativeAge.of(Instant.parse("2026-10-25T08:00:00Z"), lateEvening),
        )
    }

    @Test
    fun `under a week is counted in days`() {
        assertEquals(RelativeAge.Days(6), ageOf("2026-10-19T08:00:00Z"))
    }

    @Test
    fun `under a month is counted in weeks`() {
        assertEquals(RelativeAge.Weeks(1), ageOf("2026-10-18T08:00:00Z"))
        assertEquals(RelativeAge.Weeks(3), ageOf("2026-10-01T08:00:00Z"))
        assertEquals(RelativeAge.Weeks(4), ageOf("2026-09-26T08:00:00Z"))
    }

    @Test
    fun `under a year is counted in months`() {
        assertEquals(RelativeAge.Months(1), ageOf("2026-09-25T08:00:00Z"))
        assertEquals(RelativeAge.Months(11), ageOf("2025-10-26T08:00:00Z"))
    }

    @Test
    fun `a year or more is counted in years`() {
        assertEquals(RelativeAge.Years(1), ageOf("2025-10-25T08:00:00Z"))
        assertEquals(RelativeAge.Years(3), ageOf("2023-01-10T08:00:00Z"))
    }
}
