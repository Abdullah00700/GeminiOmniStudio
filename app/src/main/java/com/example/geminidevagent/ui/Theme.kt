package com.example.geminidevagent.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geminidevagent.R

val DisplayFontFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
val BodyFontFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val MonoFontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

@Immutable
data class OmniColors(
    val isDark: Boolean,
    val canvasBase: Color,
    val auroraPrimary: Color,
    val auroraSecondary: Color,
    val glassFill: Color,
    val glassElevatedFill: Color,
    val glassRimStart: Color,
    val glassRimEnd: Color,
    val accentHighlight: Color,
    val drawerFill: Color,
    val subtleDivider: Color
)

private val DarkOmniColors = OmniColors(
    isDark = true,
    canvasBase = Color(0xFF06080D),
    auroraPrimary = Color(0xFF4F46E5),
    auroraSecondary = Color(0xFF0EA5E9),
    glassFill = Color(0xFF121620),
    glassElevatedFill = Color(0xFF191F2D),
    glassRimStart = Color.White.copy(alpha = 0.22f),
    glassRimEnd = Color.White.copy(alpha = 0.05f),
    accentHighlight = Color(0xFF7DD3FC),
    drawerFill = Color(0xFF0D1119),
    subtleDivider = Color.White.copy(alpha = 0.10f)
)

private val LightOmniColors = OmniColors(
    isDark = false,
    canvasBase = Color(0xFFF3F6FB),
    auroraPrimary = Color(0xFF6366F1),
    auroraSecondary = Color(0xFF0284C7),
    glassFill = Color(0xFFFFFFFF),
    glassElevatedFill = Color(0xFFF8FAFF),
    glassRimStart = Color(0xFF4F46E5).copy(alpha = 0.18f),
    glassRimEnd = Color(0xFF0F172A).copy(alpha = 0.08f),
    accentHighlight = Color(0xFF2563EB),
    drawerFill = Color(0xFFF8FAFE),
    subtleDivider = Color(0xFF0F172A).copy(alpha = 0.09f)
)

val LocalOmniColors = staticCompositionLocalOf { DarkOmniColors }

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF272B59),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF042233),
    secondaryContainer = Color(0xFF182232),
    onSecondaryContainer = Color(0xFFD6EEFF),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF06080D),
    surface = Color(0xFF10141D),
    surfaceVariant = Color(0xFF1A202E),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    outlineVariant = Color(0xFF283044),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF3F1A1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4338CA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFF059669),
    background = Color(0xFFF3F6FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EEF8),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GeminiDevAgentTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val omniColors = if (darkTheme) DarkOmniColors else LightOmniColors

    CompositionLocalProvider(LocalOmniColors provides omniColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
