package com.colorsafe.trim.data

import android.content.Context
import android.os.StatFs
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import com.colorsafe.trim.model.TrimError
import com.colorsafe.trim.model.TrimMode
import com.colorsafe.trim.model.VideoColorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class TrimException(val error: TrimError) : Exception(error.message)

/**
 * ffmpegでのトリム実行。最優先は -c copy (再エンコードなし)。
 * 傾き補正(回転)が指定された場合や再エンコードが必要な場合は、
 * ffprobeで取得した色空間情報をそのまま引き継いで色味の変化を最小限にする。
 */
class VideoTrimmer(private val context: Context) {

    /** 最低限確保しておきたい空き容量の余裕分(バイト) */
    private val storageBufferBytes = 100L * 1024 * 1024

    suspend fun trim(
        readPath: String,
        sourceInfo: VideoColorInfo,
        startSeconds: Double,
        endSeconds: Double,
        outputExtension: String,
        mode: TrimMode,
        angleDegrees: Float = 0f
    ): File = withContext(Dispatchers.IO) {
        ensureEnoughStorage(estimatedOutputBytes(sourceInfo, endSeconds - startSeconds))

        val duration = (endSeconds - startSeconds).coerceAtLeast(0.1)
        val outputFile = File(context.cacheDir, "colorsafe_trim_${System.currentTimeMillis()}.$outputExtension")
        val useFaststart = outputExtension.equals("mp4", true) || outputExtension.equals("mov", true)

        val command = if (angleDegrees != 0f) {
            buildRotateCommand(readPath, startSeconds, duration, useFaststart, angleDegrees, sourceInfo, outputFile)
        } else when (mode) {
            TrimMode.FAST_STREAM_COPY -> buildCopyCommand(readPath, startSeconds, duration, outputFile)
            TrimMode.ACCURATE_REENCODE -> buildReencodeCommand(
                readPath, startSeconds, duration, useFaststart, sourceInfo, outputFile
            )
        }

        val session = executeAwait(command)

        if (!ReturnCode.isSuccess(session.returnCode)) {
            outputFile.delete()
            if (ReturnCode.isCancel(session.returnCode)) {
                throw TrimException(TrimError.Cancelled)
            }
            val detail = session.failStackTrace ?: session.allLogsAsString.orEmpty().takeLast(300)
            throw TrimException(TrimError.FfmpegFailure(detail.ifBlank { "不明なエラー" }))
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            throw TrimException(TrimError.FfmpegFailure("出力ファイルが作成されませんでした"))
        }

        outputFile
    }

    private suspend fun executeAwait(command: List<String>): Session = suspendCancellableCoroutine { cont ->
        val session = FFmpegKit.executeWithArgumentsAsync(command.toTypedArray()) { completedSession ->
            if (cont.isActive) cont.resume(completedSession)
        }
        cont.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }

    private fun buildCopyCommand(
        readPath: String,
        start: Double,
        duration: Double,
        output: File
    ): List<String> = buildList {
        add("-y")
        add("-ss"); add(start.toString())
        add("-i"); add(readPath)
        add("-t"); add(duration.toString())
        add("-map"); add("0")
        add("-map_metadata"); add("0")
        add("-c"); add("copy")
        // +faststart はmoovアトムの書き換え(実質的な部分リマックス)を伴い、
        // 一部端末が付与するDolby Vision(dvcC/dvvC)等の高度なHDRコンテナ情報が
        // 巻き添えで欠落する可能性があるため、コピー経路では付けない。
        add(output.absolutePath)
    }

    private fun buildReencodeCommand(
        readPath: String,
        start: Double,
        duration: Double,
        faststart: Boolean,
        sourceInfo: VideoColorInfo,
        output: File
    ): List<String> = buildList {
        val isHighBitDepth = sourceInfo.pixFmt?.contains("10") == true || sourceInfo.pixFmt?.contains("12") == true
        val isHevc = isHighBitDepth || sourceInfo.codecName == "hevc"
        // このFFmpegKit後継フォークはGPLライセンスのlibx264/libx265を同梱していないビルドがあり、
        // その場合 "-preset"/"-crf" が「Unrecognized option」で失敗する。
        // ライセンス不問で同梱されているAndroid端末のハードウェアエンコーダ(MediaCodec)を使う。
        val videoEncoder = when {
            sourceInfo.codecName == "vp9" -> "libvpx-vp9"
            sourceInfo.codecName == "av1" -> "libsvtav1"
            isHevc -> "hevc_mediacodec"
            else -> "h264_mediacodec"
        }
        val usesMediaCodec = videoEncoder.endsWith("_mediacodec")

        add("-y")
        add("-ss"); add(start.toString())
        add("-i"); add(readPath)
        add("-t"); add(duration.toString())
        add("-map"); add("0")
        add("-c:v"); add(videoEncoder)
        if (usesMediaCodec) {
            val bitrate = sourceInfo.bitrate?.takeIf { it > 0 } ?: estimateBitrate(sourceInfo)
            add("-b:v"); add(bitrate.toString())
        } else {
            add("-preset"); add("medium")
            add("-crf"); add("18")
        }

        // ffprobeが色情報を取得できない(unspecified)動画も多いため、
        // その場合は民生カメラ映像で最も一般的なRec.709/limited rangeを既定値として明示する。
        // タグを空のままにすると、再生側の推測が入力時と出力時で食い違い、
        // 暗く/くすんで見える(マトリクス誤判定の典型症状)原因になる。
        sourceInfo.pixFmt?.let { add("-pix_fmt"); add(it) }
        add("-colorspace"); add(sourceInfo.colorSpace ?: "bt709")
        add("-color_primaries"); add(sourceInfo.colorPrimaries ?: "bt709")
        add("-color_trc"); add(sourceInfo.colorTransfer ?: "bt709")
        add("-color_range"); add(sourceInfo.colorRange ?: "tv")

        if (videoEncoder == "libx265" || videoEncoder == "hevc_mediacodec") {
            // QuickTime/ギャラリー互換性向上のためのタグ付け
            add("-tag:v"); add("hvc1")
        }

        add("-c:a"); add("copy")
        if (faststart) {
            add("-movflags"); add("+faststart")
        }
        add(output.absolutePath)
    }

