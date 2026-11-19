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
    /** Папка на этом компьютере (например, скачанная библиотека). Тогда host/share не используются */
    val localPath: String = "",
) {
    val isLocal: Boolean get() = localPath.isNotBlank()
    val title: String get() = if (isLocal) localPath
        else "\\\\$host\\$share" + (if (path.isNotBlank()) "\\" + path.trim('/').replace('/', '\\') else "")
    val smbUrl: String get() = if (isLocal) "local:" + localPath
        else "smb://$host/$share/" + (if (path.isNotBlank()) path.trim('/') + "/" else "")
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
    /** Задняя обложка: «Название (год)-back.jpg» */
    val back: String? = null,
    /** Картинка диска: «Название (год)-disc.png» */
    val disc: String? = null,
    val fanart: String? = null,
    val actors: List<OfflineActor> = emptyList(),
    val directors: List<String> = emptyList(),
    val chapters: List<OfflineChapter> = emptyList(),
    /** Порядок добавления при сканировании (для «Новые») */
    val order: Int = 0,
)

/** Биография из папки .bio (файл «Имя_Фамилия.txt») */
@Serializable
data class OfflineBio(
    val name: String,
    val text: String? = null,
    val realName: String? = null,
    val aliases: List<String> = emptyList(),
    /** ISO: 1981-09-21 (или только год) */
    val birthDate: String? = null,
    val deathDate: String? = null,
    val birthPlace: String? = null,
    /** Остальные пункты «Краткой справки»: рост, жанры, достижения… */
    val facts: List<List<String>> = emptyList(),
)

@Serializable
data class OfflineState(
    val enabled: Boolean = false,
    /** Всегда офлайн: не подключаться к серверу, даже если он доступен */
    val forceOffline: Boolean = false,
    val sources: List<OfflineSource> = emptyList(),
    val movies: List<OfflineMovie> = emptyList(),
    /** id → позиция в мс; отрицательное — просмотрено */
    val positions: Map<String, Long> = emptyMap(),
    /** Биографии: нормализованное имя → биография */
    val bios: Map<String, OfflineBio> = emptyMap(),
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

    /** Биографии из папок .bio внутри источников */
    suspend fun scanBios(sources: List<OfflineSource>): List<OfflineBio> = emptyList()

    /** Сообщить пароли к папкам (для воспроизведения и картинок) */
    fun setSources(sources: List<OfflineSource>)

    /** http-адрес файла через встроенный локальный сервер (плееры и картинки) */
    fun fileUrl(smbPath: String): String

    /** Картинка сцены: кадр из видео в момент [ms] (делается один раз и запоминается) */
    fun sceneThumbUrl(videoSmbPath: String, ms: Long): String? = null

    /** Стандартная картинка для режиссёра без фото */
    fun directorPhotoUrl(): String? = null
}
