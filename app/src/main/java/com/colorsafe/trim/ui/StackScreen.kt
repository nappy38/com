package com.colorsafe.trim.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.colorsafe.trim.model.StackLayout
import kotlin.math.roundToInt

private val PANEL_LABELS = listOf("上のパネル", "中央のパネル", "下のパネル")

@Composable
fun StackScreen(
    state: StackUiState,
    onPickVideoClick: (Int) -> Unit,
    onOffsetChanged: (Int, Float) -> Unit,
    onZoomChanged: (Int, Float) -> Unit,
    onLayoutChanged: (StackLayout) -> Unit,
    onAudioPanelChanged: (Int) -> Unit,
    onOutputWidthChanged: (Int) -> Unit,
    onPreviewPositionChanged: (Float) -> Unit,
    onCreateClicked: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(modifier = Modifier.padding(top = 20.dp)) {
                Text(
                    text = "3分割",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "動画3本を縦に積んで1本にします。テロップはCapCutで。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- 素材 ----------
            SectionLabel("そざい")
            for (index in 0..2) {
                PanelSlotRow(
                    index = index,
                    slot = state.panels[index],
                    isLoading = state.loadingPanelIndex == index,
                    enabled = !state.isSaving,
                    onPickVideoClick = onPickVideoClick
                )
            }

            // ---------- プレビュー ----------
            // 9:16を画面幅いっぱいにすると縦700dp超になり、下のボタンまで
            // 延々スクロールすることになる。高さを固定して幅を導く。
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (state.previewBitmap != null) {
                    Image(
                        bitmap = state.previewBitmap.asImageBitmap(),
                        contentDescription = "3分割のプレビュー",
                        modifier = Modifier
                            .height(300.dp)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .height(300.dp)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "動画を選ぶと\nここに出ます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.isReady) {
                LabeledSlider(
                    label = "再生位置",
                    value = state.previewPosition,
                    valueRange = 0f..1f,
                    valueText = "%.1fs".format(state.outputSeconds * state.previewPosition),
                    enabled = !state.isSaving,
                    onValueChange = onPreviewPositionChanged
                )
            }

            // ---------- 割り付け ----------
            SectionLabel("わりつけ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StackLayout.entries.forEach { layout ->
                    FilterChip(
                        selected = state.layout == layout,
                        onClick = { onLayoutChanged(layout) },
                        enabled = !state.isSaving,
                        label = { Text(layout.label) }
                    )
                }
            }

            for (index in 0..2) {
                if (state.panels[index].file == null) continue
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = PANEL_LABELS[index],
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    LabeledSlider(
                        label = "縦位置",
                        value = state.panels[index].adjust.offsetY,
                        valueRange = -1f..1f,
                        valueText = "${(state.panels[index].adjust.offsetY * 100).roundToInt()}",
                        enabled = !state.isSaving,
                        onValueChange = { onOffsetChanged(index, it) }
                    )
                    LabeledSlider(
                        label = "大きさ",
                        value = state.panels[index].adjust.zoom,
                        valueRange = 1f..2.5f,
                        valueText = "%.1fx".format(state.panels[index].adjust.zoom),
                        enabled = !state.isSaving,
                        onValueChange = { onZoomChanged(index, it) }
                    )
                }
            }

            // ---------- 音 ----------
            SectionLabel("おと")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "上", 1 to "中央", 2 to "下", -1 to "なし").forEach { (value, label) ->
                    FilterChip(
                        selected = state.audioPanelIndex == value,
                        onClick = { onAudioPanelChanged(value) },
                        enabled = !state.isSaving,
                        label = { Text(label) }
                    )
                }
            }

            // ---------- 書き出し ----------
            SectionLabel("かきだし")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1080 to "1080×1920", 720 to "720×1280").forEach { (value, label) ->
                    FilterChip(
                        selected = state.outputWidth == value,
                        onClick = { onOutputWidthChanged(value) },
                        enabled = !state.isSaving,
                        label = { Text(label) }
                    )
                }
            }

            Button(
                onClick = onCreateClicked,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = when {
                        state.isSaving -> "書き出し中…"
                        !state.isReady -> "動画をあと${3 - state.filledCount}本えらんでください"
                        else -> "作成して保存（%.1f秒）".format(state.outputSeconds)
                    }
                )
            }

            if (state.isSaving) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = state.savingStepMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "一番短い素材の長さに揃います。音は選んだパネルのものだけが入ります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }

    if (state.errorMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = { TextButton(onClick = onDismissError) { Text("閉じる") } },
            title = { Text("エラー") },
            text = { Text(state.errorMessage) }
        )
    }

    if (state.successMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissSuccess,
            confirmButton = { TextButton(onClick = onDismissSuccess) { Text("OK") } },
            title = { Text("保存しました") },
            text = { Text(state.successMessage) }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 出力フレームのどこの帯かを示す小さな図。 */
@Composable
private fun BandGlyph(index: Int) {
    Column(
        modifier = Modifier
            .size(width = 16.dp, height = 28.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        for (band in 0..2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (band == 1) 10.dp else 9.dp)
                    .background(
                        if (band == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun PanelSlotRow(
    index: Int,
    slot: PanelSlot,
    isLoading: Boolean,
    enabled: Boolean,
    onPickVideoClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BandGlyph(index)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = PANEL_LABELS[index],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when {
                    isLoading -> "読み込み中…"
                    slot.file == null -> "まだ選んでいません"
                    else -> "${slot.displayName}  %.1fs".format(slot.durationSeconds)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            OutlinedButton(
                onClick = { onPickVideoClick(index) },
                enabled = enabled,
                modifier = Modifier.width(84.dp)
            ) {
                Text(if (slot.file == null) "選ぶ" else "変更")
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
    }
}
