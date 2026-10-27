package io.github.mgdx.potable.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * WCAG 2.x contrast of the colour pairs the screens use, in the light and
 * the dark theme (SPEC §9): 4.5:1 for text, 3:1 for the marks of the chart
 * and the focus border (non-text contrast).
 */
class ThemeContrastTest {
    private fun ratio(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** Text pairs: what is written on what, by the screens. */
    private fun textPairs(c: ColorScheme) = mapOf(
        "onSurface / surface" to (c.onSurface to c.surface),
        "onSurfaceVariant / surface (descriptions)" to (c.onSurfaceVariant to c.surface),
        "primary / surface (section titles, Primary)" to (c.primary to c.surface),
        "error / surface (non-compliance)" to (c.error to c.surface),
        "onPrimary / primary (buttons)" to (c.onPrimary to c.primary),
        "onSecondaryContainer / secondaryContainer (freshness banner, chips)" to
            (c.onSecondaryContainer to c.secondaryContainer),
        "onErrorContainer / errorContainer (refresh failed)" to
            (c.onErrorContainer to c.errorContainer),
        "onSurfaceVariant / surfaceContainer (source notice)" to
            (c.onSurfaceVariant to c.surfaceContainer),
        "onSurfaceVariant / surfaceContainerLow (key parameters)" to
            (c.onSurfaceVariant to c.surfaceContainerLow),
        "onSurfaceVariant / surfaceContainerHigh (conclusion card)" to
            (c.onSurfaceVariant to c.surfaceContainerHigh),
        "inverseOnSurface / inverseSurface (chart tooltip)" to
            (c.inverseOnSurface to c.inverseSurface),
        "onBackground / background" to (c.onBackground to c.background),
        "onSurface / surfaceContainerHighest" to (c.onSurface to c.surfaceContainerHighest),
        "primary / surfaceContainerLowest" to (c.primary to c.surfaceContainerLowest),
        "error / surfaceContainerHigh" to (c.error to c.surfaceContainerHigh),
        "onSurfaceVariant / surfaceContainerLowest" to
            (c.onSurfaceVariant to c.surfaceContainerLowest),
        "onPrimaryContainer / primaryContainer" to
            (c.onPrimaryContainer to c.primaryContainer),
    )

    /** Marks of the chart and focus border, on the surface. */
    private fun markPairs(c: ColorScheme) = mapOf(
        "primary / surface (focus border)" to (c.primary to c.surface),
        "secondary / surface (series)" to (c.secondary to c.surface),
        "secondary / surfaceContainerLow (series on a card)" to
            (c.secondary to c.surfaceContainerLow),
        "outline / surfaceContainerLow (axis on a card)" to
            (c.outline to c.surfaceContainerLow),
        "onSurface / surface (quality limit)" to (c.onSurface to c.surface),
        "onSurfaceVariant / surface (quality reference)" to (c.onSurfaceVariant to c.surface),
        "outline / surface (time axis)" to (c.outline to c.surface),
    )

    private fun check(name: String, scheme: ColorScheme) {
        val failures = mutableListOf<String>()
        textPairs(scheme).forEach { (pair, colors) ->
            val r = ratio(colors.first, colors.second)
            println("$name text  %-70s %.2f:1".format(pair, r))
            if (r < TEXT_MIN) failures += "$pair: %.2f".format(r)
        }
        markPairs(scheme).forEach { (pair, colors) ->
            val r = ratio(colors.first, colors.second)
            println("$name mark  %-70s %.2f:1".format(pair, r))
            if (r < NON_TEXT_MIN) failures += "$pair: %.2f".format(r)
        }
        assertTrue("$name below AA: $failures", failures.isEmpty())
    }

    @Test
    fun `the light theme meets AA`() = check("light", LightColorScheme)

    @Test
    fun `the dark theme meets AA`() = check("dark", DarkColorScheme)

    @Test
    fun `the brand accent carries readable text`() {
        // SPEC §18.2: #9CC8FF, the light secondary container and the dark secondary.
        val r = ratio(PotableOnAccent, PotableAccent)
        println("accent PotableOnAccent / PotableAccent %.2f:1".format(r))
        assertTrue(r >= TEXT_MIN)
    }

    @Test
    fun `the secondary containers are the brand's, not Material's lavender`() {
        assertEquals(PotableAccent, LightColorScheme.secondaryContainer)
        assertEquals(PotableContainerDark, DarkColorScheme.secondaryContainer)
        assertEquals(PotableOnContainerDark, DarkColorScheme.onSecondaryContainer)
    }

    @Test
    fun `the light canvas is the logo blue`() {
        // The logo background, #2350B5, is the light background and surface.
        assertEquals(PotableBlue, LightColorScheme.background)
        assertEquals(PotableBlue, LightColorScheme.surface)
        assertEquals(Color(0xFF2350B5), PotableBlue)
    }

    private companion object {
        const val TEXT_MIN = 4.5
        const val NON_TEXT_MIN = 3.0
    }
}
