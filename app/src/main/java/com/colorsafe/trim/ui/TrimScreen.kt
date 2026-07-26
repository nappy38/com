package com.colorsafe.trim.ui

import android.net.Uri
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.colorsafe.trim.model.KeyframeCheckResult
import com.colorsafe.trim.model.TrimMode
import kotlin.math.roundToInt

@Composable
fun TrimScreen(
    state: TrimUiState,
    onPickVideoClick: () -> Unit,
    onStartChanged: (Float) -> Unit,
    onEndChanged: (Float) -> Unit,
    onSaveClicked: () -> Unit,
    onModeChosen: (TrimMode) -> Unit,
    onDismissModeChoice: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit,
    onDurationReady: (Long) -> Unit = {}
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
            AppHeader()

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!state.hasVideo) {
                    EmptyState(onPickVideoClick = onPickVideoClick)
                } else {
                    LoadedContent(
                        state = state,
                        onStartChanged = onStartChanged,
                        onEndChanged = onEndChanged,
                        onPickVideoClick = onPickVideoClick,
                        onDurationReady = onDurationReady
                    )
                }
            }

            if (state.hasVideo) {
                SaveButton(state = state, onSaveClicked = onSaveClicked)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    state.pendingModeChoice?.let { result ->
        ModeChoiceDialog(result = result, onModeChosen = onModeChosen, onDismiss = onDismissModeChoice)
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

@Composable
private fun LoadedContent(
    state: TrimUiState,
    onStartChanged: (Float) -> Unit,
    onEndChanged: (Float) -> Unit,
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
            color = Color.Black
        ) {
            if (state.videoUri != null) {
                VideoPreview(
                    uri = state.videoUri,
                    seekToSeconds = state.previewSeekSeconds,
                    onDurationReady = onDurationReady
                )
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

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))

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
private fun ModeChoiceDialog(
    result: KeyframeCheckResult,
    onModeChosen: (TrimMode) -> Unit,
    onDismiss: () -> Unit
) {
    // Material3 AlertDialogはボタンが横に収まらないとconfirmButtonを上・dismissButtonを下に
    // 自動で積むため、confirmButton=「2」だと数字の並びと表示順が逆転し誤タップを招く。
    // ここでは表示順を明示的に「1」→「2」に固定できるDialogを直接使う。
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "正確に切り取るには再エンコードが必要です",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "開始位置がキーフレームと一致していないため、色味を完全維持したまま高速に切り出すと、開始位置が最大${"%.1f".format(kotlin.math.abs(result.differenceSeconds))}秒ずれる場合があります。",
                    style = MaterialTheme.typography.bodyMedium
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { onModeChosen(TrimMode.FAST_STREAM_COPY) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1. 高速(色味完全維持)")
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onModeChosen(TrimMode.ACCURATE_REENCODE) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("2. 正確(再エンコード)")
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("キャンセル")
                }
            }
        }
    }
}

private fun formatTime(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}
