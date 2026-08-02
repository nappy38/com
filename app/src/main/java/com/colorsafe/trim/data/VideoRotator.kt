package com.colorsafe.trim.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.colorsafe.trim.model.TrimError
import com.colorsafe.trim.model.VideoColorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 動画の傾き補正(任意角度の回転+自動クロップ)を行う。
 *
 * FFmpegKit後継フォークには動画エンコーダが同梱されておらず再エンコードができないため、
 * Android標準のMediaCodecを内部で使うGoogle公式のMedia3 Transformerで実装する。
 * 回転で生じる黒い余白は、余白が出ない最大サイズで自動的に中央クロップする。
 * 出力は常にMP4コンテナになる(Transformerの最も安定した出力形式のため)。
 */
class VideoRotator(private val context: Context) {

    suspend fun rotateAndTrim(
        inputFile: File,
        startSeconds: Double,
        endSeconds: Double,
        angleDegrees: Float,
        sourceInfo: VideoColorInfo
    ): File = withContext(Dispatchers.Main) {
        val outputFile = File(context.cacheDir, "colorsafe_rotate_${System.currentTimeMillis()}.mp4")

        val angleRad = Math.toRadians(angleDegrees.toDouble())
        val (cropWD, cropHD) = largestInteriorRect(
            sourceInfo.width.toDouble(),
            sourceInfo.height.toDouble(),
            angleRad
        )
        // 多くのエンコーダは偶数の幅・高さを要求するため2の倍数に切り下げる
        val cropW = (cropWD.roundToInt() / 2 * 2).coerceAtLeast(2)
        val cropH = (cropHD.roundToInt() / 2 * 2).coerceAtLeast(2)

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(inputFile))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs((startSeconds * 1000).toLong())
                    .setEndPositionMs((endSeconds * 1000).toLong())
                    .build()
            )
            .build()

        val rotate = ScaleAndRotateTransformation.Builder()
            .setRotationDegrees(angleDegrees)
            .build()
        val presentation = Presentation.createForWidthAndHeight(
            cropW,
            cropH,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), listOf(rotate, presentation)))
            .build()

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resumeWith(Result.success(outputFile))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        outputFile.delete()
                        if (cont.isActive) {
                            cont.resumeWith(
                                Result.failure(
                                    TrimException(
                                        TrimError.FfmpegFailure(exportException.message ?: "動画変換に失敗しました")
                                    )
                                )
                            )
                        }
                    }
                })
                .build()

            transformer.start(editedMediaItem, outputFile.absolutePath)

            cont.invokeOnCancellation {
                transformer.cancel()
            }
        }
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
