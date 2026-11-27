package edu.cs312.netshift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    secondary = Color(0xFF0F766E),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFF8FAFC),
    surfaceContainerLow = Color(0xFFF0F4FA),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF93B4FF),
    secondary = Color(0xFF5EEAD4),
    background = Color(0xFF101827),
    surface = Color(0xFF101827),
    surfaceContainerLow = Color(0xFF182234),
)

@Composable
fun NetShiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
