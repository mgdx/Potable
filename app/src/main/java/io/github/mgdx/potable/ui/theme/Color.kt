package io.github.mgdx.potable.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colours (SPEC §18.2): the logo blue #2350B5, the white of its tap and
// drop, and the light accent #9CC8FF. The light theme ("Blue") paints the
// whole canvas in the logo blue; the dark theme ("Night") is a deep navy of
// the same hue. Both canvases are dark, so both schemes are built from
// darkColorScheme() and both keep light system-bar icons. Contrast ratios are
// WCAG 2.x ratios, checked by ThemeContrastTest.

/** Logo blue: the light canvas, and the dark primary container. */
val PotableBlue = Color(0xFF2350B5)

/** Light accent, the secondary of both themes (chart series, progress). */
val PotableAccent = Color(0xFF9CC8FF)

/** Text and icons on [PotableAccent]. */
val PotableOnAccent = Color(0xFF001D35)

/**
 * Dark-theme secondary container (freshness banner, selected chips): a deep
 * brand blue instead of Material's default lavender.
 */
val PotableContainerDark = Color(0xFF1D3F8A)

/** Text and icons on [PotableContainerDark]. */
val PotableOnContainerDark = Color(0xFFDCE3FF)

// Light theme "Blue": surfaces are tones of the logo blue.
internal val BlueOnSurface = Color(0xFFFFFFFF)
internal val BlueOnSurfaceVariant = Color(0xFFDCE5FF)
internal val BlueSurfaceContainerLowest = Color(0xFF2A58BE)
internal val BlueSurfaceContainerLow = Color(0xFF1F4AAA)
internal val BlueSurfaceContainer = Color(0xFF1D46A3)
internal val BlueSurfaceContainerHigh = Color(0xFF1A419A)
internal val BlueSurfaceContainerHighest = Color(0xFF173C91)
internal val BlueSurfaceBright = Color(0xFF2E5CC2)
internal val BluePrimary = Color(0xFFC7DDFF)
internal val BlueOnPrimary = Color(0xFF0E2F7A)
internal val BluePrimaryContainer = Color(0xFFFFFFFF)
internal val BlueOnPrimaryContainer = Color(0xFF1A3F94)
internal val BlueTertiary = Color(0xFFDCE5FF)
internal val BlueOnTertiary = Color(0xFF0E2F7A)
internal val BlueError = Color(0xFFFFCFC9)
internal val BlueOnError = Color(0xFF690005)
internal val BlueErrorContainer = Color(0xFF8C1D18)
internal val BlueOnErrorContainer = Color(0xFFFFDAD6)
internal val BlueOutline = Color(0xFFA9BDEB)
internal val BlueOutlineVariant = Color(0xFF5379D2)
internal val BlueInverseSurface = Color(0xFFF4F6FF)
internal val BlueInverseOnSurface = Color(0xFF1A2A4F)
internal val BlueScrim = Color(0xFF000000)

// Dark theme "Night": a deep navy of the logo hue.
internal val NightSurface = Color(0xFF0B1838)
internal val NightOnSurface = Color(0xFFE3E8F7)
internal val NightOnSurfaceVariant = Color(0xFFBFC9E6)
internal val NightSurfaceContainerLowest = Color(0xFF07112A)
internal val NightSurfaceContainerLow = Color(0xFF0F1F45)
internal val NightSurfaceContainer = Color(0xFF13244C)
internal val NightSurfaceContainerHigh = Color(0xFF1A2D58)
internal val NightSurfaceContainerHighest = Color(0xFF213665)
internal val NightSurfaceBright = Color(0xFF24396A)
internal val NightPrimary = Color(0xFFB3C5FF)
internal val NightOnPrimary = Color(0xFF002A78)
internal val NightOnPrimaryContainer = Color(0xFFFFFFFF)
internal val NightError = Color(0xFFFFB4AB)
internal val NightOnError = Color(0xFF690005)
internal val NightErrorContainer = Color(0xFF93000A)
internal val NightOnErrorContainer = Color(0xFFFFDAD6)
internal val NightOutline = Color(0xFF8592B3)
internal val NightOutlineVariant = Color(0xFF34446B)
internal val NightInverseSurface = Color(0xFFE3E8F7)
internal val NightInverseOnSurface = Color(0xFF1A2A4F)
