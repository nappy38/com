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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.colorsafe.trim.ui.StackScreen
import com.colorsafe.trim.ui.StackViewModel
import com.colorsafe.trim.ui.TrimScreen
import com.colorsafe.trim.ui.TrimViewModel
import com.colorsafe.trim.ui.theme.ColorSafeTrimTheme
import com.colorsafe.trim.ui.theme.WatercolorBackground

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

                // 動画選択の画面を開いている間にActivityが作り直されても
                // 値が失われないよう rememberSaveable にする。remember だと
                // 枠の番号が0に戻り、選んだ動画が別の枠に入ってしまう。
                var mode by rememberSaveable { mutableIntStateOf(MODE_TRIM) }
                // 3分割で「どの枠へ入れる動画か」を、選択ダイアログを跨いで覚えておく
                var pendingPanelIndex by rememberSaveable { mutableIntStateOf(0) }

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

                WatercolorBackground(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 切り替えは画面下に置く。上端だと片手で親指が届かない。
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
                                    onSpeedChanged = stackViewModel::onSpeedChanged,
                                    onLayoutChanged = stackViewModel::onLayoutChanged,
                                    onAudioPanelChanged = stackViewModel::onAudioPanelChanged,
                                    onOutputWidthChanged = stackViewModel::onOutputWidthChanged,
                                    onTargetSecondsChanged = stackViewModel::onTargetSecondsChanged,
                                    onColorBoostChanged = stackViewModel::onColorBoostChanged,
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

                        // 背景のにじみを下まで続かせたいので、バーは透明にする
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            NavigationBarItem(
                                selected = mode == MODE_TRIM,
                                onClick = { mode = MODE_TRIM },
                                icon = { Icon(Icons.Filled.ContentCut, contentDescription = null) },
                                label = { Text("トリム") }
                            )
                            NavigationBarItem(
                                selected = mode == MODE_STACK,
                                onClick = { mode = MODE_STACK },
                                icon = { Icon(Icons.Filled.ViewAgenda, contentDescription = null) },
                                label = { Text("3分割") }
                            )
                        }
                    }
                }
            }
        }
    }
}
