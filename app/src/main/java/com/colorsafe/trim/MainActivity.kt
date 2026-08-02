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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.colorsafe.trim.ui.StackScreen
import com.colorsafe.trim.ui.StackViewModel
import com.colorsafe.trim.ui.TrimScreen
import com.colorsafe.trim.ui.TrimViewModel
import com.colorsafe.trim.ui.theme.ColorSafeTrimTheme

private const val MODE_TRIM = 0
private const val MODE_STACK = 1

class MainActivity : ComponentActivity() {

    private val viewModel: TrimViewModel by viewModels()
    private val stackViewModel: StackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ColorSafeTrimTheme {
                val context = LocalContext.current
                val state by viewModel.uiState.collectAsState()
                val stackState by stackViewModel.uiState.collectAsState()

                var mode by remember { mutableIntStateOf(MODE_TRIM) }
                // 3分割で「どの枠へ入れる動画か」を、選択ダイアログを跨いで覚えておく
                var pendingPanelIndex by remember { mutableIntStateOf(0) }

                val documentPickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> uri?.let { viewModel.onVideoPicked(it) } }

                val stackPickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> uri?.let { stackViewModel.onVideoPicked(pendingPanelIndex, it) } }

                val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        if (mode == MODE_STACK) {
                            stackPickerLauncher.launch(arrayOf("video/*"))
                        } else {
                            documentPickerLauncher.launch(arrayOf("video/*"))
                        }
                    } else {
                        viewModel.onPermissionDenied()
                    }
                }

                fun hasReadPermission(): Boolean =
                    ContextCompat.checkSelfPermission(context, readPermission) ==
                        PackageManager.PERMISSION_GRANTED

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = mode == MODE_TRIM,
                                onClick = { mode = MODE_TRIM },
                                label = { Text("トリム") }
                            )
                            FilterChip(
                                selected = mode == MODE_STACK,
                                onClick = { mode = MODE_STACK },
                                label = { Text("3分割") }
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            if (mode == MODE_STACK) {
                                StackScreen(
                                    state = stackState,
                                    onPickVideoClick = { index ->
                                        pendingPanelIndex = index
                                        if (hasReadPermission()) {
                                            stackPickerLauncher.launch(arrayOf("video/*"))
                                        } else {
                                            permissionLauncher.launch(readPermission)
                                        }
                                    },
                                    onOffsetChanged = stackViewModel::onOffsetChanged,
                                    onZoomChanged = stackViewModel::onZoomChanged,
                                    onLayoutChanged = stackViewModel::onLayoutChanged,
                                    onAudioPanelChanged = stackViewModel::onAudioPanelChanged,
                                    onOutputWidthChanged = stackViewModel::onOutputWidthChanged,
                                    onPreviewPositionChanged = stackViewModel::onPreviewPositionChanged,
                                    onCreateClicked = stackViewModel::onCreateClicked,
                                    onDismissError = stackViewModel::dismissError,
                                    onDismissSuccess = stackViewModel::dismissSuccess
                                )
                            } else {
                                TrimScreen(
                                    state = state,
                                    onPickVideoClick = {
                                        if (hasReadPermission()) {
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
            }
        }
    }
}
