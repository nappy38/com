package com.colorsafe.trim.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
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
 * 背景。紙の色の上に、さやさん自作の水彩画を下から立ち上げる。
 *
 * 画面いっぱいに引き伸ばさず、幅に合わせて下端に置く。縦長のスマホでは
 * 上側に紙の色が残り、そこに文字が乗るので読みやすさを損なわない。
 * 引き伸ばすと絵が間延びするうえ、文字が枝と重なって読みづらくなる。
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
            contentScale = ContentScale.FillWidth,
            // 文字の下に敷くので、原画より少し引く
            alpha = 0.8f,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
        content()
    }
}
