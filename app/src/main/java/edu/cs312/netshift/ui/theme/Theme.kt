package edu.cs312.netshift.ui.theme

import android.animation.ValueAnimator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import edu.cs312.netshift.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalNetShiftDark = staticCompositionLocalOf { true }
val LocalUiMotion = staticCompositionLocalOf { true }
val LocalShiftyTexture = staticCompositionLocalOf<ImageBitmap?> { null }
val WifiMint = Color(0xFF66E5C4)
val MobileGold = Color(0xFFFFD36E)
val Violet = Color(0xFFB9A6FF)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6645B5), onPrimary = Color.White,
    secondary = Color(0xFF006C58), onSecondary = Color.White,
    tertiary = Color(0xFF825500), background = Color(0xFFF8F6FD),
    onBackground = Color(0xFF222032), surface = Color(0xFFF8F6FD),
    onSurface = Color(0xFF222032), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDE8F5), onSurfaceVariant = Color(0xFF625C70),
    outlineVariant = Color(0xFFE0DAEB), outline = Color(0xFF7C738C),
)
private val DarkColors = darkColorScheme(
    primary = Violet, onPrimary = Color(0xFF28184B),
    secondary = WifiMint, onSecondary = Color(0xFF00382C),
    tertiary = MobileGold, background = Color(0xFF101016),
    onBackground = Color(0xFFF3EFF9), surface = Color(0xFF101016),
    onSurface = Color(0xFFF3EFF9), surfaceContainerLow = Color(0xFF1C1B25),
    surfaceVariant = Color(0xFF282532), onSurfaceVariant = Color(0xFFBDB5CD),
    outlineVariant = Color(0xFF36313F), outline = Color(0xFF8C829E),
)
private val Type = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)
@Composable
fun NetShiftTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val texture = ImageBitmap.imageResource(R.drawable.shifty_atlas)
    CompositionLocalProvider(LocalNetShiftDark provides darkTheme, LocalUiMotion provides ValueAnimator.areAnimatorsEnabled(), LocalShiftyTexture provides texture) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Type,
            shapes = Shapes(
                small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(22.dp),
                large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)),
            content = content,
        )
    }
}
