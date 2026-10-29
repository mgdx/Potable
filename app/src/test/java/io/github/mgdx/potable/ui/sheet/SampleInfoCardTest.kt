package io.github.mgdx.potable.ui.sheet

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SampleInfoCardTest {
    @Test
    fun `a left-to-right value is aligned on its end in a left-to-right layout`() {
        assertEquals(TextAlign.End, ltrValueAlign(LayoutDirection.Ltr))
    }

    @Test
    fun `a left-to-right value is aligned on its start, the layout's end, in an RTL layout`() {
        assertEquals(TextAlign.Start, ltrValueAlign(LayoutDirection.Rtl))
    }
}
