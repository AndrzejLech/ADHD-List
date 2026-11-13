package com.example.adhdlist.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Yellow,
    onPrimary = OnYellow,
    primaryContainer = Yellow,
    onPrimaryContainer = OnYellow,
    secondary = MutedInkLight,
    tertiary = DeepGold, // progress bars: visible on light grey, unlike yellow
    background = GreyBackgroundLight,
    onBackground = InkLight,
    surface = GreyBackgroundLight,
    onSurface = InkLight,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = MutedInkLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = GreyRowLight,
    surfaceContainer = GreyBarLight,
    surfaceContainerHigh = Color(0xFFE6E6E6),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = RedError,
    errorContainer = PinkDelete,
    onErrorContainer = OnPinkDelete,
)

private val DarkColorScheme = darkColorScheme(
    primary = Yellow,
    onPrimary = OnYellow,
    primaryContainer = Yellow,
    onPrimaryContainer = OnYellow,
    secondary = MutedInkDark,
    tertiary = Yellow,
    background = GreyBackgroundDark,
    onBackground = InkDark,
    surface = GreyBackgroundDark,
    onSurface = InkDark,
    surfaceVariant = GreyFieldDark,
    onSurfaceVariant = MutedInkDark,
    surfaceContainerLowest = Color(0xFF141317),
    surfaceContainerLow = GreyRowDark,
    surfaceContainer = GreyBarDark,
    surfaceContainerHigh = GreyFieldDark,
    outline = Color(0xFF938F99),
    outlineVariant = GreyFieldDark,
    error = PinkDelete,
    errorContainer = PinkDelete,
    onErrorContainer = OnPinkDelete,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** The yellow bar from the first version of the app, used by every screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun yellowTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)

// No dynamic (wallpaper) colour on purpose: the app should look the same every time it opens.
@Composable
fun ADHDListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
