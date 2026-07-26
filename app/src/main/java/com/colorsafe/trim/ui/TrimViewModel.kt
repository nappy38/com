package com.colorsafe.trim.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.colorsafe.trim.data.MediaStoreSaver
import com.colorsafe.trim.data.TrimException
import com.colorsafe.trim.data.VideoProbe
import com.colorsafe.trim.data.VideoTrimmer
import com.colorsafe.trim.model.TrimError
import com.colorsafe.trim.model.TrimMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class TrimViewModel(application: Application) : AndroidViewModel(application) {

    private val probe = VideoProbe(application)
    private val trimmer = VideoTrimmer(application)
    private val saver = MediaStoreSaver(application)

    private val _uiState = MutableStateFlow(TrimUiState())
    val uiState: StateFlow<TrimUiState> = _uiState.asStateFlow()

    private var currentReadPath: String? = null
    private var stagedInputFile: File? = null

    fun onVideoPicked(uri: Uri) {
        val context = getApplication<Application>()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        stagedInputFile?.delete()
        stagedInputFile = null
        currentReadPath = null

        val (name, extension) = resolveNameAndExtension(uri)

        _uiState.value = TrimUiState(
            videoUri = uri,
            displayName = name,
            extension = extension,
            isProbing = true
        )

        viewModelScope.launch {
            try {
                val staged = probe.stageInputFile(uri, extension)
                stagedInputFile = staged
                val readPath = staged.absolutePath
                currentReadPath = readPath
                val info = probe.probeColorInfo(readPath)
                _uiState.value = _uiState.value.copy(
                    colorInfo = info,
                    durationSeconds = info.durationSeconds,
                    startSeconds = 0.0,
                    endSeconds = info.durationSeconds,
                    isProbing = false
                )
            } catch (e: TrimException) {
                _uiState.value = _uiState.value.copy(
                    isProbing = false,
                    errorMessage = e.error.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProbing = false,
                    errorMessage = TrimError.Unknown(e.message ?: "動画の解析に失敗しました").message
                )
            }
        }
    }

    fun onStartChanged(value: Float) {
        val end = _uiState.value.endSeconds
        val start = value.toDouble().coerceIn(0.0, end)
        if (start != _uiState.value.startSeconds) {
            _uiState.value = _uiState.value.copy(startSeconds = start, previewSeekSeconds = start)
        }
    }

    fun onEndChanged(value: Float) {
        val duration = _uiState.value.durationSeconds
        val start = _uiState.value.startSeconds
        val end = value.toDouble().coerceIn(start, duration)
        if (end != _uiState.value.endSeconds) {
            _uiState.value = _uiState.value.copy(endSeconds = end, previewSeekSeconds = end)
        }
    }

    fun onSaveClicked() {
        val state = _uiState.value
        val readPath = currentReadPath ?: return
        if (!state.canSave) return

        _uiState.value = state.copy(isSaving = true, savingStepMessage = "キーフレームを確認中...")

        viewModelScope.launch {
            try {
                val keyframeResult = probe.findNearestKeyframeAtOrBefore(readPath, state.startSeconds)
                if (keyframeResult.isOnKeyframe) {
                    performTrim(TrimMode.FAST_STREAM_COPY)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        savingStepMessage = "",
                        pendingModeChoice = keyframeResult
                    )
                }
            } catch (e: TrimException) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.error.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = TrimError.Unknown(e.message ?: "解析に失敗しました").message
                )
            }
        }
    }

    fun onModeChosen(mode: TrimMode) {
        _uiState.value = _uiState.value.copy(pendingModeChoice = null, isSaving = true)
        viewModelScope.launch { performTrim(mode) }
    }

    fun dismissModeChoice() {
        _uiState.value = _uiState.value.copy(pendingModeChoice = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun onPermissionDenied() {
        _uiState.value = _uiState.value.copy(errorMessage = TrimError.PermissionDenied.message)
    }

    /**
     * ffprobeで長さが取得できなかった場合のフォールバック。
     * ExoPlayerが実際に読み込めた長さを正として範囲スライダーに反映する。
     */
    fun onPlayerDurationReady(durationMs: Long) {
        val state = _uiState.value
        if (state.durationSeconds <= 0.0 && durationMs > 0) {
            val durationSeconds = durationMs / 1000.0
            _uiState.value = state.copy(
                durationSeconds = durationSeconds,
                endSeconds = durationSeconds
            )
        }
    }

    fun dismissSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null, colorPreservedMessage = null)
    }

    private suspend fun performTrim(mode: TrimMode) {
        val state = _uiState.value
        val readPath = currentReadPath ?: return
        val sourceInfo = state.colorInfo ?: return

        _uiState.value = state.copy(
            isSaving = true,
            savingStepMessage = if (mode == TrimMode.FAST_STREAM_COPY) "高速トリム中(色味完全維持)..." else "再エンコード中..."
        )

        try {
            val outputFile = trimmer.trim(
                readPath = readPath,
                sourceInfo = sourceInfo,
                startSeconds = state.startSeconds,
                endSeconds = state.endSeconds,
                outputExtension = state.extension,
                mode = mode
            )

            _uiState.value = _uiState.value.copy(savingStepMessage = "色空間を確認中...")
            val outputInfo = runCatching { probe.probeColorInfo(outputFile.absolutePath) }.getOrNull()
            val colorPreserved = outputInfo != null && sourceInfo.hasSameColorMetadataAs(outputInfo)

            _uiState.value = _uiState.value.copy(savingStepMessage = "ギャラリーへ保存中...")
            val displayName = (state.displayName?.substringBeforeLast('.') ?: "trimmed") + "_trim.${state.extension}"
            saver.saveToGallery(outputFile, displayName, state.extension)

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                savingStepMessage = "",
                successMessage = "保存しました",
                colorPreservedMessage = if (colorPreserved) "色空間を維持しました" else null
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
                errorMessage = TrimError.Unknown(e.message ?: "不明なエラー").message
            )
        }
    }

    private fun resolveNameAndExtension(uri: Uri): Pair<String, String> {
        val context = getApplication<Application>()
        var name = "video"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                cursor.getString(idx)?.let { name = it }
            }
        }
        val extensionFromName = name.substringAfterLast('.', "")
        val extension = extensionFromName.ifBlank {
            val mimeType = context.contentResolver.getType(uri)
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp4"
        }.lowercase()
        return name to extension
    }

    override fun onCleared() {
        super.onCleared()
        stagedInputFile?.delete()
    }
}
