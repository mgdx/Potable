package io.github.mgdx.potable.ui.common

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableWidthTest {
    @Test
    fun `a phone in portrait gets no inset`() {
        assertEquals(0.dp, readableInset(360.dp))
        assertEquals(0.dp, readableInset(480.dp))
        assertEquals(0.dp, readableInset(ReadableMaxWidth))
    }

    @Test
    fun `a wider window centres the reading column`() {
        assertEquals(80.dp, readableInset(800.dp))
        assertEquals(192.dp, readableInset(1024.dp))
        assertEquals(0.dp, readableInset(1024.dp, maxWidth = TwoPaneMaxWidth))
    }

    @Test
    fun `two columns from the expanded width on`() {
        assertFalse(isExpandedWidth(411.dp))
        assertFalse(isExpandedWidth(839.dp))
        assertTrue(isExpandedWidth(840.dp))
        assertTrue(isExpandedWidth(1280.dp))
    }

    @Test
    fun `a two-column layout holds two reading columns`() {
        assertEquals(ReadableMaxWidth * 2 + PaneSpacing, TwoPaneMaxWidth)
        assertTrue(TwoPaneMaxWidth > ExpandedMinWidth)
    }
}
