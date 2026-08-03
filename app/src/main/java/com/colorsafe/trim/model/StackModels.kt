package com.colorsafe.trim.model

/**
 * 縦3分割(vstack相当)の割り付け。
 * 上から順の高さ比率を持つ。合計は 1.0。
 */
enum class StackLayout(val label: String, val ratios: List<Float>) {
    EVEN("均等", listOf(1f / 3f, 1f / 3f, 1f / 3f)),
    CENTER_LARGE("中央を大きく", listOf(560f / 1920f, 800f / 1920f, 560f / 1920f))
}

/**
 * 1パネル分の調整値。
 *
 * @param offsetY -1.0(いちばん上を見せる) 〜 0.0(中央) 〜 1.0(いちばん下を見せる)
 * @param zoom 1.0(帯を埋める最小の拡大) 〜 2.5。大きいほど寄る。
 */
data class PanelAdjust(
    val offsetY: Float = 0f,
    val zoom: Float = 1f,
    /** 再生速度。1.0が等速、0.5でゆっくり、2.0で倍速 */
    val speed: Float = 1f
)

/**
 * 色味を持ち上げるフィルターの強さ。
 *
 * 参考にしたリールの見え方（緑が濃く、締まりのある絵）に寄せた1種類だけ。
 * 数を増やすとCapCutの劣化版になるので、増やさない。
 */
object ColorBoost {
    /** 彩度の足し幅。-100〜100 */
    const val SATURATION = 26f

    /** コントラストの足し幅。-1〜1 */
    const val CONTRAST = 0.10f

    /** 明るさの足し幅。-100〜100 */
    const val LIGHTNESS = 2f
}

/**
 * 帯の位置と、元動画のどこを切り出すかの計算。
 *
 * 書き出し(Media3 Transformer)と画面プレビュー(Bitmap)の両方がここを使う。
 * 片方だけ直すとプレビューと結果がズレるため、計算は必ずこの1か所に置く。
 */
object StackGeometry {

    /** 出力フレーム内での1本の帯。単位はピクセル。 */
    data class Band(val top: Int, val height: Int)

    /**
     * 元動画のどの範囲を使うかを、0.0〜1.0の割合で表したもの。
     * cx/cy は中心位置、w/h は幅・高さの割合。
     */
    data class SourceRect(val cx: Float, val cy: Float, val w: Float, val h: Float)

    /**
     * 出力の高さを3本の帯に割る。
     * エンコーダは幅・高さが偶数であることを要求するため、各帯を2の倍数に丸める。
     */
    fun bands(outHeight: Int, layout: StackLayout): List<Band> {
        val h0 = (outHeight * layout.ratios[0]).toInt() / 2 * 2
        val h1 = (outHeight * layout.ratios[1]).toInt() / 2 * 2
        val h2 = outHeight - h0 - h1
        return listOf(
            Band(top = 0, height = h0),
            Band(top = h0, height = h1),
            Band(top = h0 + h1, height = h2)
        )
    }

    /**
     * 帯を余白なしで埋める切り出し範囲を求める(いわゆるcover)。
     * zoom で更に寄せ、offsetY で上下位置をずらす。
     */
    fun sourceRect(
        srcWidth: Int,
        srcHeight: Int,
        bandWidth: Int,
        bandHeight: Int,
        adjust: PanelAdjust
    ): SourceRect {
        if (srcWidth <= 0 || srcHeight <= 0 || bandWidth <= 0 || bandHeight <= 0) {
            return SourceRect(0.5f, 0.5f, 1f, 1f)
        }

        val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()
        val bandAspect = bandWidth.toFloat() / bandHeight.toFloat()

        // 帯より横長の素材は高さいっぱいを使い、横を削る。縦長ならその逆。
        var w: Float
        var h: Float
        if (srcAspect > bandAspect) {
            h = 1f
            w = bandAspect / srcAspect
        } else {
            w = 1f
            h = srcAspect / bandAspect
        }

        val zoom = adjust.zoom.coerceIn(1f, 4f)
        w = (w / zoom).coerceIn(0.02f, 1f)
        h = (h / zoom).coerceIn(0.02f, 1f)

        // 切り出し範囲が元動画からはみ出さない範囲でだけ動かせる
        val slack = (1f - h) / 2f
        val cy = 0.5f + adjust.offsetY.coerceIn(-1f, 1f) * slack

        return SourceRect(cx = 0.5f, cy = cy, w = w, h = h)
    }
}
