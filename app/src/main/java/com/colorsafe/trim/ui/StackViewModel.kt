package com.colorsafe.trim.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.colorsafe.trim.data.MediaStoreSaver
import com.colorsafe.trim.data.StackPreviewRenderer
import com.colorsafe.trim.data.TrimException
import com.colorsafe.trim.data.VideoProbe
import com.colorsafe.trim.data.VideoStacker
import com.colorsafe.trim.model.StackLayout
import com.colorsafe.trim.model.TrimError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StackViewModel(application: Application) : AndroidViewModel(application) {

    private val probe = VideoProbe(application)
    private val stacker = VideoStacker(application)
    private val previewRenderer = StackPreviewRenderer()
    private val saver = MediaStoreSaver(application)

    private val _uiState = MutableStateFlow(StackUiState())
    val uiState: StateFlow<StackUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null

    fun onVideoPicked(index: Int, uri: Uri) {
        if (index !in 0..2) return

        val extension = resolveExtension(uri)
        val name = resolveDisplayName(uri)

        _uiState.value.panels.getOrNull(index)?.file?.delete()
        _uiState.value = _uiState.value.copy(loadingPanelIndex = index, errorMessage = null)

        viewModelScope.launch {
            try {
                val staged = probe.stageInputFile(uri, extension)
                val info = probe.probeColorInfo(staged.absolutePath)

                val panels = _uiState.value.panels.toMutableList()
                panels[index] = panels[index].copy(
                    file = staged,
                    displayName = name,
                    durationSeconds = info.durationSeconds
                )

                // 描画中の処理を先に止めてからキャッシュを捨てる。逆にすると
                // 描画側が消えたフレームを掴んだままになる。
                previewJob?.cancel()
                previewRenderer.clear()
                _uiState.value = _uiState.value.copy(
                    panels = panels,
                    loadingPanelIndex = null
                )
                refreshPreview()
            } catch (e: TrimException) {
                _uiState.value = _uiState.value.copy(
                    loadingPanelIndex = null,
                    errorMessage = e.error.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loadingPanelIndex = null,
                    errorMessage = TrimError.Unknown(e.message ?: "読み込みに失敗しました").message
                )
            }
        }
    }

    fun onOffsetChanged(index: Int, value: Float) {
        updateAdjust(index) { it.copy(offsetY = value) }
    }

    fun onZoomChanged(index: Int, value: Float) {
        updateAdjust(index) { it.copy(zoom = value) }
    }

    fun onLayoutChanged(layout: StackLayout) {
        _uiState.value = _uiState.value.copy(layout = layout)
        refreshPreview()
    }

    fun onAudioPanelChanged(index: Int) {
        _uiState.value = _uiState.value.copy(audioPanelIndex = index)
    }

    fun onOutputWidthChanged(width: Int) {
        _uiState.value = _uiState.value.copy(outputWidth = width)
    }

    /** null で「一番短い素材に合わせる」 */
    fun onTargetSecondsChanged(seconds: Double?) {
        _uiState.value = _uiState.value.copy(targetSeconds = seconds)
    }

    fun onPreviewPositionChanged(fraction: Float) {
        _uiState.value = _uiState.value.copy(previewPosition = fraction.coerceIn(0f, 1f))
        refreshPreview()
    }

    fun onCreateClicked() {
        val state = _uiState.value
        if (!state.canSave) return

        val files = state.panels.mapNotNull { it.file }
        if (files.size != 3) return

        _uiState.value = state.copy(
            isSaving = true,
            progress = 0f,
            savingStepMessage = "3分割を書き出しています",
            errorMessage = null,
            successMessage = null,
            savedUri = null
        )

        viewModelScope.launch {
            try {
                val output = stacker.stack(
                    inputs = files,
                    adjusts = state.panels.map { it.adjust },
                    layout = state.layout,
                    audioPanelIndex = state.audioPanelIndex,
                    outputWidth = state.outputWidth,
                    maxDurationMs = state.targetSeconds?.let { (it * 1000).toLong() }
                ) { progress ->
                    _uiState.value = _uiState.value.copy(progress = progress)
                }

                _uiState.value = _uiState.value.copy(
                    progress = 1f,
                    savingStepMessage = "ギャラリーへ保存しています"
                )

                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPAN).format(Date())
                val uri = saver.saveToGallery(output, "3panel_$stamp.mp4", "mp4")

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savingStepMessage = "",
                    savedUri = uri,
                    successMessage = "保存しました。CapCutで開いてテロップを載せてください。"
                )
            } catch (e: TrimException) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savingStepMessage = "",
                    errorMessage = e.error.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savingStepMessage = "",
                    errorMessage = TrimError.Unknown(e.message ?: "書き出しに失敗しました").message
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun dismissSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null, savedUri = null)
    }

    private fun updateAdjust(index: Int, transform: (com.colorsafe.trim.model.PanelAdjust) -> com.colorsafe.trim.model.PanelAdjust) {
        if (index !in 0..2) return
        val panels = _uiState.value.panels.toMutableList()
        panels[index] = panels[index].copy(adjust = transform(panels[index].adjust))
        _uiState.value = _uiState.value.copy(panels = panels)
        refreshPreview()
    }

    /**
     * スライダーを動かすたびに描き直す。前の描画は捨てる。
     * フレームの取り出しは StackPreviewRenderer 側でキャッシュされるので、
     * 位置調整中は合成だけがやり直される。
     */
    private fun refreshPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val state = _uiState.value
            if (state.filledCount == 0) return@launch
            // プレビューの失敗で viewModelScope 全体を巻き込まないよう囲う。
            // ここで例外を素通しすると、以降どの描画も走らなくなる。
            runCatching {
                previewRenderer.render(
                    files = state.panels.map { it.file },
                    adjusts = state.panels.map { it.adjust },
                    layout = state.layout,
                    positionFraction = state.previewPosition
                )
            }.onSuccess { bitmap ->
                _uiState.value = _uiState.value.copy(previewBitmap = bitmap)
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) {
                return cursor.getString(idx)
            }
        }
        return "動画"
    }

    private fun resolveExtension(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        val mime = resolver.getType(uri)
        val fromMime = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return fromMime ?: "mp4"
    }

    override fun onCleared() {
        super.onCleared()
        previewRenderer.clear()
        _uiState.value.panels.forEach { it.file?.delete() }
    }
}
