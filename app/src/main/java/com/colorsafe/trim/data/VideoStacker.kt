package com.colorsafe.trim.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import androidx.media3.common.MediaItem
// Media3 は android.util.Size ではなく独自の Size を使う。取り違えると
// getOutputSize がインターフェースを実装していない扱いになる。
import androidx.media3.common.util.Size
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.colorsafe.trim.model.PanelAdjust
import com.colorsafe.trim.model.StackGeometry
import com.colorsafe.trim.model.StackLayout
import com.colorsafe.trim.model.TrimError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 動画3本を縦に積んで1本の9:16動画にする(ffmpegの vstack 相当)。
 *
 * ffmpeg側は使えない。同梱のFFmpegKit後継フォークには動画エンコーダが
 * mpeg4/mjpeg/pngしか入っておらず、H.264で書き出せないため
 * (VideoTrimmer のコメント参照)。よって傾き補正と同じく、端末の
 * MediaCodecを使うMedia3 Transformerで合成する。
 *
 * 3本をそれぞれ独立したシーケンスとして与え、VideoCompositorSettings で
 * 出力フレーム内の上・中・下へ配置する。各シーケンスには Crop で
 * 「元動画のどこを使うか」、Presentation で「帯のピクセルサイズ」を与える。
 */
class VideoStacker(private val context: Context) {

    private val storageBufferBytes = 100L * 1024 * 1024

    /**
     * @param inputs 上・中・下の順に3本
     * @param adjusts 各パネルの位置調整(inputsと同じ並び)
     * @param audioPanelIndex 音を採用するパネル。-1で無音
     * @param outputWidth 1080 または 720
     */
    suspend fun stack(
        inputs: List<File>,
        adjusts: List<PanelAdjust>,
        layout: StackLayout,
        audioPanelIndex: Int,
        outputWidth: Int,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.Main) {
        require(inputs.size == 3) { "3分割には動画が3本必要です" }

        val outputHeight = outputWidth * 16 / 9
        val bands = StackGeometry.bands(outputHeight, layout)

        val metas = withContext(Dispatchers.IO) { inputs.map { readMeta(it) } }
        val shortestMs = metas.minOf { it.durationMs }.coerceAtLeast(200L)

        withContext(Dispatchers.IO) {
            ensureEnoughStorage(estimatedOutputBytes(outputWidth, shortestMs))
        }

        val outputFile = File(context.cacheDir, "colorsafe_stack_${System.currentTimeMillis()}.mp4")

        val sequences = inputs.mapIndexed { index, file ->
            val band = bands[index]
            val meta = metas[index]

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(0L)
                        .setEndPositionMs(shortestMs)
                        .build()
                )
                .build()

            val rect = StackGeometry.sourceRect(
                srcWidth = meta.width,
                srcHeight = meta.height,
                bandWidth = outputWidth,
                bandHeight = band.height,
                adjust = adjusts[index]
            )

            // Crop はNDC(-1〜1、Yは上が+)で指定する。SourceRectは左上原点の割合なので変換する。
            val left = (rect.cx - rect.w / 2f) * 2f - 1f
            val right = (rect.cx + rect.w / 2f) * 2f - 1f
            val top = 1f - (rect.cy - rect.h / 2f) * 2f
            val bottom = 1f - (rect.cy + rect.h / 2f) * 2f

            val effects = listOf(
                Crop(left, right, bottom, top),
                // Crop後は帯と同じ縦横比になっているので、引き伸ばさずぴったり収まる
                Presentation.createForWidthAndHeight(
                    outputWidth,
                    band.height,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )

            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(index != audioPanelIndex)
                .setEffects(Effects(emptyList(), effects))
                .build()

            EditedMediaItemSequence(edited)
        }

        val composition = Composition.Builder(sequences)
            .setVideoCompositorSettings(BandCompositorSettings(outputWidth, outputHeight, bands))
            .build()

        var transformer: Transformer? = null

        coroutineScope {
            val poller = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    delay(200)
                    val t = transformer ?: continue
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                }
            }

            try {
                suspendCancellableCoroutine { cont ->
                    val t = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult
                            ) {
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
                                                TrimError.FfmpegFailure(
                                                    exportException.message ?: "3分割の書き出しに失敗しました"
                                                )
                                            )
                                        )
                                    )
                                }
                            }
                        })
                        .build()

                    transformer = t
                    t.start(composition, outputFile.absolutePath)

                    cont.invokeOnCancellation { t.cancel() }
                }
            } finally {
                poller.cancel()
            }
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            throw TrimException(TrimError.FfmpegFailure("出力ファイルが作成されませんでした"))
        }

        outputFile
    }

    /**
     * 出力フレームのどこに各シーケンスを置くかを決める。
     * 合成器は各入力を「出力フレーム全体に貼るオーバーレイ」として扱うため、
     * 縦方向だけ帯の高さぶんに縮めて、帯の中心へ寄せる。
     */
    private class BandCompositorSettings(
        private val outWidth: Int,
        private val outHeight: Int,
        private val bands: List<StackGeometry.Band>
    ) : VideoCompositorSettings {

        override fun getOutputSize(inputSizes: List<Size>): Size = Size(outWidth, outHeight)

        override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
            val band = bands.getOrElse(inputId) { bands.last() }
            val centerYPx = band.top + band.height / 2f
            // 画面座標(上が0)からNDC(上が+1)へ
            val ndcY = 1f - 2f * (centerYPx / outHeight.toFloat())

            return OverlaySettings.Builder()
                .setScale(1f, band.height.toFloat() / outHeight.toFloat())
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, ndcY)
                .build()
        }
    }

    private data class Meta(val width: Int, val height: Int, val durationMs: Long)

    /**
     * 実際に表示される向きの幅・高さと長さを読む。
     * 縦向き撮影は回転タグ付きで保存されるため、タグを見て縦横を入れ替える。
     */
    private fun readMeta(file: File): Meta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val rawW = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawH = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            if (rotation == 90 || rotation == 270) {
                Meta(rawH, rawW, durationMs)
            } else {
                Meta(rawW, rawH, durationMs)
            }
        } catch (e: Exception) {
            Meta(0, 0, 0L)
        } finally {
            retriever.release()
        }
    }

    private fun ensureEnoughStorage(estimatedBytes: Long) {
        val free = StatFs(context.cacheDir.absolutePath).availableBytes
        if (free < estimatedBytes + storageBufferBytes) {
            throw TrimException(TrimError.InsufficientStorage)
        }
    }

    private fun estimatedOutputBytes(outputWidth: Int, durationMs: Long): Long {
        val bitrate = if (outputWidth >= 1080) 10_000_000L else 5_000_000L
        return ((bitrate / 8.0) * (durationMs / 1000.0)).toLong().coerceAtLeast(10L * 1024 * 1024)
    }
}
