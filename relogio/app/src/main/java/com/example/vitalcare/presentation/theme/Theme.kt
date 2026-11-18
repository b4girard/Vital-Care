package com.example.vitalcare.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val VitalCareColors = ColorScheme(


    primary = Color(0xFF00BFA5),
    onPrimary = Color.White,


    primaryContainer = Color(0xFF136F68),
    onPrimaryContainer = Color.White,


    secondary = Color(0xFF00BFA5),
    onSecondary = Color.White,

    secondaryContainer = Color(0xFF136F68),
    onSecondaryContainer = Color.White,


    surfaceContainer = Color(0xFF130C2E),
    onSurface = Color.White,


    background = Color(0xFF19253D),
    onBackground = Color.White,

    error = Color(0xFFE53935),
    onError = Color.White,

    outline = Color(0xFF136F68),
    outlineVariant = Color(0xFF136F68),

    tertiary = Color(0xFF00BFA5),
    onTertiary = Color.White,

    tertiaryContainer = Color(0xFF136F68),
    onTertiaryContainer = Color.White
)

@Composable
fun VitalCareTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VitalCareColors,
        content = content
    )
}