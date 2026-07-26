package com.colorsafe.trim.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFprobeKit
import com.colorsafe.trim.model.KeyframeCheckResult
import com.colorsafe.trim.model.TrimError
import com.colorsafe.trim.model.VideoColorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * 動画の取り込みと、ffprobeによる色空間・HDR情報・キーフレーム位置の解析を行うラッパー。
 *
 * content:// URIをffmpeg/ffprobeへ直接渡すSAFプロトコルは、FFmpegKit後継フォークによって
 * 実装状況が異なり信頼できないため使わない。代わりに一度アプリのキャッシュ領域へ
 * 実ファイルとしてコピーしてから処理することで、どの端末・どのフォークでも確実に動くようにする。
 */
class VideoProbe(private val context: Context) {

    private val storageBufferBytes = 100L * 1024 * 1024

    /** content:// URIをアプリのキャッシュ領域へコピーし、実ファイルパスとして扱えるようにする */
    suspend fun stageInputFile(uri: Uri, extension: String): File = withContext(Dispatchers.IO) {
        val sourceSize = querySize(uri)
        if (sourceSize != null) {
            val free = StatFs(context.cacheDir.absolutePath).availableBytes
            if (free < sourceSize + storageBufferBytes) {
                throw TrimException(TrimError.InsufficientStorage)
            }
        }

        val outFile = File(context.cacheDir, "colorsafe_input_${System.currentTimeMillis()}.$extension")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw TrimException(TrimError.PermissionDenied)
        input.use { streamIn ->
            outFile.outputStream().use { streamOut -> streamIn.copyTo(streamOut) }
        }
        outFile
    }

    private fun querySize(uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) {
                return cursor.getLong(idx)
            }
        }
        return null
    }

    suspend fun probeColorInfo(readPath: String): VideoColorInfo = withContext(Dispatchers.IO) {
        val command = arrayOf(
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries",
            "stream=codec_name,pix_fmt,color_space,color_transfer,color_primaries,color_range,width,height,bit_rate,r_frame_rate:format=duration",
            "-of", "json",
            readPath
        )
        val session = FFprobeKit.executeWithArguments(command)
        val output = session.output ?: throw IllegalStateException("ffprobeの出力が空です")
        parseColorInfo(output)
    }

    private fun parseColorInfo(json: String): VideoColorInfo {
        val root = JSONObject(json)
        val streams = root.optJSONArray("streams")
        val stream = if (streams != null && streams.length() > 0) streams.getJSONObject(0) else JSONObject()
        val format = root.optJSONObject("format")

        val frameRateRaw = stream.optString("r_frame_rate", "")
        val frameRate = parseFrameRate(frameRateRaw)

        return VideoColorInfo(
            codecName = stream.optString("codec_name", null),
            pixFmt = stream.optString("pix_fmt", null),
            colorSpace = stream.optString("color_space", null).ifBlankToNull(),
            colorTransfer = stream.optString("color_transfer", null).ifBlankToNull(),
            colorPrimaries = stream.optString("color_primaries", null).ifBlankToNull(),
            colorRange = stream.optString("color_range", null).ifBlankToNull(),
            width = stream.optInt("width", 0),
            height = stream.optInt("height", 0),
            durationSeconds = format?.optString("duration")?.toDoubleOrNull() ?: 0.0,
            bitrate = stream.optString("bit_rate", null)?.toLongOrNull(),
            frameRate = frameRate
        )
    }

    private fun parseFrameRate(raw: String): Double? {
        val parts = raw.split("/")
        if (parts.size != 2) return raw.toDoubleOrNull()
        val num = parts[0].toDoubleOrNull() ?: return null
        val den = parts[1].toDoubleOrNull() ?: return null
        if (den == 0.0) return null
        return num / den
    }

    /**
     * 指定した開始位置の直前(または一致)にある最寄りのキーフレーム位置を探す。
     * -ss を -i の前に置いた場合、ffmpegはこの位置へシークするため、
     * -c copy の実際の開始点はここになる。
     */
    suspend fun findNearestKeyframeAtOrBefore(
        readPath: String,
        requestedStartSeconds: Double
    ): KeyframeCheckResult = withContext(Dispatchers.IO) {
        val windowStart = (requestedStartSeconds - 8.0).coerceAtLeast(0.0)
        val windowDuration = (requestedStartSeconds - windowStart) + 8.0

        val command = arrayOf(
            "-v", "error",
            "-select_streams", "v:0",
            "-read_intervals", "${windowStart}%+${windowDuration}",
            "-show_entries", "packet=pts_time,flags",
            "-of", "csv=p=0",
            readPath
        )
        val session = FFprobeKit.executeWithArguments(command)
        val output = session.output.orEmpty()

        var bestAtOrBefore: Double? = null
        var bestOverall: Double? = null

        output.lineSequence().forEach { line ->
            val cols = line.split(",")
            if (cols.size < 2) return@forEach
            val ptsTime = cols[0].toDoubleOrNull() ?: return@forEach
            val flags = cols[1]
            val isKeyframe = flags.contains("K")
            if (!isKeyframe) return@forEach

            if (bestOverall == null || kotlin.math.abs(ptsTime - requestedStartSeconds) <
                kotlin.math.abs((bestOverall ?: Double.MAX_VALUE) - requestedStartSeconds)
            ) {
                bestOverall = ptsTime
            }
            if (ptsTime <= requestedStartSeconds &&
                (bestAtOrBefore == null || ptsTime > bestAtOrBefore!!)
            ) {
                bestAtOrBefore = ptsTime
            }
        }

        val nearest = bestAtOrBefore ?: bestOverall
        val isExact = nearest != null && kotlin.math.abs(nearest - requestedStartSeconds) < 0.05

        KeyframeCheckResult(
            requestedStartSeconds = requestedStartSeconds,
            nearestKeyframeSeconds = nearest,
            isOnKeyframe = isExact
        )
    }

    private fun String?.ifBlankToNull(): String? = if (this.isNullOrBlank()) null else this
}

private fun String?.toDoubleOrNull(): Double? = this?.let { runCatching { it.toDouble() }.getOrNull() }
private fun String?.toLongOrNull(): Long? = this?.let { runCatching { it.toLong() }.getOrNull() }