    /**
     * 傾き補正(任意角度の回転)をしてトリムする。回転はピクセルを動かす処理のため
     * 必ず再エンコードになるが、色空間情報は元動画からそのまま引き継いで色味の変化を防ぐ。
     * 回転で生じる黒い余白は、余白が出ない最大サイズで自動的に中央クロップする。
     */
    private fun buildRotateCommand(
        readPath: String,
        start: Double,
        duration: Double,
        faststart: Boolean,
        angleDegrees: Float,
        sourceInfo: VideoColorInfo,
        output: File
    ): List<String> = buildList {
        val isHighBitDepth = sourceInfo.pixFmt?.contains("10") == true || sourceInfo.pixFmt?.contains("12") == true
        val isHevc = isHighBitDepth || sourceInfo.codecName == "hevc"
        val videoEncoder = when {
            sourceInfo.codecName == "vp9" -> "libvpx-vp9"
            sourceInfo.codecName == "av1" -> "libsvtav1"
            isHevc -> "hevc_mediacodec"
            else -> "h264_mediacodec"
        }
        val usesMediaCodec = videoEncoder.endsWith("_mediacodec")

        val angleRad = Math.toRadians(angleDegrees.toDouble())
        val (cropW, cropH) = largestInteriorRect(sourceInfo.width.toDouble(), sourceInfo.height.toDouble(), angleRad)
        // 多くのエンコーダは偶数の幅・高さを要求するため2の倍数に切り下げる
        val cropWInt = ((cropW.roundToInt()) / 2 * 2).coerceAtLeast(2)
        val cropHInt = ((cropH.roundToInt()) / 2 * 2).coerceAtLeast(2)

        add("-y")
        add("-ss"); add(start.toString())
        add("-i"); add(readPath)
        add("-t"); add(duration.toString())
        add("-vf")
        add("rotate=a=$angleRad:ow=rotw($angleRad):oh=roth($angleRad):c=black,crop=$cropWInt:$cropHInt")
        add("-map"); add("0:v:0")
        add("-map"); add("0:a?")
        add("-c:v"); add(videoEncoder)
        if (usesMediaCodec) {
            val bitrate = sourceInfo.bitrate?.takeIf { it > 0 } ?: estimateBitrate(sourceInfo)
            add("-b:v"); add(bitrate.toString())
        } else {
            add("-preset"); add("medium")
            add("-crf"); add("18")
        }

        // 回転フィルターを通した映像がハードウェアエンコーダの想定外のピクセル形式にならないよう明示する
        add("-pix_fmt"); add(if (isHighBitDepth) "p010le" else "yuv420p")

        add("-colorspace"); add(sourceInfo.colorSpace ?: "bt709")
        add("-color_primaries"); add(sourceInfo.colorPrimaries ?: "bt709")
        add("-color_trc"); add(sourceInfo.colorTransfer ?: "bt709")
        add("-color_range"); add(sourceInfo.colorRange ?: "tv")

        if (videoEncoder == "libx265" || videoEncoder == "hevc_mediacodec") {
            add("-tag:v"); add("hvc1")
        }

        add("-c:a"); add("copy")
        if (faststart) {
            add("-movflags"); add("+faststart")
        }
        add(output.absolutePath)
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

    private fun ensureEnoughStorage(estimatedBytes: Long) {
        val stat = StatFs(context.cacheDir.absolutePath)
        val freeBytes = stat.availableBytes
        if (freeBytes < estimatedBytes + storageBufferBytes) {
            throw TrimException(TrimError.InsufficientStorage)
        }
    }

    private fun estimatedOutputBytes(sourceInfo: VideoColorInfo, durationSeconds: Double): Long {
        val bitrate = sourceInfo.bitrate ?: (8_000_000L)
        return ((bitrate / 8.0) * durationSeconds).toLong().coerceAtLeast(10L * 1024 * 1024)
    }

    /** 元動画のビットレートが取得できない場合の目安値(解像度とフレームレートから概算) */
    private fun estimateBitrate(info: VideoColorInfo): Long {
        val pixels = (info.width.toLong() * info.height.toLong()).coerceAtLeast(1)
        val fps = info.frameRate?.takeIf { it > 0 } ?: 30.0
        val bitsPerPixelPerFrame = 0.1
        val bps = (pixels * fps * bitsPerPixelPerFrame).toLong()
        return bps.coerceIn(4_000_000L, 50_000_000L)
    }
}
