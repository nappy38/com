package com.colorsafe.trim.model

/**
 * ffprobeから取得した映像ストリームの色情報。
 * -c copy が使えない場合の再エンコード時、これらの値をそのままffmpegへ渡して色味を維持する。
 */
data class VideoColorInfo(
    val codecName: String?,
    val pixFmt: String?,
    val colorSpace: String?,
    val colorTransfer: String?,
    val colorPrimaries: String?,
    val colorRange: String?,
    val width: Int,
    val height: Int,
    val durationSeconds: Double,
    val bitrate: Long?,
    val frameRate: Double?
) {
    val isHdr: Boolean
        get() = colorTransfer == "smpte2084" || colorTransfer == "arib-std-b67"

    /** 表示用の要約(例: "HDR10 / BT.2020 / 3840x2160") */
    fun summaryLabel(): String {
        val dynamicRange = when (colorTransfer) {
            "smpte2084" -> "HDR10"
            "arib-std-b67" -> "HLG"
            else -> "SDR"
        }
        val gamut = when {
            colorPrimaries == "bt2020" -> "BT.2020"
            colorPrimaries == "bt709" -> "BT.709"
            colorPrimaries != null -> colorPrimaries
            else -> "不明"
        }
        return "$dynamicRange / $gamut / ${width}x${height}"
    }

    /** 保存後の検証で「色空間を維持できたか」を比較する */
    fun hasSameColorMetadataAs(other: VideoColorInfo): Boolean {
        return colorSpace == other.colorSpace &&
            colorTransfer == other.colorTransfer &&
            colorPrimaries == other.colorPrimaries &&
            colorRange == other.colorRange
    }
}
