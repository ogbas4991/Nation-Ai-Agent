package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OpaDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color(0xFF001F24),
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = PurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = PurpleContainer,
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = CyanPrimaryDark,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = DarkSurfaceElevated,
    surfaceContainerHigh = DarkSurfaceHighlight,
    outline = DarkSurfaceBorder,
    outlineVariant = Color(0xFF2E3344),
    error = StatusDisconnected,
    onError = Color.White
)

@Composable
fun OpaTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = OpaDarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Retain alias for any preview compatibility
@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) = OpaTheme(content = content)

