package com.colorsafe.trim.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorSafeLightScheme = lightColorScheme(
    primary = AppAccentBlue,
    onPrimary = AppWhite,
    background = AppWhite,
    onBackground = AppInkBlack,
    surface = AppWhite,
    onSurface = AppInkBlack,
    surfaceVariant = AppWhite,
    onSurfaceVariant = AppInkGray,
    outline = AppDividerGray,
    error = AppDangerRed
)

@Composable
fun ColorSafeTrimTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 要件: 常に白ベースのミニマルデザインを維持する
    MaterialTheme(
        colorScheme = ColorSafeLightScheme,
        typography = ColorSafeTypography,
        content = content
    )
}
