package com.colorsafe.trim.ui

import android.graphics.Bitmap
import android.net.Uri
import com.colorsafe.trim.model.PanelAdjust
import com.colorsafe.trim.model.StackLayout
import java.io.File

/** 3分割の1枠分。まだ選んでいなければ file が null。 */
data class PanelSlot(
    val file: File? = null,
    val displayName: String? = null,
    val durationSeconds: Double = 0.0,
    val adjust: PanelAdjust = PanelAdjust()
)

data class StackUiState(
    val panels: List<PanelSlot> = List(3) { PanelSlot() },
    val layout: StackLayout = StackLayout.EVEN,
    /** 音を採用するパネル。-1 で無音 */
    val audioPanelIndex: Int = 1,
    val outputWidth: Int = 1080,
    /** 出来上がりの長さの指定。null なら一番短い素材に合わせる */
    val targetSeconds: Double? = null,
    /** 色味を持ち上げるか。既定は素材そのまま */
    val colorBoost: Boolean = false,
    /**
     * プレビューで見ている位置(0.0〜1.0)。
     * 冒頭は暗転していたりカメラの露出が合っていないことが多く、
     * 0だと真っ暗な絵を見せてしまうため、少し進んだ位置を既定にする。
     */
    val previewPosition: Float = 0.35f,
    val previewBitmap: Bitmap? = null,
    val loadingPanelIndex: Int? = null,
    val isSaving: Boolean = false,
    val progress: Float = 0f,
    val savingStepMessage: String = "",
    val successMessage: String? = null,
    val savedUri: Uri? = null,
    val errorMessage: String? = null
) {
    val filledCount: Int get() = panels.count { it.file != null }
    val isReady: Boolean get() = filledCount == 3

    /** 一番短い素材の長さ。ここを超える尺は作れない */
    val sourceSeconds: Double
        get() = panels.filter { it.file != null }.minOfOrNull { it.durationSeconds } ?: 0.0

    /** 出来上がりの長さ。指定があればその長さちょうどになる */
    val outputSeconds: Double
        get() = targetSeconds ?: sourceSeconds

    /** 指定の長さに足りず、短い素材を最後の絵で止めて埋める状態か */
    val isPadded: Boolean
        get() = targetSeconds != null && sourceSeconds > 0.0 && targetSeconds > sourceSeconds

    val canSave: Boolean get() = isReady && !isSaving && loadingPanelIndex == null
}
