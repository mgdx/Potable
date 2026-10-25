package io.github.mgdx.potable.ui.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class SampleDateTest {
    /** Read in Paris (UTC+2 in summer), this would be the 27th. */
    private val lateSample = Instant.parse("2026-08-26T23:30:00Z")

    @Test
    fun `a late sample keeps its published date`() {
        assertEquals(LocalDate.of(2026, 8, 26), publishedDate(lateSample))
    }

    @Test
    fun `a late sample is displayed on the 26th, not the 27th`() {
        assertEquals("August 26, 2026", formatSampleDate(lateSample, Locale.US))
        assertEquals("26 août 2026", formatSampleDate(lateSample, Locale.FRANCE))
    }

    @Test
    fun `dates are written with the digits of the locale`() {
        // This RTL-script locale writes non-Latin digits; java.time alone
        // would write Latin ones in every language (SPEC §11.2).
        val egypt = Locale.forLanguageTag("ar-EG")
        val sampled = formatSampleDate(lateSample, egypt)
        assertTrue(sampled, sampled.contains("٢٦") && sampled.contains("٢٠٢٦"))
        assertFalse(sampled, sampled.any { it in '0'..'9' })
        val fetched = formatFetchedDate(lateSample, egypt, FRANCE_ZONE)
        assertFalse(fetched, fetched.any { it in '0'..'9' })
    }

    @Test
    fun `samples of the same day are told apart by their published time`() {
        val afternoon = Instant.parse("2026-08-26T15:10:00Z")
        val morning = Instant.parse("2026-08-26T08:30:00Z")
        assertEquals(
            listOf(LocalTime.of(15, 10), LocalTime.of(8, 30)),
            distinctSampleTimes(listOf(afternoon, morning)),
        )
        assertEquals("15:10", formatSampleTime(LocalTime.of(15, 10), Locale.FRANCE))
    }

    @Test
    fun `samples without a time of their own are numbered instead`() {
        val midnight = Instant.parse("2026-08-26T00:00:00Z")
        val morning = Instant.parse("2026-08-26T08:30:00Z")
        assertEquals(listOf(null, null), distinctSampleTimes(listOf(morning, midnight)))
        assertEquals(listOf(null, null), distinctSampleTimes(listOf(morning, morning)))
    }

    @Test
    fun `digits are not forced to non-Latin ones either`() {
        // This RTL-script locale writes Latin digits.
        val morocco = formatSampleDate(lateSample, Locale.forLanguageTag("ar-MA"))
        assertTrue(morocco, morocco.contains("26") && morocco.contains("2026"))
    }
}
