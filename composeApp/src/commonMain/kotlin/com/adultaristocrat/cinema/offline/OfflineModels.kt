package com.adultaristocrat.cinema.offline

import kotlinx.serialization.Serializable

/** Сетевая папка (SMB): \\host\share\path */
@Serializable
data class OfflineSource(
    val host: String,
    val share: String,
    val path: String = "",
    val user: String = "",
    val password: String = "",
) {
    val title: String get() = "\\\\$host\\$share" + (if (path.isNotBlank()) "\\" + path.trim('/').replace('/', '\\') else "")
    val smbUrl: String get() = "smb://$host/$share/" + (if (path.isNotBlank()) path.trim('/') + "/" else "")
}

/** Сцена (глава MKV), прочитанная прямо из файла */
@Serializable
data class OfflineChapter(val name: String, val startMs: Long)

@Serializable
data class OfflineActor(val name: String, val role: String? = null, val thumb: String? = null)

/** Фильм, найденный в папке (видео + .nfo + постер + фанарт + .actors) */
@Serializable
data class OfflineMovie(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val premiered: String? = null,
    val plot: String? = null,
    val tagline: String? = null,
    val genres: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val rating: Float? = null,
    val runtimeMin: Int? = null,
    val mpaa: String? = null,
    /** smb:// пути */
    val video: String,
    val poster: String? = null,
    val fanart: String? = null,
    val actors: List<OfflineActor> = emptyList(),
    val directors: List<String> = emptyList(),
    val chapters: List<OfflineChapter> = emptyList(),
    /** Порядок добавления при сканировании (для «Новые») */
    val order: Int = 0,
)

@Serializable
data class OfflineState(
    val enabled: Boolean = false,
    val sources: List<OfflineSource> = emptyList(),
    val movies: List<OfflineMovie> = emptyList(),
    /** id → позиция в мс; отрицательное — просмотрено */
    val positions: Map<String, Long> = emptyMap(),
    val lastScan: String? = null,
)

data class OfflineHost(val ip: String, val name: String? = null)

/** Работа с сетью и файлами (реализация общая для Android и ПК — jcifs-ng) */
interface OfflineEngine {
    /** Поиск компьютеров/NAS с открытыми общими папками в локальной сети */
    suspend fun discover(onFound: (OfflineHost) -> Unit)

    /** Общие папки на компьютере */
    suspend fun listShares(host: String, user: String, password: String): List<String>

    /** Подпапки внутри папки */
    suspend fun listDirs(source: OfflineSource): List<String>

    /** Рекурсивное сканирование папок: видео + nfo + картинки */
    suspend fun scan(sources: List<OfflineSource>, onProgress: (folder: String, found: Int) -> Unit): List<OfflineMovie>

    /** Сообщить пароли к папкам (для воспроизведения и картинок) */
    fun setSources(sources: List<OfflineSource>)

    /** http-адрес файла через встроенный локальный сервер (плееры и картинки) */
    fun fileUrl(smbPath: String): String
}
