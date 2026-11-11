package com.adultaristocrat.cinema

import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun AndroidVideoPlayer(request: PlaybackRequest, events: PlayerEvents, modifier: Modifier) {
    val context = LocalContext.current

    val player = remember(request.key) {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(context, renderers)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(request.url), request.startPositionMs)
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        var reportedTrackProblem = false
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) events.onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                events.onError("${error.errorCodeName}: ${error.message ?: ""}")
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (reportedTrackProblem) return
                // Если устройство не умеет декодировать звук или видео — переходим на перекодирование
                val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                val audioBad = audio.isNotEmpty() && audio.none { it.isSupported }
                val videoBad = video.isNotEmpty() && video.none { it.isSupported }
                if (audioBad || videoBad) {
                    reportedTrackProblem = true
                    events.onError(if (videoBad) "Видеокодек не поддерживается" else "Аудиокодек не поддерживается")
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(1000)
            val st = player.playbackState
            if (st == Player.STATE_READY || st == Player.STATE_BUFFERING) {
                events.onProgress(player.currentPosition, player.duration.coerceAtLeast(0), !player.playWhenReady)
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.BLACK)
                this.player = player
                useController = true
                controllerShowTimeoutMs = 4000
                setShowSubtitleButton(true)
                setShowNextButton(false)
                setShowPreviousButton(false)
                keepScreenOn = true
                isFocusable = true
                isFocusableInTouchMode = true
                post { requestFocus() }
            }
        },
        update = { view -> if (view.player !== player) view.player = player },
        modifier = modifier,
    )
}
