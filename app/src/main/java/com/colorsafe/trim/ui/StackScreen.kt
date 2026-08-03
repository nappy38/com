package com.colorsafe.trim.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.colorsafe.trim.model.StackLayout
import kotlin.math.roundToInt

private val PANEL_LABELS = listOf("上のパネル", "中央のパネル", "下のパネル")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StackScreen(
    state: StackUiState,
    onPickVideoClick: (Int) -> Unit,
    onOffsetChanged: (Int, Float) -> Unit,
    onZoomChanged: (Int, Float) -> Unit,
    onSpeedChanged: (Int, Float) -> Unit,
    onLayoutChanged: (StackLayout) -> Unit,
    onAudioPanelChanged: (Int) -> Unit,
    onOutputWidthChanged: (Int) -> Unit,
    onTargetSecondsChanged: (Double?) -> Unit,
    onColorBoostChanged: (Boolean) -> Unit,
    onPreviewPositionChanged: (Float) -> Unit,
    onCreateClicked: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit
) {
    // 背景は MainActivity の水彩に任せるので、ここは透かす
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ScreenTitle(
                title = "3分割",
                subtitle = "3本の動画を、縦に積む"
            )

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
                            .height(380.dp)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(14.dp)
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .height(380.dp)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.filledCount == 0) {
                                "動画を選ぶと\nここに出ます"
                            } else {
                                "プレビューを\n作れませんでした"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.filledCount > 0) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "はやさ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(56.dp)
                        )
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                0.5f to "0.5x",
                                0.7f to "0.7x",
                                1f to "1x",
                                1.2f to "1.2x"
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = state.panels[index].adjust.speed == value,
                                    onClick = { onSpeedChanged(index, value) },
                                    enabled = !state.isSaving,
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
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

            // ---------- 色味 ----------
            SectionLabel("いろみ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.colorBoost,
                    onClick = { onColorBoostChanged(false) },
                    enabled = !state.isSaving,
                    label = { Text("そのまま") }
                )
                FilterChip(
                    selected = state.colorBoost,
                    onClick = { onColorBoostChanged(true) },
                    enabled = !state.isSaving,
                    label = { Text("こく") }
                )
            }

            // ---------- 長さ ----------
            SectionLabel("ながさ")
            // 選択肢が横幅に収まらないので折り返す
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf<Pair<Double?, String>>(
                    null to "そのまま",
                    4.0 to "4秒",
                    5.0 to "5秒",
                    6.0 to "6秒"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = state.targetSeconds == value,
                        onClick = { onTargetSecondsChanged(value) },
                        enabled = !state.isSaving,
                        label = { Text(label) }
                    )
                }
            }
            if (state.isPadded) {
                Text(
                    text = "一番短い素材が%.1f秒なので、足りない分は最後の絵で止めて伸ばします"
                        .format(state.sourceSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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
            text = {
                // 原因を知らせてもらう必要があるので、長押しでコピーできるようにする
                SelectionContainer {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
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
        color = MaterialTheme.colorScheme.primary
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
