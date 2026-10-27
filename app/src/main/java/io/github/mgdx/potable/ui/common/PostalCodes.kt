package io.github.mgdx.potable.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import io.github.mgdx.potable.R
import io.github.mgdx.potable.ui.sample.currentLocale
import java.util.Locale

/**
 * The postal codes of a commune as one short line, as in the search results
 * and "My communes": the first one, followed by "+n" when the commune has
 * more; null when it has none, so that no empty line is shown.
 */
fun postalCodesText(postalCodes: List<String>, resources: Resources, locale: Locale): String? =
    when (postalCodes.size) {
        0 -> null
        1 -> postalCodes.first()
        else -> resources.getString(
            R.string.postal_codes_more,
            bidiWrap(postalCodes.first(), locale),
            postalCodes.size - 1,
        )
    }

/** [postalCodesText] in the language of the current configuration. */
@Composable
fun postalCodesText(postalCodes: List<String>): String? =
    postalCodesText(postalCodes, LocalResources.current, currentLocale())
