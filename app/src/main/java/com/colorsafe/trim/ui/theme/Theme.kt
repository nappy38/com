package com.colorsafe.trim.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.colorsafe.trim.R

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
 * 背景。紙の色の上に、さやさん自作の水彩画を画面いっぱいに敷く。
 *
 * 縦横比は保ったまま画面を覆う(Crop)。縦長のスマホでは絵の左右が
 * はみ出すので、枝のある左端を残す向きで寄せる。引き伸ばして
 * 縦横比を崩すことはしない。水彩は形が歪むとすぐ嘘っぽくなる。
 */
@Composable
fun WatercolorBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.background(AppPaper)) {
        Image(
            painter = painterResource(R.drawable.bg_watercolor),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomStart,
            // 文字の下に敷くので、原画より少し引く
            alpha = 0.8f,
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}
