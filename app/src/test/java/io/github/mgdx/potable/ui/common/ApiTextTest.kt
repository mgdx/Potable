package io.github.mgdx.potable.ui.common

import android.text.TextDirectionHeuristics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * API data shown alone take the direction of their content (SPEC §11.2);
 * translated text keeps that of the layout, even when it starts with a
 * parameter. Measured with the real text layout (native graphics).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApiTextTest {
    private val measurer = TextMeasurer(
        createFontFamilyResolver(RuntimeEnvironment.getApplication()),
        Density(1f),
        LayoutDirection.Rtl,
    )

    private fun direction(text: String, style: TextStyle) =
        measurer.measure(text, style).multiParagraph.getParagraphDirection(0)

    @Test
    fun `a French conclusion alone is a left-to-right paragraph in an RTL layout`() {
        assertEquals(ResolvedTextDirection.Ltr, direction(CONCLUSION, TextStyle().asApiText()))
    }

    @Test
    fun `without the API style, the same conclusion is laid out right to left`() {
        // The anomaly: the final full stop then shows at the start of the line.
        assertEquals(ResolvedTextDirection.Rtl, direction(CONCLUSION, TextStyle()))
    }

    @Test
    fun `RTL-script data alone stay right to left`() {
        assertEquals(ResolvedTextDirection.Rtl, direction("مياه الشرب", TextStyle().asApiText()))
    }

    @Test
    fun `a translated sentence starting with a parameter keeps the layout direction`() {
        // search_postal_codes_more in an RTL script: "%1$s +%2$d".
        val text = bidiWrap("45000", RTL_LOCALE) + " +٣"
        assertEquals(ResolvedTextDirection.Rtl, direction(text, TextStyle()))
        val unit = bidiWrap("Saint-Denis", RTL_LOCALE) + "، " + bidiWrap("Réseau Nord", RTL_LOCALE)
        assertEquals(ResolvedTextDirection.Rtl, direction(unit, TextStyle()))
    }

    @Test
    fun `the API style sets the content direction and the start alignment`() {
        val style = TextStyle(textAlign = TextAlign.Center).asApiText()
        assertEquals(TextDirection.Content, style.textDirection)
        assertEquals(TextAlign.Start, style.textAlign)
    }

    @Test
    fun `a plain-text sentence starting with API data keeps the direction of an RTL script`() {
        // The notification title, commune_and_unit in an RTL script: "%1$s، %2$s".
        // bidiWrap puts a right-to-left mark before the wrapped commune name.
        val title = bidiWrap("Saint-Denis", RTL_LOCALE) + "، " + bidiWrap("Réseau Nord", RTL_LOCALE)
        assertTrue(title.startsWith("\u200F"))
        assertTrue(viewIsRtl(title))
    }

    @Test
    fun `a conclusion in a notification is a left-to-right paragraph in an RTL script`() {
        val wrapped = apiParagraph(CONCLUSION, RTL_LOCALE)
        assertEquals("\u202A" + CONCLUSION + "\u202C", wrapped)
        assertFalse(viewIsRtl(wrapped))
        // bidiWrap would put a right-to-left mark first, hence apiParagraph.
        assertTrue(viewIsRtl(bidiWrap(CONCLUSION, RTL_LOCALE)))
        assertEquals(CONCLUSION, apiParagraph(CONCLUSION, Locale.FRENCH))
    }

    /** The paragraph direction of a TextView (first strong character, LTR by default). */
    private fun viewIsRtl(text: String) =
        TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length)

    private companion object {
        val RTL_LOCALE: Locale = Locale.forLanguageTag("ar")
        const val CONCLUSION =
            "Eau d'alimentation conforme aux exigences de qualité en vigueur pour l'ensemble des " +
                "paramètres mesurés."
    }
}
