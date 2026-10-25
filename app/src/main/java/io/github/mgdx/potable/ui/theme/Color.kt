package io.github.mgdx.potable.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colours (SPEC §18.2). Contrast ratios are WCAG 2.x relative-luminance
// ratios, computed from the sRGB values; AA asks 4.5:1 for body text.

/** Brand blue, light-theme primary. White on it: 7.28:1. */
val PotableBlue = Color(0xFF2350B5)

/**
 * Dark-theme primary, a lightened brand blue (tone 80 of the same hue).
 * [PotableOnBlueDark] on it: 7.71:1; it on a dark surface (#121318): 10.91:1.
 */
val PotableBlueDark = Color(0xFFB3C5FF)

/** Text and icons on [PotableBlueDark]. */
val PotableOnBlueDark = Color(0xFF002A78)

/** Light accent for states and charts. [PotableOnAccent] on it: 9.88:1. */
val PotableAccent = Color(0xFF9CC8FF)

/** Text and icons on [PotableAccent]. */
val PotableOnAccent = Color(0xFF001D35)

/**
 * Dark-theme secondary container (freshness banner, selected chips): a deep
 * brand blue, between [PotableBlue] and the dark surface, instead of
 * Material's default lavender. [PotableOnContainerDark] on it: 7.73:1.
 */
val PotableContainerDark = Color(0xFF1D3F8A)

/** Text and icons on [PotableContainerDark], a pale tint of [PotableBlueDark]. */
val PotableOnContainerDark = Color(0xFFDCE3FF)
