package com.colorsafe.trim.model

/** キーフレーム解析の結果。 */
data class KeyframeCheckResult(
    val requestedStartSeconds: Double,
    val nearestKeyframeSeconds: Double?,
    val isOnKeyframe: Boolean
) {
    val differenceSeconds: Double
        get() = if (nearestKeyframeSeconds != null) requestedStartSeconds - nearestKeyframeSeconds else 0.0
}

/** アプリ内で扱うエラーを日本語メッセージ付きで表現する。 */
sealed class TrimError(val message: String) {
    data object InsufficientStorage : TrimError("容量不足のため保存できませんでした。空き容量を確保してから再度お試しください。")
    data object PermissionDenied : TrimError("動画にアクセスする権限がありません。設定から権限を許可してください。")
    data class FfmpegFailure(val detail: String) : TrimError("動画の処理に失敗しました。($detail)")
    data object Cancelled : TrimError("処理をキャンセルしました。")
    data class Unknown(val detail: String) : TrimError("予期しないエラーが発生しました。($detail)")
}
