package com.colorsafe.trim

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.colorsafe.trim.ui.ChooserScreen
import com.colorsafe.trim.ui.PhotoScreen
import com.colorsafe.trim.ui.PhotoViewModel
import com.colorsafe.trim.ui.TrimScreen
import com.colorsafe.trim.ui.TrimViewModel
import com.colorsafe.trim.ui.theme.ColorSafeTrimTheme

private enum class AppMode { VIDEO, PHOTO }

class MainActivity : ComponentActivity() {

    private val videoViewModel: TrimViewModel by viewModels()
    private val photoViewModel: PhotoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorSafeTrimTheme {
                val context = LocalContext.current
                var mode by remember { mutableStateOf<AppMode?>(null) }
                val videoState by videoViewModel.uiState.collectAsState()
                val photoState by photoViewModel.uiState.collectAsState()

                val videoPickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        mode = AppMode.VIDEO
                        videoViewModel.onVideoPicked(uri)
                    }
                }
                val photoPickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        mode = AppMode.PHOTO
                        photoViewModel.onPhotoPicked(uri)
                    }
                }

                val videoReadPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                val photoReadPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val videoPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        videoPickerLauncher.launch(arrayOf("video/*"))
                    } else {
                        videoViewModel.onPermissionDenied()
                    }
                }
                val photoPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        photoPickerLauncher.launch(arrayOf("image/*"))
                    } else {
                        photoViewModel.onPermissionDenied()
                    }
                }

                val pickVideo: () -> Unit = {
                    val granted = ContextCompat.checkSelfPermission(context, videoReadPermission) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        videoPickerLauncher.launch(arrayOf("video/*"))
                    } else {
                        videoPermissionLauncher.launch(videoReadPermission)
                    }
                }
                val pickPhoto: () -> Unit = {
                    val granted = ContextCompat.checkSelfPermission(context, photoReadPermission) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        photoPickerLauncher.launch(arrayOf("image/*"))
                    } else {
                        photoPermissionLauncher.launch(photoReadPermission)
                    }
                }

                when (mode) {
                    null -> ChooserScreen(
                        onPickVideoClick = pickVideo,
                        onPickPhotoClick = pickPhoto
                    )
                    AppMode.VIDEO -> TrimScreen(
                        state = videoState,
                        onPickVideoClick = pickVideo,
                        onStartChanged = videoViewModel::onStartChanged,
                        onEndChanged = videoViewModel::onEndChanged,
                        onSaveClicked = videoViewModel::onSaveClicked,
                        onDismissError = videoViewModel::dismissError,
                        onDismissSuccess = videoViewModel::dismissSuccess,
                        onDurationReady = videoViewModel::onPlayerDurationReady,
                        onSwitchMode = { mode = AppMode.PHOTO }
                    )
                    AppMode.PHOTO -> PhotoScreen(
                        state = photoState,
                        onPickPhotoClick = pickPhoto,
                        onAngleChanged = photoViewModel::onAngleChanged,
                        onResetAngle = photoViewModel::onResetAngle,
                        onSaveClicked = photoViewModel::onSaveClicked,
                        onDismissError = photoViewModel::dismissError,
                        onDismissSuccess = photoViewModel::dismissSuccess,
                        onSwitchMode = { mode = AppMode.VIDEO }
                    )
                }
            }
        }
    }
}
