package com.example.geminidevagent.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF8AB4F8),
    secondaryContainer = Color(0xFF1E1F20),
    onSecondaryContainer = Color(0xFFE3E3E3),
    tertiary = Color(0xFF81C995),
    background = Color(0xFF000000),
    surface = Color(0xFF0E0F12),
    surfaceVariant = Color(0xFF1E1F20),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF9AA0A6),
    outlineVariant = Color(0xFF2C2D30),
    error = Color(0xFFF28B82),
    errorContainer = Color(0xFF3C1E1E),
    onErrorContainer = Color(0xFFFCE8E6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF5F6368),
    secondaryContainer = Color(0xFFF1F3F4),
    onSecondaryContainer = Color(0xFF202124),
    tertiary = Color(0xFF1E8E3E),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F4F9),
    onBackground = Color(0xFF1F1F1F),
    onSurface = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFF5F6368),
    outlineVariant = Color(0xFFDADCE0)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)

@Composable
fun GeminiDevAgentTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme || isSystemInDarkTheme()) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
