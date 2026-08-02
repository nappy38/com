package com.colorsafe.trim.data

import android.content.Context
import android.os.StatFs
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import com.colorsafe.trim.model.TrimError
import com.colorsafe.trim.model.VideoColorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class TrimException(val error: TrimError) : Exception(error.message)

/**
 * ffmpegでのトリム実行。常に -c copy (再エンコードなし)を使い、
 * 画質・色味・HDRを完全に維持する。
 *
 * (このFFmpegKit後継フォークには動画エンコーダが同梱されていないため、
 * 再エンコードが必要な処理はここでは行えない。傾き補正は VideoRotator
 * [Media3 Transformer] が別途担当する。)
 */
class VideoTrimmer(private val context: Context) {

    /** 最低限確保しておきたい空き容量の余裕分(バイト) */
    private val storageBufferBytes = 100L * 1024 * 1024

    suspend fun trim(
        readPath: String,
        sourceInfo: VideoColorInfo,
        startSeconds: Double,
        endSeconds: Double,
        outputExtension: String
    ): File = withContext(Dispatchers.IO) {
        ensureEnoughStorage(estimatedOutputBytes(sourceInfo, endSeconds - startSeconds))

        val duration = (endSeconds - startSeconds).coerceAtLeast(0.1)
        val outputFile = File(context.cacheDir, "colorsafe_trim_${System.currentTimeMillis()}.$outputExtension")

        val command = buildCopyCommand(readPath, startSeconds, duration, outputFile)
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
