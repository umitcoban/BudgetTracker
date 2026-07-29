package com.umit.budgettracker.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ForestDark,
    onPrimary = Color(0xFF00382E),
    primaryContainer = MintDark,
    onPrimaryContainer = Color(0xFFB8F1E0),
    secondary = Color(0xFFB8CCC5),
    onSecondary = Color(0xFF24332F),
    secondaryContainer = SoftGrayDark,
    onSecondaryContainer = Color(0xFFD6E6E0),
    tertiary = WarningDark,
    tertiaryContainer = WarmSurfaceDark,
    onTertiaryContainer = Color(0xFFFFDFA7),
    error = NegativeDark,
    errorContainer = Color(0xFF5B2027),
    onErrorContainer = Color(0xFFFFDADD),
    background = CanvasDark,
    onBackground = Color(0xFFE2E8E4),
    surface = PaperDark,
    onSurface = Color(0xFFE2E8E4),
    surfaceVariant = SoftGrayDark,
    onSurfaceVariant = Color(0xFFB9C3BE),
    outline = OutlineDark,
    outlineVariant = Color(0xFF2C3431)
)

private val LightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Color(0xFF083C32),
    secondary = Color(0xFF516660),
    onSecondary = Color.White,
    secondaryContainer = SoftGray,
    onSecondaryContainer = Ink,
    tertiary = Warning,
    tertiaryContainer = WarmSurface,
    onTertiaryContainer = Color(0xFF4B3100),
    error = Negative,
    errorContainer = Color(0xFFFFDADC),
    onErrorContainer = Color(0xFF5B111B),
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SoftGray,
    onSurfaceVariant = Color(0xFF5E6965),
    outline = OutlineLight,
    outlineVariant = Color(0xFFE6EAE7)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun BudgetTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // A stable palette gives the product a recognizable identity. Dynamic color can
    // still be exposed later as a user preference without changing screen code.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
