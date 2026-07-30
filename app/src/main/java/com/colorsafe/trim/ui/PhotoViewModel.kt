package com.colorsafe.trim.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.colorsafe.trim.data.PhotoSaver
import com.colorsafe.trim.data.PhotoStraightener
import com.colorsafe.trim.data.TrimException
import com.colorsafe.trim.model.TrimError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val straightener = PhotoStraightener(application)
    private val saver = PhotoSaver(application)

    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    fun onPhotoPicked(uri: Uri) {
        val context = getApplication<Application>()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val name = resolveName(uri)
        _uiState.value = PhotoUiState(photoUri = uri, displayName = name, isLoading = true)

        viewModelScope.launch {
            try {
                val bitmap = straightener.loadBitmap(uri)
                _uiState.value = _uiState.value.copy(originalBitmap = bitmap, isLoading = false)
            } catch (e: TrimException) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.error.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = TrimError.Unknown(e.message ?: "画像の読み込みに失敗しました").message
                )
            }
        }
    }

    fun onAngleChanged(angle: Float) {
        _uiState.value = _uiState.value.copy(angleDegrees = angle.coerceIn(-45f, 45f))
    }

    fun onResetAngle() {
        _uiState.value = _uiState.value.copy(angleDegrees = 0f)
    }

    fun onPermissionDenied() {
        _uiState.value = _uiState.value.copy(errorMessage = TrimError.PermissionDenied.message)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun dismissSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun onSaveClicked() {
        val state = _uiState.value
        val bitmap = state.originalBitmap ?: return
        if (!state.canSave) return

        _uiState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val result = straightener.straighten(bitmap, state.angleDegrees)
                val displayName = (state.displayName?.substringBeforeLast('.') ?: "photo") + "_straight.jpg"
                saver.saveToGallery(result, displayName)

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "保存しました"
                )
            } catch (e: TrimException) {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = e.error.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = TrimError.Unknown(e.message ?: "不明なエラー").message
                )
            }
        }
    }

    private fun resolveName(uri: Uri): String {
        val context = getApplication<Application>()
        var name = "photo"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                cursor.getString(idx)?.let { name = it }
            }
        }
        return name
    }
}
