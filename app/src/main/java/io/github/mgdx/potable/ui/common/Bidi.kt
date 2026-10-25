package io.github.mgdx.potable.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.core.text.BidiFormatter
import io.github.mgdx.potable.ui.sample.currentLocale
import java.util.Locale

/**
 * Isolates a piece of API data (commune or unit name, Hub'Eau label, value
 * with its unit) before it is inserted into a translated sentence, so that
 * its direction cannot scramble the surrounding text in RTL languages
 * (SPEC §11.2): in an RTL-script sentence, "Saint-Denis" or "<=50 mg/L" stays
 * one left-to-right run, and the punctuation that follows it stays in place.
 *
 * [locale] is the language of the interface, which sets the direction of the
 * sentence: the per-app language on Android 13+, not [Locale.getDefault].
 * In a left-to-right language, left-to-right data come back unchanged.
 */
fun bidiWrap(text: String, locale: Locale): String =
    BidiFormatter.getInstance(locale).unicodeWrap(text)

/** [bidiWrap] for the language of the current configuration. */
@Composable
fun bidiWrap(text: String): String = bidiWrap(text, currentLocale())

/**
 * What a piece of API data shown as a block of its own (a conclusion, a
 * parameter label, a commune or unit name alone on its line) adds to its
 * text style (SPEC §11.2): the paragraph takes the direction of its content,
 * not that of the interface, and is aligned on its own start. In an RTL
 * layout, a French conclusion then reads left to right with its final full
 * stop at the end, instead of being laid out as a right-to-left paragraph
 * (".paramètres mesurés").
 *
 * Compose otherwise gives every paragraph the direction of the layout
 * ([TextDirection.Unspecified] resolves to [TextDirection.Rtl] in an RTL
 * layout), which is right for translated text, including the sentences that
 * start with a parameter. Data inserted in a translated sentence keep the
 * direction of the sentence and go through [bidiWrap] instead.
 */
val ApiTextStyle = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start)

/** This style, for a piece of API data shown as a block of its own ([ApiTextStyle]). */
fun TextStyle.asApiText(): TextStyle = merge(ApiTextStyle)

/**
 * [ApiTextStyle] for plain text (a notification, not Compose): a piece of API
 * data shown as a paragraph of its own, embedded in its own direction. Android
 * views read the embedding as the first strong character, so a French
 * conclusion is a left-to-right paragraph even in an RTL script; and a view that
 * forces the direction of the language still keeps its full stop at the end.
 * Unlike [bidiWrap], no direction mark is added around it, since one would
 * set the paragraph direction. In a left-to-right language, left-to-right
 * data come back unchanged.
 */
fun apiParagraph(text: String, locale: Locale): String =
    BidiFormatter.getInstance(locale).unicodeWrap(text, false)
