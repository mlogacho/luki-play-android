// ui/theme/Theme.kt
package com.luki.play.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LukiDarkColors = darkColorScheme(
    primary       = Color(0xFFFF5E5B),     // rojo Luki
    onPrimary     = Color(0xFFFFFFFF),
    secondary     = Color(0xFFFFB400),
    background    = Color(0xFF240046),     // violeta marca
    onBackground  = Color(0xFFEDEDED),
    surface       = Color(0xFF301A4B),
    onSurface     = Color(0xFFEDEDED),
)

private val LukiLightColors = lightColorScheme(
    primary       = Color(0xFFFF5E5B),
    onPrimary     = Color(0xFFFFFFFF),
    background    = Color(0xFFFFFFFF),
    onBackground  = Color(0xFF1B1B1F),
    surface       = Color(0xFFF6F6F6),
    onSurface     = Color(0xFF1B1B1F),
)

@Composable
fun LukiTheme(
    darkTheme: Boolean = true,   // marca Luki es oscura por default
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LukiDarkColors else LukiLightColors,
        content = content,
    )
}
