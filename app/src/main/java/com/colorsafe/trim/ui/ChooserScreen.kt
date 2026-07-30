package com.colorsafe.trim.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 起動直後の選択画面。「動画のトリミング」か「写真の傾き補正」かを選ぶ。 */
@Composable
fun ChooserScreen(
    onPickVideoClick: () -> Unit,
    onPickPhotoClick: () -> Unit
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ColorSafe Trim",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "何をしますか？",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onPickVideoClick,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("動画をトリミング(色味を変えない)")
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPickPhotoClick,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("写真の傾きを補正")
            }
        }
    }
}
