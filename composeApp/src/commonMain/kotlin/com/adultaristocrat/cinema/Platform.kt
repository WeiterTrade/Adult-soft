package com.adultaristocrat.cinema

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

/** Простое хранилище ключ-значение (SharedPreferences / java.util.prefs) */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/** Сцена (глава MKV) */
data class Chapter(val name: String, val startMs: Long, val imageUrl: String?)

/** Что именно воспроизводить */
data class PlaybackRequest(
    val url: String,
    val startPositionMs: Long,
    val title: String,
    /** Меняется при каждой новой загрузке (например, переход на перекодирование) */
    val key: String,
    val chapters: List<Chapter> = emptyList(),
)

/** События от плеера платформы в общий код */
interface PlayerEvents {
    fun onProgress(positionMs: Long, durationMs: Long, isPaused: Boolean)
    fun onEnded()
    fun onError(message: String)
    fun onBack()
    /** Файл нельзя перематывать напрямую (например, MKV без индекса) — нужен HLS */
    fun onUnseekable() {}
}

/** Дорожка звука или субтитров */
data class TrackOption(val id: String, val label: String, val selected: Boolean)

/** Управление плеером платформы из общего интерфейса */
interface PlayerControl {
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
    val isBuffering: Boolean
    fun togglePause()
    fun seekTo(ms: Long)
    fun audioTracks(): List<TrackOption>
    fun selectAudio(id: String)
    /** Первый вариант — «Выключены» с id = "off" */
    fun subtitleTracks(): List<TrackOption>
    fun selectSubtitle(id: String)
}

data class AppFonts(val body: FontFamily, val brand: FontFamily)

/** Всё, что отличается между Android и ПК */
interface Platform {
    val deviceName: String
    val deviceId: String
    /** Android TV (управление пультом) */
    val isTv: Boolean
    val isDesktop: Boolean
    val store: KeyValueStore
    val fonts: AppFonts
    val currentYear: Int

    fun openUrl(url: String)

    /** Полноэкранный режим для плеера */
    fun setPlayerMode(enabled: Boolean)

    /** Полный экран по кнопке (только ПК) */
    val canToggleFullscreen: Boolean get() = false
    val isFullscreen: Boolean get() = false
    fun toggleFullscreen() {}

    /**
     * Только картинка видео (без кнопок). Кнопки и сцены рисует общий код.
     * Когда плеер готов, платформа отдаёт управление через [onControl].
     */
    @Composable
    fun VideoSurface(
        request: PlaybackRequest,
        events: PlayerEvents,
        onControl: (PlayerControl?) -> Unit,
        modifier: Modifier,
    )
}
