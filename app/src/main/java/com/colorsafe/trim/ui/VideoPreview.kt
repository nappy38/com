package com.colorsafe.trim.ui

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.colorsafe.trim.R

/**
 * 元動画の色味そのままでプレビューするためのMedia3 ExoPlayerビュー。
 * 一部端末(MIUI等)でSurfaceViewだと動画の回転メタデータが正しく反映されないことがあるため、
 * res/layout/view_player.xml でTextureViewを明示的に指定している。
 */
@Composable
fun VideoPreview(
    uri: Uri,
    modifier: Modifier = Modifier,
    seekToSeconds: Double? = null,
    onDurationReady: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val duration = player.duration
                if (duration != C.TIME_UNSET && duration > 0) {
                    onDurationReady(duration)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, seekToSeconds) {
        if (seekToSeconds != null) {
            exoPlayer.seekTo((seekToSeconds * 1000).toLong())
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                player = exoPlayer
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        }
    )
}
