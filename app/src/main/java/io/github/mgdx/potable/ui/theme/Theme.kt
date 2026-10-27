package io.github.mgdx.potable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import io.github.mgdx.potable.data.preferences.ThemeMode

/**
 * Light theme "Blue": the whole canvas is the logo blue, text is white. The
 * canvas is dark, hence darkColorScheme(): every role left out is a dark one.
 */
internal val LightColorScheme = darkColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BluePrimaryContainer,
    onPrimaryContainer = BlueOnPrimaryContainer,
    inversePrimary = PotableBlue,
    secondary = PotableAccent,
    onSecondary = PotableOnAccent,
    secondaryContainer = PotableAccent,
    onSecondaryContainer = PotableOnAccent,
    tertiary = BlueTertiary,
    onTertiary = BlueOnTertiary,
    background = PotableBlue,
    onBackground = BlueOnSurface,
    surface = PotableBlue,
    onSurface = BlueOnSurface,
    surfaceVariant = BlueSurfaceContainerHigh,
    onSurfaceVariant = BlueOnSurfaceVariant,
    surfaceTint = BluePrimary,
    inverseSurface = BlueInverseSurface,
    inverseOnSurface = BlueInverseOnSurface,
    error = BlueError,
    onError = BlueOnError,
    errorContainer = BlueErrorContainer,
    onErrorContainer = BlueOnErrorContainer,
    outline = BlueOutline,
    outlineVariant = BlueOutlineVariant,
    scrim = BlueScrim,
    surfaceBright = BlueSurfaceBright,
    surfaceContainer = BlueSurfaceContainer,
    surfaceContainerHigh = BlueSurfaceContainerHigh,
    surfaceContainerHighest = BlueSurfaceContainerHighest,
    surfaceContainerLow = BlueSurfaceContainerLow,
    surfaceContainerLowest = BlueSurfaceContainerLowest,
    surfaceDim = BlueSurfaceContainerHigh,
)

/** Dark theme "Night": a deep navy of the logo hue, the logo blue as primary container. */
internal val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightOnPrimary,
    primaryContainer = PotableBlue,
    onPrimaryContainer = NightOnPrimaryContainer,
    inversePrimary = PotableBlue,
    secondary = PotableAccent,
    onSecondary = PotableOnAccent,
    secondaryContainer = PotableContainerDark,
    onSecondaryContainer = PotableOnContainerDark,
    background = NightSurface,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceContainerHigh,
    onSurfaceVariant = NightOnSurfaceVariant,
    surfaceTint = NightPrimary,
    inverseSurface = NightInverseSurface,
    inverseOnSurface = NightInverseOnSurface,
    error = NightError,
    onError = NightOnError,
    errorContainer = NightErrorContainer,
    onErrorContainer = NightOnErrorContainer,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    surfaceBright = NightSurfaceBright,
    surfaceContainer = NightSurfaceContainer,
    surfaceContainerHigh = NightSurfaceContainerHigh,
    surfaceContainerHighest = NightSurfaceContainerHighest,
    surfaceContainerLow = NightSurfaceContainerLow,
    surfaceContainerLowest = NightSurfaceContainerLowest,
    surfaceDim = NightSurface,
)

/**
 * Application theme. Dynamic colour is deliberately off: the brand blue is
 * part of the identity (SPEC §18.2) and must not change with the wallpaper.
 */
@Composable
fun PotableTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}

/** Whether [this] mode means dark colours now; [ThemeMode.SYSTEM] follows the device. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
