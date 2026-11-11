package com.adultaristocrat.cinema

import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale

/** Управление ExoPlayer для общего интерфейса плеера */
@OptIn(UnstableApi::class)
private class ExoControl(
    private val player: ExoPlayer,
    private val onScale: (ScaleMode) -> Unit,
) : PlayerControl {
    override fun setScaleMode(mode: ScaleMode) = onScale(mode)

    override val positionMs: Long get() = player.currentPosition
    override val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
    override val isPlaying: Boolean get() = player.playWhenReady && player.playbackState != Player.STATE_ENDED
    override val isBuffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING

    override fun togglePause() {
        player.playWhenReady = !player.playWhenReady
    }

    override fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0))

    private fun options(type: Int): List<Pair<String, TrackOption>> {
        val result = mutableListOf<Pair<String, TrackOption>>()
        player.currentTracks.groups.forEachIndexed { gi, g ->
            if (g.type != type) return@forEachIndexed
            for (ti in 0 until g.length) {
                if (!g.isTrackSupported(ti)) continue
                val id = "$gi:$ti"
                result += id to TrackOption(id, label(g.getTrackFormat(ti), result.size + 1), g.isTrackSelected(ti))
            }
        }
        return result
    }

    private fun label(f: Format, n: Int): String {
        val parts = mutableListOf<String>()
        f.label?.takeIf { it.isNotBlank() }?.let { parts += it }
        f.language?.takeIf { it.isNotBlank() && it != "und" }?.let {
            val name = Locale(it).getDisplayLanguage(Locale("ru"))
            if (parts.none { p -> p.contains(name, ignoreCase = true) }) parts += name.replaceFirstChar { c -> c.uppercase() }
        }
        if (f.channelCount != Format.NO_VALUE && f.channelCount > 0) {
            parts += when (f.channelCount) { 1 -> "моно"; 2 -> "стерео"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount} кан." }
        }
        return if (parts.isEmpty()) "Дорожка $n" else parts.joinToString(" · ")
    }

    private fun select(id: String, type: Int) {
        val (gi, ti) = id.split(":").map { it.toInt() }
        val group = player.currentTracks.groups.getOrNull(gi) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti))
            .build()
    }

    override fun audioTracks(): List<TrackOption> = options(C.TRACK_TYPE_AUDIO).map { it.second }

    override fun selectAudio(id: String) = select(id, C.TRACK_TYPE_AUDIO)

    override fun subtitleTracks(): List<TrackOption> {
        val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val list = options(C.TRACK_TYPE_TEXT).map { it.second }
        val anySelected = !disabled && list.any { it.selected }
        return listOf(TrackOption("off", "Выключены", !anySelected)) +
            list.map { if (disabled) it.copy(selected = false) else it }
    }

    override fun selectSubtitle(id: String) {
        if (id == "off") {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else select(id, C.TRACK_TYPE_TEXT)
    }
}

@OptIn(UnstableApi::class)
@Composable
fun AndroidVideoSurface(
    request: PlaybackRequest,
    events: PlayerEvents,
    onControl: (PlayerControl?) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current

    var scale by remember { mutableStateOf(ScaleMode.FIT) }

    val player = remember(request.key) {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        // Короткий стартовый буфер — видео начинается быстрее
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 2_000)
            .build()
        ExoPlayer.Builder(context, renderers)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(request.url), request.startPositionMs)
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        var reportedTrackProblem = false
        var checkedSeekable = false
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) events.onEnded()
                if (playbackState == Player.STATE_READY && !checkedSeekable) {
                    checkedSeekable = true
                    // MKV без индекса (Cues): ExoPlayer при перемотке начинает сначала.
                    // Переходим на HLS-переупаковку сервером — как в браузере.
                    if (!player.isCurrentMediaItemSeekable) events.onUnseekable()
                }
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
        onControl(ExoControl(player) { scale = it })
        onDispose {
            onControl(null)
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.BLACK)
                useController = false // кнопки рисует общий интерфейс
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepScreenOn = true
                isFocusable = false
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = when (scale) {
                ScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                ScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                ScaleMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
        },
        modifier = modifier,
    )
}
