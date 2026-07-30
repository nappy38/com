package com.colorsafe.trim.ui

import android.net.Uri
import com.colorsafe.trim.model.VideoColorInfo

data class TrimUiState(
    val videoUri: Uri? = null,
    val displayName: String? = null,
    val extension: String = "mp4",
    val colorInfo: VideoColorInfo? = null,
    val durationSeconds: Double = 0.0,
    val startSeconds: Double = 0.0,
    val endSeconds: Double = 0.0,
    val previewSeekSeconds: Double? = null,
    val angleDegrees: Float = 0f,
    val isProbing: Boolean = false,
    val isSaving: Boolean = false,
    val savingStepMessage: String = "",
    val successMessage: String? = null,
    val colorPreservedMessage: String? = null,
    val errorMessage: String? = null
) {
    val hasVideo: Boolean get() = videoUri != null
    val canSave: Boolean get() = hasVideo && !isProbing && !isSaving && endSeconds > startSeconds
}
