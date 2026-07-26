package com.colorsafe.trim.data

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.colorsafe.trim.model.KeyframeCheckResult
import com.colorsafe.trim.model.VideoColorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * ffprobeで動画の色空間・HDR情報・キーフレーム位置を解析するラッパー。
 * FFmpegKit互換フォークが提供するSAF("saf:")プロトコルを使い、
 * content:// のURIをコピーせずそのまま読む。
 */
class VideoProbe(private val context: Context) {

    /** SAF経由でffmpeg/ffprobeから読める入力パスに変換する */
    fun resolveReadPath(uri: Uri): String {
        return FFmpegKitConfig.getSafParameterForRead(context, uri)
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
        val session = FFprobeKit.execute(command.joinToString(" ") { arg ->
            if (arg.contains(" ")) "\"$arg\"" else arg
        })
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
        val session = FFprobeKit.execute(command.joinToString(" "))
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
