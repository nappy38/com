package com.colorsafe.trim.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.colorsafe.trim.model.TrimError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 写真の傾き補正(水平・垂直合わせ)。回転後、余白が出ない最大の矩形で自動クロップする。 */
class PhotoStraightener(private val context: Context) {

    suspend fun loadBitmap(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val original = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw TrimException(TrimError.Unknown("画像を読み込めませんでした"))

        val orientation = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        applyExifOrientation(original, orientation)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** 指定角度(度、時計回り)で回転し、黒い余白が出ない最大サイズで中央クロップする */
    suspend fun straighten(bitmap: Bitmap, angleDegrees: Float): Bitmap = withContext(Dispatchers.Default) {
        if (angleDegrees == 0f) return@withContext bitmap

        val matrix = Matrix().apply { postRotate(angleDegrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val (cropW, cropH) = largestInteriorRect(
            bitmap.width.toDouble(),
            bitmap.height.toDouble(),
            Math.toRadians(angleDegrees.toDouble())
        )
        val cropWInt = cropW.roundToInt().coerceIn(1, rotated.width)
        val cropHInt = cropH.roundToInt().coerceIn(1, rotated.height)
        val left = ((rotated.width - cropWInt) / 2).coerceAtLeast(0)
        val top = ((rotated.height - cropHInt) / 2).coerceAtLeast(0)

        Bitmap.createBitmap(rotated, left, top, cropWInt, cropHInt)
    }

    /**
     * w×hの矩形をangleRadだけ回転させたときに、余白なしで収まる最大の軸並行矩形のサイズを求める。
     * (出典: 回転画像の最大内接矩形を求める一般的なアルゴリズム)
     */
    private fun largestInteriorRect(w: Double, h: Double, angleRad: Double): Pair<Double, Double> {
        if (w <= 0 || h <= 0) return 0.0 to 0.0

        val widthIsLonger = w >= h
        val sideLong = if (widthIsLonger) w else h
        val sideShort = if (widthIsLonger) h else w

        val sinA = abs(sin(angleRad))
        val cosA = abs(cos(angleRad))

        return if (sideShort <= 2.0 * sinA * cosA * sideLong || abs(sinA - cosA) < 1e-10) {
            val x = 0.5 * sideShort
            if (widthIsLonger) (x / sinA) to (x / cosA) else (x / cosA) to (x / sinA)
        } else {
            val cos2a = cosA * cosA - sinA * sinA
            ((w * cosA - h * sinA) / cos2a) to ((h * cosA - w * sinA) / cos2a)
        }
    }
}
