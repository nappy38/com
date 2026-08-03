package com.colorsafe.trim.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.colorsafe.trim.R
import com.colorsafe.trim.ui.theme.WatercolorBloom
import kotlin.math.roundToInt

@Composable
fun TrimScreen(
    state: TrimUiState,
    onPickVideoClick: () -> Unit,
    onStartChanged: (Float) -> Unit,
    onEndChanged: (Float) -> Unit,
    onAngleChanged: (Float) -> Unit,
    onResetAngle: () -> Unit,
    onSaveClicked: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit,
    onDurationReady: (Long) -> Unit = {}
) {
    var showLicenses by remember { mutableStateOf(false) }

    // 背景は MainActivity の水彩に任せるので、ここは透かす
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            AppHeader()

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!state.hasVideo) {
                    EmptyState(onPickVideoClick = onPickVideoClick)
                } else {
                    LoadedContent(
                        state = state,
                        onStartChanged = onStartChanged,
                        onEndChanged = onEndChanged,
                        onAngleChanged = onAngleChanged,
                        onResetAngle = onResetAngle,
                        onPickVideoClick = onPickVideoClick,
                        onDurationReady = onDurationReady
                    )
                }
            }

            if (state.hasVideo) {
                SaveButton(state = state, onSaveClicked = onSaveClicked)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            }

            TextButton(onClick = { showLicenses = true }) {
                Text(
                    text = "ライセンス情報",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
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
            text = {
                if (state.colorPreservedMessage != null) {
                    Text(state.colorPreservedMessage)
                }
            }
        )
    }
}

@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ColorSafe Trim",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "色味を変えずに動画をトリミング",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(onPickVideoClick: () -> Unit) {
    // 何も置かれていない画面なので、ここだけは絵を大きく見せる。
    // 固定サイズだと狭い端末ではみ出すので、空いている領域いっぱいに描く。
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WatercolorBloom(modifier = Modifier.fillMaxSize())

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "動画を選んでください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onPickVideoClick) {
                Text("動画を選択")
            }
        }
    }
}

@Composable
private fun LoadedContent(
    state: TrimUiState,
    onStartChanged: (Float) -> Unit,
    onEndChanged: (Float) -> Unit,
    onAngleChanged: (Float) -> Unit,
    onResetAngle: () -> Unit,
    onPickVideoClick: () -> Unit,
    onDurationReady: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 縦動画・横動画どちらでも正しい向きで表示されるよう、
        // 枠の縦横比は固定せず、PlayerViewのFITモードに向きの判断を任せる
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp)),
            // 黒い枠は画面全体を暗く見せる。動画の余白は紙の色で受ける
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.videoUri != null) {
                    VideoPreview(
                        uri = state.videoUri,
                        seekToSeconds = state.previewSeekSeconds,
                        modifier = Modifier.graphicsLayer(rotationZ = state.angleDegrees),
                        onDurationReady = onDurationReady
                    )
                }
                if (state.angleDegrees != 0f) {
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
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))

        if (state.isProbing) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        } else {
            state.colorInfo?.let { info ->
                Text(
                    text = info.summaryLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "開始 ${formatTime(state.startSeconds)}　終了 ${formatTime(state.endSeconds)}",
                style = MaterialTheme.typography.titleMedium
            )
            RangeSlider(
                value = state.startSeconds.toFloat()..state.endSeconds.toFloat(),
                onValueChange = { range ->
                    onStartChanged(range.start)
                    onEndChanged(range.endInclusive)
                },
                valueRange = 0f..(state.durationSeconds.toFloat().coerceAtLeast(0.1f)),
                enabled = !state.isSaving
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "傾き補正 %.1f°".format(state.angleDegrees),
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = state.angleDegrees,
                onValueChange = onAngleChanged,
                valueRange = -45f..45f,
                enabled = !state.isSaving
            )
            if (state.angleDegrees != 0f) {
                TextButton(onClick = onResetAngle, enabled = !state.isSaving) {
                    Text("傾きをリセット")
                }
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = onPickVideoClick, enabled = !state.isSaving) {
            Text("別の動画を選ぶ")
        }
    }
}

@Composable
private fun SaveButton(state: TrimUiState, onSaveClicked: () -> Unit) {
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
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(0.dp))
            Text("  " + state.savingStepMessage.ifBlank { "保存中..." })
        } else {
            Text("保存")
        }
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val licenseText = remember {
        val lgpl = context.resources.openRawResource(R.raw.license_lgpl_3_0)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val gpl = context.resources.openRawResource(R.raw.license_gpl_3_0)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        buildString {
            append("このアプリは動画処理に FFmpegKit(FFmpeg) を使用しています。\n")
            append("配布物: com.moizhassan.ffmpeg:ffmpeg-kit-16kb\n")
            append("ライセンス: GNU Lesser General Public License v3.0 (LGPL-3.0)\n")
            append("ソース: https://github.com/moizhassankh/ffmpeg-kit-android-16KB\n")
            append("(元プロジェクト: https://github.com/arthenica/ffmpeg-kit)\n")
            append("\nLGPL-3.0はGNU General Public License v3.0(GPL-3.0)を一部引用しているため、\n")
            append("両方のライセンス全文をあわせて掲載します。\n")
            append("\n\n===== GNU LESSER GENERAL PUBLIC LICENSE Version 3 =====\n\n")
            append(lgpl)
            append("\n\n===== GNU GENERAL PUBLIC LICENSE Version 3 =====\n\n")
            append(gpl)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("オープンソースライセンス") },
        text = {
            Text(
                text = licenseText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

private fun formatTime(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}
