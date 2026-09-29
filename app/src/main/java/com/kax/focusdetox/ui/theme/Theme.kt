package com.kax.focusdetox.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Typography ───────────────────────────────────────────────────────────────
// Add to build.gradle:  implementation("androidx.compose.ui:ui-text-google-fonts")
// Then register Inter + Space Grotesk via GoogleFont provider.
// For now, default typography is used — swap these for GoogleFont instances.

private val White = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary          = Blue600,
    onPrimary        = Blue50,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue800,

    secondary        = Blue400,
    onSecondary      = Blue50,
    secondaryContainer = Blue50,
    onSecondaryContainer = Blue800,

    background       = Slate50,
    onBackground     = Slate900,

    surface          = White,
    onSurface        = Slate900,
    surfaceVariant   = Blue50,
    onSurfaceVariant = Slate700,

    outline          = Slate200,
    outlineVariant   = Blue100,

    error            = Red,
    onError          = RedLight,
)

private val DarkColors = darkColorScheme(
    primary          = Blue200,
    onPrimary        = Navy950,
    primaryContainer = Blue800,
    onPrimaryContainer = Blue100,

    secondary        = Blue200,
    onSecondary      = Navy900,
    secondaryContainer = Navy800,
    onSecondaryContainer = Blue100,

    background       = Navy950,
    onBackground     = Blue50,

    surface          = Navy900,
    onSurface        = Blue50,
    surfaceVariant   = Navy800,
    onSurfaceVariant = Slate400,

    outline          = Navy700,
    outlineVariant   = Blue800,

    error            = Red,
    onError          = RedLight,
)



@Composable
fun FocusDetoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    // Color the status bar to match background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}