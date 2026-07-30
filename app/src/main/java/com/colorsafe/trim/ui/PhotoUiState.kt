package com.colorsafe.trim.ui

import android.graphics.Bitmap
import android.net.Uri

data class PhotoUiState(
    val photoUri: Uri? = null,
    val displayName: String? = null,
    val originalBitmap: Bitmap? = null,
    val angleDegrees: Float = 0f,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
) {
    val hasPhoto: Boolean get() = photoUri != null && originalBitmap != null
    val canSave: Boolean get() = hasPhoto && !isLoading && !isSaving
}
