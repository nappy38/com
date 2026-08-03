package com.colorsafe.trim.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import com.colorsafe.trim.model.ColorBoost
import com.colorsafe.trim.model.PanelAdjust
import com.colorsafe.trim.model.StackGeometry
import com.colorsafe.trim.model.StackLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 書き出す前の見た目を1枚の画像として組み立てる。
 *
 * 位置合わせは書き出しと同じ [StackGeometry] を使う。ここを独自計算にすると
 * 「プレビューでは合っていたのに書き出したらズレる」が起きるため、必ず共有する。
 *
 * 元動画からのフレーム取り出しは遅いので、パスと時刻をキーにして持ち回す。
 * スライダーを動かしている間は取り出し済みのフレームを再合成するだけになる。
 */
class StackPreviewRenderer {

    private val frameCache = HashMap<String, Bitmap>()

    private val dividerPaint = Paint().apply {
        color = Color.argb(90, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val emptyPaint = Paint().apply {
        color = Color.argb(255, 26, 32, 44)
        style = Paint.Style.FILL
    }
    private val bitmapPaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }

    /**
     * 書き出し側の HslAdjustment + Contrast と同じ変化を、画面の絵にも当てる。
     * 書き出してから色が違うと気づくのでは、プレビューの意味がない。
     */
    private val boostPaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        val matrix = ColorMatrix().apply {
            setSaturation(1f + ColorBoost.SATURATION / 100f)
        }
        val factor = (1f + ColorBoost.CONTRAST) / (1.0001f - ColorBoost.CONTRAST)
        val lift = ColorBoost.LIGHTNESS * 2.55f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    factor, 0f, 0f, 0f, lift,
                    0f, factor, 0f, 0f, lift,
                    0f, 0f, factor, 0f, lift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        colorFilter = ColorMatrixColorFilter(matrix)
    }

    /**
     * @param files 上・中・下の順。未選択は null
     * @param positionFraction 0.0〜1.0。どの時点のフレームを見るか
     * @param previewWidth 生成する画像の幅(px)。高さは16:9で決まる
     */
    suspend fun render(
        files: List<File?>,
        isImages: List<Boolean> = List(files.size) { false },
        adjusts: List<PanelAdjust>,
        layout: StackLayout,
        positionFraction: Float,
        colorBoost: Boolean = false,
        previewWidth: Int = 360
    ): Bitmap = withContext(Dispatchers.IO) {
        val paint = if (colorBoost) boostPaint else bitmapPaint
        val width = previewWidth
        val height = previewWidth * 16 / 9
        val bands = StackGeometry.bands(height, layout)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        for (index in 0 until 3) {
            val band = bands[index]
            val dst = Rect(0, band.top, width, band.top + band.height)

            val file = files.getOrNull(index)
            if (file == null) {
                canvas.drawRect(dst, emptyPaint)
                continue
            }

            val frame = if (isImages.getOrElse(index) { false }) {
                photoOf(file)
            } else {
                frameOf(file, positionFraction)
            }
            if (frame == null) {
                canvas.drawRect(dst, emptyPaint)
                continue
            }

            val rect = StackGeometry.sourceRect(
                srcWidth = frame.width,
                srcHeight = frame.height,
                bandWidth = width,
                bandHeight = band.height,
                adjust = adjusts.getOrElse(index) { PanelAdjust() }
            )

            val src = Rect(
                ((rect.cx - rect.w / 2f) * frame.width).toInt().coerceIn(0, frame.width - 1),
                ((rect.cy - rect.h / 2f) * frame.height).toInt().coerceIn(0, frame.height - 1),
                ((rect.cx + rect.w / 2f) * frame.width).toInt().coerceIn(1, frame.width),
                ((rect.cy + rect.h / 2f) * frame.height).toInt().coerceIn(1, frame.height)
            )

            canvas.drawBitmap(frame, src, dst, paint)
        }

        // 帯の境目をうっすら見せて、どこで切れているか分かるようにする
        canvas.drawRect(0f, bands[0].height - 1f, width.toFloat(), bands[0].height + 1f, dividerPaint)
        val second = (bands[0].height + bands[1].height).toFloat()
        canvas.drawRect(0f, second - 1f, width.toFloat(), second + 1f, dividerPaint)

        output
    }

    /**
     * 選び直したときに古いフレームを残さない。
     *
     * ここで recycle() してはいけない。描画中の別スレッドがそのフレームを
     * 掴んでいると「破棄済みBitmapを使った」で落ち、以後プレビューが
     * 出なくなる。参照を外すだけにしてGCに任せる。
     */
    fun clear() {
        frameCache.clear()
    }

    /**
     * 写真を読む。動画と違って時刻の概念がないので、パスだけを鍵に持ち回す。
     * 画面に出すだけなので、長辺1600pxまで間引いて読む。
     */
    private fun photoOf(file: File): Bitmap? {
        val key = "photo:${file.absolutePath}"
        frameCache[key]?.let { return it }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > 1600) sample *= 2

            val decoded = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )

            // 縦で撮った写真は、横のまま保存して「回して見せる」印が付いていることが
            // ある。BitmapFactory はその印を見ないので、自分で回す
            val bitmap = decoded?.let { applyExifRotation(file, it) }

            if (bitmap != null) {
                if (frameCache.size > 24) clear()
                frameCache[key] = bitmap
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val degrees = try {
            when (
                ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun frameOf(file: File, positionFraction: Float): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            // 0.5秒刻みに丸めて、スライダーの細かい動きで取り出し直さないようにする
            val timeMs = (durationMs * positionFraction.coerceIn(0f, 1f)).toLong() / 500 * 500
            val key = "${file.absolutePath}@$timeMs"

            frameCache[key]?.let { return it }

            // OPTION_CLOSEST_SYNC は、その時刻より前にキーフレームが無いと null を返す。
            // 端末や撮影アプリによっては先頭付近で普通に起きるので、順に緩めて探す。
            val frame = retriever.getFrameAtTime(
                timeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
                ?: retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                ?: retriever.getFrameAtTime()

            if (frame != null) {
                if (frameCache.size > 24) clear()
                frameCache[key] = frame
            }
            frame
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
