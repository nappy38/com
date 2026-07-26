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

class TrimException(val error: TrimError) : Exception(error.message)

/**
 * ffmpegでのトリム実行。最優先は -c copy (再エンコードなし)。
 * 再エンコードが必要な場合は、ffprobeで取得した色空間情報をそのまま引き継ぐ。
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
        mode: TrimMode
    ): File = withContext(Dispatchers.IO) {
        ensureEnoughStorage(estimatedOutputBytes(sourceInfo, endSeconds - startSeconds))

        val duration = (endSeconds - startSeconds).coerceAtLeast(0.1)
        val outputFile = File(context.cacheDir, "colorsafe_trim_${System.currentTimeMillis()}.$outputExtension")
        val useFaststart = outputExtension.equals("mp4", true) || outputExtension.equals("mov", true)

        val command = when (mode) {
            TrimMode.FAST_STREAM_COPY -> buildCopyCommand(readPath, startSeconds, duration, useFaststart, outputFile)
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
        faststart: Boolean,
        output: File
    ): List<String> = buildList {
        add("-y")
        add("-ss"); add(start.toString())
        add("-i"); add(readPath)
        add("-t"); add(duration.toString())
        add("-map"); add("0")
        add("-c"); add("copy")
        if (faststart) {
            add("-movflags"); add("+faststart")
        }
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
        val videoEncoder = when {
            sourceInfo.codecName == "vp9" -> "libvpx-vp9"
            sourceInfo.codecName == "av1" -> "libsvtav1"
            isHighBitDepth -> "libx265"
            sourceInfo.codecName == "hevc" -> "libx265"
            else -> "libx264"
        }

        add("-y")
        add("-ss"); add(start.toString())
        add("-i"); add(readPath)
        add("-t"); add(duration.toString())
        add("-map"); add("0")
        add("-c:v"); add(videoEncoder)
        add("-preset"); add("medium")
        add("-crf"); add("18")

        // ffprobeが色情報を取得できない(unspecified)動画も多いため、
        // その場合は民生カメラ映像で最も一般的なRec.709/limited rangeを既定値として明示する。
        // タグを空のままにすると、再生側の推測が入力時と出力時で食い違い、
        // 暗く/くすんで見える(マトリクス誤判定の典型症状)原因になる。
        sourceInfo.pixFmt?.let { add("-pix_fmt"); add(it) }
        add("-colorspace"); add(sourceInfo.colorSpace ?: "bt709")
        add("-color_primaries"); add(sourceInfo.colorPrimaries ?: "bt709")
        add("-color_trc"); add(sourceInfo.colorTransfer ?: "bt709")
        add("-color_range"); add(sourceInfo.colorRange ?: "tv")

        if (videoEncoder == "libx265") {
            // QuickTime/ギャラリー互換性向上のためのタグ付け
            add("-tag:v"); add("hvc1")
        }

        add("-c:a"); add("copy")
        if (faststart) {
            add("-movflags"); add("+faststart")
        }
        add(output.absolutePath)
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
}
