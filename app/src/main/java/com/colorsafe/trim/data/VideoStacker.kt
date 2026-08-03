package com.colorsafe.trim.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.StatFs
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
// Media3 は android.util.Size ではなく独自の Size を使う。取り違えると
// getOutputSize がインターフェースを実装していない扱いになる。
import androidx.media3.common.util.Size
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.SpeedChangeEffect
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
import com.colorsafe.trim.model.ColorBoost
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
        isImages: List<Boolean> = List(inputs.size) { false },
        adjusts: List<PanelAdjust>,
        layout: StackLayout,
        audioPanelIndex: Int,
        outputWidth: Int,
        maxDurationMs: Long? = null,
        colorBoost: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.Main) {
        require(inputs.size == 3) { "3分割には素材が3つ必要です" }

        val outputHeight = outputWidth * 16 / 9
        val bands = StackGeometry.bands(outputHeight, layout)

        val metas = withContext(Dispatchers.IO) {
            inputs.mapIndexed { index, file ->
                if (isImages.getOrElse(index) { false }) readImageMeta(file) else readMeta(file)
            }
        }

        // 速度をかけた後の実際の長さ。倍速なら半分になる。
        // 写真は長さを持たないので、尺の計算から外す。
        val effectiveMs = metas.mapIndexed { index, meta ->
            if (isImages.getOrElse(index) { false }) {
                null
            } else {
                (meta.durationMs / adjusts[index].speed.coerceAtLeast(0.1f)).toLong()
            }
        }

        // 指定がなければ一番短い動画に合わせる。全部写真なら基準が無いので4秒。
        // 指定がある場合、そこに届かない動画は末尾を最後の絵で埋めて揃える。
        val autoMs = effectiveMs.filterNotNull().minOrNull() ?: 4000L
        val targetMs = (maxDurationMs ?: autoMs).coerceAtLeast(200L)

        withContext(Dispatchers.IO) {
            ensureEnoughStorage(estimatedOutputBytes(outputWidth, targetMs))
        }

        val outputFile = File(context.cacheDir, "colorsafe_stack_${System.currentTimeMillis()}.mp4")
        val freezeFiles = mutableListOf<File>()

        val sequences = inputs.mapIndexed { index, file ->
            val band = bands[index]
            val meta = metas[index]
            val isImage = isImages.getOrElse(index) { false }

            val speed = adjusts[index].speed.coerceIn(0.1f, 4f)
            // 目標の長さを埋めるのに必要な「元の尺」。倍速なら2倍必要になる
            val neededSourceMs = (targetMs * speed).toLong()
            val clipMs = minOf(meta.durationMs, neededSourceMs).coerceAtLeast(100L)
            // 速度をかけた後の長さ。ここが目標に届かない分を静止画で埋める
            val padMs = targetMs - (clipMs / speed).toLong()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(0L)
                        .setEndPositionMs(clipMs)
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

            // 色味を持ち上げるのは切り出しと縮小のあと。先にかけると
            // 捨てる部分まで計算することになる
            val boost: List<Effect> = if (colorBoost) {
                listOf(
                    HslAdjustment.Builder()
                        .adjustSaturation(ColorBoost.SATURATION)
                        .adjustLightness(ColorBoost.LIGHTNESS)
                        .build(),
                    Contrast(ColorBoost.CONTRAST)
                )
            } else {
                emptyList()
            }

            // 静止画の継ぎ足しには速度をかけない。尺を直接指定しているため
            val stillEffects: List<Effect> = listOf(
                Crop(left, right, bottom, top),
                // Crop後は帯と同じ縦横比になっているので、引き伸ばさずぴったり収まる
                Presentation.createForWidthAndHeight(
                    outputWidth,
                    band.height,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            ) + boost

            // 写真は最初から最後まで動かない。尺を直接与えるだけで済む
            if (isImage) {
                return@mapIndexed EditedMediaItemSequence(
                    EditedMediaItem.Builder(
                        MediaItem.Builder()
                            .setUri(Uri.fromFile(file))
                            .setMimeType(imageMimeOf(file))
                            .build()
                    )
                        .setDurationUs(targetMs * 1000L)
                        .setFrameRate(30)
                        .setEffects(Effects(emptyList(), stillEffects))
                        .build()
                )
            }

            val keepAudio = index == audioPanelIndex
            // 音を残すパネルだけは、音も一緒に伸び縮みさせる必要がある。
            // 映像だけの SpeedChangeEffect では音とズレる。
            val speedPair = if (speed != 1f && keepAudio) {
                Effects.createExperimentalSpeedChangingEffect(constantSpeed(speed))
            } else {
                null
            }

            val videoEffects = when {
                speedPair != null -> stillEffects + speedPair.second
                speed != 1f -> stillEffects + SpeedChangeEffect(speed)
                else -> stillEffects
            }
            val audioProcessors: List<AudioProcessor> =
                if (speedPair != null) listOf(speedPair.first) else emptyList()

            val edited = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(!keepAudio)
                .setEffects(Effects(audioProcessors, videoEffects))
                .build()

            // 指定の長さに届かない素材は、最後のコマを静止画として継ぎ足す。
            // ffmpeg の tpad=stop_mode=clone と同じ考え方。
            val freeze = if (padMs >= 100L) {
                withContext(Dispatchers.IO) { extractLastFrame(file, clipMs, index) }
            } else {
                null
            }

            if (freeze == null) {
                EditedMediaItemSequence(edited)
            } else {
                freezeFiles += freeze
                val still = EditedMediaItem.Builder(
                    // 拡張子からも判定されるが、画像として扱われないと
                    // 動画の読み込み側へ回されて失敗するので明示する
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(freeze))
                        .setMimeType(MimeTypes.IMAGE_JPEG)
                        .build()
                )
                    .setDurationUs(padMs * 1000L)
                    .setFrameRate(30)
                    .setEffects(Effects(emptyList(), stillEffects))
                    .build()
                EditedMediaItemSequence(edited, still)
            }
        }

        val compositionBuilder = Composition.Builder(sequences)
            .setVideoCompositorSettings(BandCompositorSettings(outputWidth, outputHeight, bands))

        // 次のいずれかのときは全体をSDRに落とす。素材をそのまま積むだけの
        // ときは、これまでどおりHDRを保つ。
        //  - 静止画が混ざる(継ぎ足し・写真)
        //    HDR動画にSDRの画像を混ぜると「HDR出力なのにSDRが来た」で落ちる
        //  - 色味を持ち上げる
        //    HslAdjustment が HDR に対応しておらず「HDR is not yet supported」で落ちる
        val usesStillImage = freezeFiles.isNotEmpty() || isImages.any { it }
        if (usesStillImage || colorBoost) {
            compositionBuilder.setHdrMode(
                Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            )
        }

        val composition = compositionBuilder.build()

        var transformer: Transformer? = null

        try {
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
                                                TrimError.FfmpegFailure(describe(exportException))
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
        } finally {
            // 継ぎ足し用に書き出した静止画は、成功しても失敗しても捨てる
            freezeFiles.forEach { it.delete() }
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

            // Media3の合成器は、各シーケンスの実ピクセルサイズ(ここではPresentationで
            // outputWidth×band.heightに揃えてある)を背景キャンバスとの比率で自動的に
            // スケーリングする。ここでさらに band.height/outHeight を掛けると二重適用になり、
            // 帯が本来よりずっと小さく縮んでしまう。等倍(1,1)のままでよい。
            return OverlaySettings.Builder()
                .setScale(1f, 1f)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, ndcY)
                .build()
        }
    }

    /**
     * 失敗の中身を1行にまとめる。
     *
     * Media3 の message は「Asset loader error」のように種類しか出さず、
     * 本当の理由は cause の奥にある。原因を追えるよう連鎖ごと出す。
     */
    private fun describe(e: ExportException): String {
        val sb = StringBuilder()
        sb.append(e.message ?: "書き出しに失敗しました")
        sb.append(" [code=").append(e.errorCode).append("]")
        var cause: Throwable? = e.cause
        var depth = 0
        while (cause != null && depth < 4) {
            sb.append(" / ")
                .append(cause.javaClass.simpleName)
                .append(": ")
                .append(cause.message ?: "-")
            cause = cause.cause
            depth++
        }
        return sb.toString()
    }

    /** 常に同じ速度を返す SpeedProvider。速度は途中で変えない */
    private fun constantSpeed(speed: Float): SpeedProvider = object : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    /**
     * 指定の長さに足りない素材のために、末尾のコマを静止画として書き出す。
     * これを尺付きで継ぎ足すと「最後の絵で止まる」動きになる。
     */
    private fun extractLastFrame(source: File, clipMs: Long, index: Int): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            // ちょうど末尾だとコマが取れないことがあるので少し手前を狙う
            val atUs = (clipMs - 60L).coerceAtLeast(0L) * 1000L
            val bitmap = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime()
                ?: return null

            val out = File(
                context.cacheDir,
                "colorsafe_freeze_${index}_${System.currentTimeMillis()}.jpg"
            )
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            out
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * 写真の寸法を読む。中身は展開せず、大きさだけ見る。
     * 縦で撮った写真は横のまま保存して「回して見せる」印が付いていることが
     * あるので、その場合は縦横を入れ替える。切り出しの計算がずれるため。
     */
    private fun readImageMeta(file: File): Meta {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val rotated = try {
            when (
                ExifInterface(file.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270 -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }

        val w = options.outWidth.coerceAtLeast(0)
        val h = options.outHeight.coerceAtLeast(0)
        return if (rotated) Meta(h, w, 0L) else Meta(w, h, 0L)
    }

    /**
     * 拡張子から画像の種類を決める。
     * 画像として扱われないと動画の読み込み側へ回されて失敗するため、明示する。
     */
    private fun imageMimeOf(file: File): String =
        when (file.extension.lowercase()) {
            "png" -> MimeTypes.IMAGE_PNG
            "webp" -> MimeTypes.IMAGE_WEBP
            "heic" -> MimeTypes.IMAGE_HEIC
            "heif" -> MimeTypes.IMAGE_HEIF
            else -> MimeTypes.IMAGE_JPEG
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
