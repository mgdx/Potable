package io.github.mgdx.potable.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/** The postal codes line shared by the search results and "My communes". */
@RunWith(RobolectricTestRunner::class)
class PostalCodesTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    private fun text(vararg codes: String) =
        postalCodesText(codes.toList(), resources, Locale.ENGLISH)

    @Test
    fun `no postal code gives no line`() {
        assertNull(text())
    }

    @Test
    fun `a single postal code is shown alone`() {
        assertEquals("45000", text("45000"))
    }

    @Test
    fun `more postal codes give the first one and how many more`() {
        assertEquals("45000 +1", text("45000", "45100"))
        assertEquals("75001 +19", text(*Array(20) { "750" + "${it + 1}".padStart(2, '0') }))
    }
}
