package io.github.mgdx.potable.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** BidiFormatter reads the direction of the locale through the framework. */
@RunWith(RobolectricTestRunner::class)
class BidiTest {
    @Test
    fun `French data in an English sentence come back unchanged`() {
        assertEquals("Saint-Denis", bidiWrap("Saint-Denis", Locale.ENGLISH))
        assertEquals("<=50 mg/L", bidiWrap("<=50 mg/L", Locale.ENGLISH))
    }

    @Test
    fun `French data in an RTL-script sentence are isolated as a left-to-right run`() {
        val wrapped = bidiWrap("Saint-Denis", Locale.forLanguageTag("ar"))
        assertTrue(wrapped, wrapped.contains(LRE + "Saint-Denis" + PDF))
    }

    @Test
    fun `a value with its unit stays one left-to-right run in an RTL script`() {
        val wrapped = bidiWrap("12,5 mg/L", Locale.forLanguageTag("ar"))
        assertTrue(wrapped, wrapped.contains(LRE + "12,5 mg/L" + PDF))
    }

    private companion object {
        const val LRE = "\u202A"
        const val PDF = "\u202C"
    }
}
