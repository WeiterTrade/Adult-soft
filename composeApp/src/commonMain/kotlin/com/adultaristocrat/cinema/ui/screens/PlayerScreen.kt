package com.adultaristocrat.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.PlaybackRequest
import com.adultaristocrat.cinema.PlayerControl
import com.adultaristocrat.cinema.PlayerEvents
import com.adultaristocrat.cinema.ui.PlayerOverlay
import androidx.compose.runtime.remember
import com.adultaristocrat.cinema.Route
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.JellyfinApi
import com.adultaristocrat.cinema.api.PlaybackReport
import com.adultaristocrat.cinema.ui.LoadingBox
import com.adultaristocrat.cinema.ui.MessageBox
import kotlinx.coroutines.launch

class PlayerModel(private val state: AppState, private val route: Route.Player) : PlayerEvents {
    private val api = state.api
    var request by mutableStateOf<PlaybackRequest?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var item: BaseItem? = null
    private var mediaSourceId: String? = null
    private var playSessionId: String? = null
    private var transcoding = false
    private var lastPosMs = 0L
    private var lastPaused = false
    private var ticksSinceReport = 0
    private var stopped = false
    private var attempt = 0

    init { start() }

    private fun start() {
        state.scope.launch {
            try {
                val it = api.item(route.itemId)
                item = it
                val info = api.playbackInfo(it.id)
                val source = info.mediaSources.firstOrNull()
                mediaSourceId = source?.id ?: it.id
                playSessionId = info.playSessionId
                val startMs = route.startMs
                    ?: if (route.fromStart) 0L else (it.userData?.playbackPositionTicks ?: 0L) / JellyfinApi.TICKS_PER_MS
                lastPosMs = startMs
                transcoding = state.forceTranscode
                request = PlaybackRequest(
                    url = buildUrl(),
                    startPositionMs = startMs,
                    title = title(it),
                    key = "${it.id}-${attempt++}",
                    chapters = api.chaptersOf(it),
                )
                api.reportStart(report())
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
        }
    }

    private fun title(it: BaseItem): String =
        if (it.isEpisode) "${it.seriesName ?: ""} · ${it.episodeLabel ?: ""} ${it.name ?: ""}" else it.name ?: ""

    private fun buildUrl(): String {
        val id = item?.id ?: route.itemId
        return if (transcoding) api.transcodeUrl(id, mediaSourceId, playSessionId)
        else api.directStreamUrl(id, mediaSourceId, playSessionId)
    }

    private fun report() = PlaybackReport(
        itemId = item?.id ?: route.itemId,
        mediaSourceId = mediaSourceId,
        playSessionId = playSessionId,
        positionTicks = lastPosMs * JellyfinApi.TICKS_PER_MS,
        isPaused = lastPaused,
        playMethod = if (transcoding) "Transcode" else "DirectPlay",
    )

    override fun onProgress(positionMs: Long, durationMs: Long, isPaused: Boolean) {
        lastPosMs = positionMs
        val pauseChanged = isPaused != lastPaused
        lastPaused = isPaused
        ticksSinceReport++
        if (ticksSinceReport >= 10 || pauseChanged) {
            ticksSinceReport = 0
            val r = report()
            state.scope.launch { api.reportProgress(r) }
        }
    }

    override fun onEnded() {
        val it = item
        stop()
        if (it != null && it.isEpisode && state.autoNextEpisode) {
            state.scope.launch {
                val next = runCatching { api.nextEpisodeAfter(it) }.getOrNull()
                if (next != null) state.replaceTop(Route.Player(next.id, fromStart = true))
                else state.back()
            }
        } else {
            state.back()
        }
    }

    override fun onError(message: String) {
        val r = request
        if (!transcoding && r != null && !state.platform.isDesktop) {
            // Устройство не смогло воспроизвести оригинал — просим сервер перекодировать
            transcoding = true
            request = r.copy(url = buildUrl(), startPositionMs = lastPosMs, key = "${route.itemId}-${attempt++}")
        } else {
            error = "Ошибка воспроизведения: $message"
        }
    }

    override fun onBack() {
        state.back()
    }

    fun stop() {
        if (stopped || item == null) return
        stopped = true
        val r = report()
        state.scope.launch { api.reportStopped(r) }
    }
}

@Composable
fun PlayerScreen(state: AppState, route: Route.Player) {
    val model = state.model(route) { PlayerModel(state, route) }
    DisposableEffect(model) {
        state.platform.setPlayerMode(true)
        onDispose {
            state.platform.setPlayerMode(false)
            model.stop()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val req = model.request
        when {
            model.error != null -> MessageBox(model.error ?: "", actionText = "Назад") { state.back() }
            req == null -> LoadingBox()
            else -> key(req.key) {
                var control by remember { mutableStateOf<PlayerControl?>(null) }
                state.platform.VideoSurface(req, model, { control = it }, Modifier.fillMaxSize())
                PlayerOverlay(req, control, model, state.platform)
            }
        }
    }
}
