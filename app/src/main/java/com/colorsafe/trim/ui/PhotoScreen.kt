package com.colorsafe.trim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PhotoScreen(
    state: PhotoUiState,
    onPickPhotoClick: () -> Unit,
    onAngleChanged: (Float) -> Unit,
    onResetAngle: () -> Unit,
    onSaveClicked: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit,
    onSwitchMode: () -> Unit = {}
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            TextButton(onClick = onSwitchMode, modifier = Modifier.align(Alignment.Start)) {
                Text("◀ 動画のトリミングへ", style = MaterialTheme.typography.bodySmall)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "写真の傾き補正",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "水平・垂直をまっすぐに",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!state.hasPhoto) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.isLoading) {
                            CircularProgressIndicator()
                        } else {
                            Text(
                                text = "写真を選んでください",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onPickPhotoClick) { Text("写真を選択") }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp)),
                            color = Color.Black
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val bitmap = state.originalBitmap
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(rotationZ = state.angleDegrees)
                                    )
                                }
                                // 傾き調整の目安になる、回転しない固定の三分割ガイド線
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val guideColor = Color.White.copy(alpha = 0.6f)
                                    val thirdW = size.width / 3f
                                    val thirdH = size.height / 3f
                                    for (i in 1..2) {
                                        drawLine(
                                            guideColor,
                                            Offset(thirdW * i, 0f),
                                            Offset(thirdW * i, size.height),
                                            strokeWidth = 1.5f
                                        )
                                        drawLine(
                                            guideColor,
                                            Offset(0f, thirdH * i),
                                            Offset(size.width, thirdH * i),
                                            strokeWidth = 1.5f
                                        )
                                    }
                                }
                            }
                        }

                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "%.1f°".format(state.angleDegrees),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Slider(
                            value = state.angleDegrees,
                            onValueChange = onAngleChanged,
                            valueRange = -45f..45f,
                            enabled = !state.isSaving
                        )
                        TextButton(onClick = onResetAngle, enabled = !state.isSaving) {
                            Text("水平に戻す")
                        }

                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))

                        TextButton(onClick = onPickPhotoClick, enabled = !state.isSaving) {
                            Text("別の写真を選ぶ")
                        }
                    }
                }
            }

            if (state.hasPhoto) {
                Button(
                    onClick = onSaveClicked,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).aspectRatio(1f),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Text("  保存中...")
                    } else {
                        Text("保存")
                    }
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = { TextButton(onClick = onDismissError) { Text("閉じる") } },
            title = { Text("エラー") },
            text = { Text(message) }
        )
    }

    if (state.successMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissSuccess,
            confirmButton = { TextButton(onClick = onDismissSuccess) { Text("OK") } },
            title = { Text(state.successMessage) },
            text = {}
        )
    }
}
