package com.colorsafe.trim.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 水彩の花束。画像を持たずにその場で描く。
 *
 * 花びら1枚を「輪郭のない円」として置き、少しずつずらして重ねる。
 * 水彩のにじみは輪郭が消えていく所にあるので、円は必ず中心から
 * 透明へ落とす。輪郭線は引かない。引いた瞬間にイラストになる。
 */
private data class Petal(
    val dx: Float,
    val dy: Float,
    val radius: Float,
    val color: Color
)

// 上に花、下へ向かって色が落ちていく並び。手で置いた配置なので規則性はない
private val PETALS = listOf(
    // 中心の濃い花
    Petal(0.00f, -0.02f, 0.38f, Color(0xA6C2607B)),
    Petal(0.05f, 0.03f, 0.25f, Color(0x99A84A66)),
    Petal(-0.07f, 0.01f, 0.22f, Color(0x85D4788B)),

    // 右上に開く花びら
    Petal(0.22f, -0.20f, 0.32f, Color(0x99E9A79E)),
    Petal(0.34f, -0.08f, 0.24f, Color(0x85EFC2A4)),
    Petal(0.18f, 0.14f, 0.26f, Color(0x6BD98A96)),

    // 左上
    Petal(-0.24f, -0.18f, 0.30f, Color(0x8CEFC2A4)),
    Petal(-0.34f, -0.04f, 0.23f, Color(0x73E9A79E)),
    Petal(-0.16f, -0.30f, 0.20f, Color(0x6BD4788B)),

    // 上に伸びる小花
    Petal(0.02f, -0.34f, 0.19f, Color(0x85EBB9BC)),
    Petal(0.13f, -0.38f, 0.13f, Color(0x6BC2607B)),
    Petal(-0.10f, -0.40f, 0.12f, Color(0x5EEFC2A4)),

    // 葉。ピンクだけに寄らないよう緑で受ける
    Petal(-0.30f, 0.16f, 0.28f, Color(0x7A7C9A94)),
    Petal(0.30f, 0.18f, 0.23f, Color(0x667C9A94)),
    Petal(-0.06f, -0.24f, 0.18f, Color(0x665D8189)),

    // 下へ流れる裾。輪郭を作らずぼかして消す
    Petal(-0.02f, 0.30f, 0.30f, Color(0x54D98A96)),
    Petal(0.06f, 0.46f, 0.25f, Color(0x3DC2607B)),
    Petal(-0.04f, 0.60f, 0.19f, Color(0x2BE9A79E))
)

@Composable
fun WatercolorBloom(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f

        PETALS.forEach { petal ->
            val center = Offset(cx + petal.dx * unit, cy + petal.dy * unit)
            val radius = petal.radius * unit
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(petal.color, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
    }
}
