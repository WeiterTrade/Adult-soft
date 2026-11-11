package com.adultaristocrat.cinema

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily

/** Простое хранилище ключ-значение (SharedPreferences / java.util.prefs) */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/** Что именно воспроизводить */
data class PlaybackRequest(
    val url: String,
    val startPositionMs: Long,
    val title: String,
    /** Меняется при каждой новой загрузке (например, переход на перекодирование) */
    val key: String,
)

/** События от плеера платформы в общий код */
interface PlayerEvents {
    fun onProgress(positionMs: Long, durationMs: Long, isPaused: Boolean)
    fun onEnded()
    fun onError(message: String)
    fun onBack()
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

    @Composable
    fun VideoPlayer(request: PlaybackRequest, events: PlayerEvents, modifier: Modifier)
}
