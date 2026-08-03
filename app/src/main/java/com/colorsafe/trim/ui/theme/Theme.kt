package com.colorsafe.trim.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val ColorSafeLightScheme = lightColorScheme(
    primary = AppRose,
    onPrimary = Color.White,
    primaryContainer = AppRoseLight,
    onPrimaryContainer = AppInk,
    secondary = AppSage,
    onSecondary = Color.White,
    secondaryContainer = AppSageLight,
    onSecondaryContainer = AppInk,
    background = AppPaper,
    onBackground = AppInk,
    surface = AppPaperCard,
    onSurface = AppInk,
    surfaceVariant = AppBlush,
    onSurfaceVariant = AppInkSoft,
    outline = AppLine,
    outlineVariant = AppLine,
    error = AppDanger,
    onError = Color.White
)

@Composable
fun ColorSafeTrimTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 明るい紙の色を前提に組んだ配色なので、端末が夜間モードでも切り替えない
    MaterialTheme(
        colorScheme = ColorSafeLightScheme,
        typography = ColorSafeTypography,
        content = content
    )
}

/**
 * 紙に水彩がにじんだ背景。
 *
 * 画像ファイルは持たず、その場で描く。写真を敷くと容量が増えるうえ、
 * 端末の縦横比ごとに間延びする。淡い円形のにじみを3つ重ねるだけなら
 * どの画面サイズでも破綻しない。
 */
@Composable
fun WatercolorBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.background(AppPaper)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            // 右上にバラ色
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(AppBlushWash, Color.Transparent),
                    center = Offset(w * 0.88f, h * 0.05f),
                    radius = w * 1.05f
                )
            )
            // 左上に淡い橙。花のオレンジ側を拾う
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(AppPeachWash, Color.Transparent),
                    center = Offset(w * 0.10f, h * 0.20f),
                    radius = w * 0.85f
                )
            )
            // 左下に葉の緑。全体がピンクだけに寄らないよう受ける
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(AppSageWash, Color.Transparent),
                    center = Offset(w * 0.04f, h * 0.84f),
                    radius = w * 1.0f
                )
            )
        }
        content()
    }
}
