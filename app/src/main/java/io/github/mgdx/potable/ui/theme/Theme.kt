package io.github.mgdx.potable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.mgdx.potable.data.preferences.ThemeMode

internal val LightColorScheme = lightColorScheme(
    primary = PotableBlue,
    onPrimary = Color.White,
    secondaryContainer = PotableAccent,
    onSecondaryContainer = PotableOnAccent,
)

internal val DarkColorScheme = darkColorScheme(
    primary = PotableBlueDark,
    onPrimary = PotableOnBlueDark,
    secondary = PotableAccent,
    onSecondary = PotableOnAccent,
    secondaryContainer = PotableContainerDark,
    onSecondaryContainer = PotableOnContainerDark,
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
