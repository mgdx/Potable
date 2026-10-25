package io.github.mgdx.potable.ui.sample

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.core.os.ConfigurationCompat
import io.github.mgdx.potable.R
import io.github.mgdx.potable.core.model.AnalysisResult
import io.github.mgdx.potable.ui.common.bidiWrap
import java.util.Locale

/** The first locale of the current configuration. */
@Composable
internal fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault()
    }
}

/**
 * The value of a result and its unit ([valueWithUnit]), rendered as one
 * isolated left-to-right block in every language, so that "12,5 mg/L" or
 * "<0,01 mg/L" is never reordered in a right-to-left layout (SPEC §11.2).
 * [selectable] lets the user copy it; leave it off inside a clickable row,
 * whose taps the selection would otherwise compete for.
 */
@Composable
fun ResultValue(
    result: AnalysisResult,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    selectable: Boolean = true,
) {
    val locale = currentLocale()
    val value = remember(result, locale) { valueWithUnit(result, locale) }
    if (value == null) {
        Text(
            text = stringResource(R.string.sample_no_value),
            style = style,
            fontStyle = FontStyle.Italic,
            modifier = modifier,
        )
    } else if (selectable) {
        SelectionContainer(modifier = modifier) {
            Text(text = bidiWrap(value), style = style.copy(textDirection = TextDirection.Ltr))
        }
    } else {
        Text(
            text = bidiWrap(value),
            style = style.copy(textDirection = TextDirection.Ltr),
            modifier = modifier,
        )
    }
}
