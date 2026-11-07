package com.osintpocket.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1AE),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    secondary = Color(0xFF9CCBFF),
    background = Color(0xFF0F1412),
    surface = Color(0xFF0F1412),
    surfaceContainer = Color(0xFF1A201D),
    surfaceContainerLow = Color(0xFF161C19),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4F),
    primaryContainer = Color(0xFF9BF2CB),
    secondary = Color(0xFF2D6197),
    background = Color(0xFFF5FAF6),
    surface = Color(0xFFF5FAF6),
)

@Composable
fun OsintTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
