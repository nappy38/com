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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.colorsafe.trim.ui.TrimScreen
import com.colorsafe.trim.ui.TrimViewModel
import com.colorsafe.trim.ui.theme.ColorSafeTrimTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TrimViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorSafeTrimTheme {
                val context = LocalContext.current
                val state by viewModel.uiState.collectAsState()

                val documentPickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> uri?.let { viewModel.onVideoPicked(it) } }

                val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        documentPickerLauncher.launch(arrayOf("video/*"))
                    } else {
                        viewModel.onPermissionDenied()
                    }
                }

                TrimScreen(
                    state = state,
                    onPickVideoClick = {
                        val granted = ContextCompat.checkSelfPermission(context, readPermission) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            documentPickerLauncher.launch(arrayOf("video/*"))
                        } else {
                            permissionLauncher.launch(readPermission)
                        }
                    },
                    onStartChanged = viewModel::onStartChanged,
                    onEndChanged = viewModel::onEndChanged,
                    onAngleChanged = viewModel::onAngleChanged,
                    onResetAngle = viewModel::onResetAngle,
                    onSaveClicked = viewModel::onSaveClicked,
                    onDismissError = viewModel::dismissError,
                    onDismissSuccess = viewModel::dismissSuccess,
                    onDurationReady = viewModel::onPlayerDurationReady
                )
            }
        }
    }
}
