package com.adultaristocrat.cinema.api

import com.adultaristocrat.cinema.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Небольшой клиент Jellyfin REST API.
 * Используются маршруты, которые есть и в 10.8, и в 10.9–10.11.
 */
class JellyfinApi(
    private val deviceName: String,
    private val deviceId: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    private val http = HttpClient {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    var baseUrl: String = ""
        private set
    var token: String? = null
        private set
    var userId: String? = null
        private set

    fun configure(server: String, token: String?, userId: String?) {
        this.baseUrl = normalizeServer(server)
        this.token = token
        this.userId = userId
    }

    private fun safe(s: String) = s.replace("\"", "'").replace(",", " ")

    private fun authHeader(): String {
        val sb = StringBuilder()
        sb.append("MediaBrowser Client=\"${safe(AppConfig.APP_NAME)}\", ")
        sb.append("Device=\"${safe(deviceName)}\", ")
        sb.append("DeviceId=\"${safe(deviceId)}\", ")
        sb.append("Version=\"${AppConfig.VERSION}\"")
        token?.let { sb.append(", Token=\"$it\"") }
        return sb.toString()
    }

    private fun HttpRequestBuilder.auth(params: Map<String, Any?>) {
        header(HttpHeaders.Authorization, authHeader())
        header("X-Emby-Authorization", authHeader())
        params.forEach { (k, v) -> if (v != null && v.toString().isNotEmpty()) parameter(k, v) }
    }

    private suspend inline fun <reified T> getJson(path: String, params: Map<String, Any?> = emptyMap()): T =
        http.get(baseUrl + path) { auth(params) }.body()

    private suspend fun postEmpty(path: String, params: Map<String, Any?> = emptyMap()) {
        http.post(baseUrl + path) { auth(params) }
    }

    private suspend fun deleteEmpty(path: String, params: Map<String, Any?> = emptyMap()) {
        http.delete(baseUrl + path) { auth(params) }
    }

    // ---------------- Вход ----------------

    suspend fun publicInfo(server: String): PublicSystemInfo {
        val url = normalizeServer(server)
        return http.get("$url/System/Info/Public").body()
    }

    suspend fun login(server: String, username: String, password: String): AuthResult {
        baseUrl = normalizeServer(server)
        token = null
        val result: AuthResult = http.post("$baseUrl/Users/AuthenticateByName") {
            auth(emptyMap())
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(username, password))
        }.body()
        token = result.accessToken
        userId = result.user?.id
        return result
    }

    // ---------------- Быстрое подключение (Quick Connect) ----------------

    suspend fun quickConnectEnabled(server: String): Boolean {
        val url = normalizeServer(server)
        return runCatching { http.get("$url/QuickConnect/Enabled") { auth(emptyMap()) }.body<Boolean>() }.getOrDefault(false)
    }

    /** Получить код для ввода на другом устройстве */
    suspend fun quickConnectInitiate(server: String): QuickConnectResult {
        baseUrl = normalizeServer(server)
        token = null
        return runCatching { http.post("$baseUrl/QuickConnect/Initiate") { auth(emptyMap()) }.body<QuickConnectResult>() }
            .getOrElse { http.get("$baseUrl/QuickConnect/Initiate") { auth(emptyMap()) }.body() }
    }

    suspend fun quickConnectState(secret: String): QuickConnectResult =
        http.get("$baseUrl/QuickConnect/Connect") { auth(mapOf("secret" to secret)) }.body()

    suspend fun quickConnectAuthenticate(secret: String): AuthResult {
        val result: AuthResult = http.post("$baseUrl/Users/AuthenticateWithQuickConnect") {
            auth(emptyMap())
            contentType(ContentType.Application.Json)
            setBody(mapOf("Secret" to secret))
        }.body()
        token = result.accessToken
        userId = result.user?.id
        return result
    }

    /** Подтвердить вход другого устройства по коду (из настроек этого приложения) */
    suspend fun quickConnectAuthorize(code: String) {
        http.post("$baseUrl/QuickConnect/Authorize") { auth(mapOf("code" to code.trim(), "userId" to userId)) }
    }

    suspend fun logout() {
        runCatching { postEmpty("/Sessions/Logout") }
        token = null
        userId = null
    }

    // ---------------- Каталог ----------------

    suspend fun views(): List<BaseItem> {
        val uid = userId ?: return emptyList()
        return runCatching { getJson<ItemsResult>("/UserViews", mapOf("userId" to uid)).items }
            .getOrElse { getJson<ItemsResult>("/Users/$uid/Views").items }
            .filter { it.collectionType == null || it.collectionType in setOf("movies", "mixed", "homevideos") || (AppConfig.SHOW_SERIES && it.collectionType == "tvshows") }
    }

    suspend fun items(
        parentId: String? = null,
        types: String? = AppConfig.CONTENT_TYPES,
        sortBy: String = "DateCreated,SortName",
        sortOrder: String = "Descending",
        filters: String? = null,
        genres: Set<String> = emptySet(),
        years: Set<Int> = emptySet(),
        search: String? = null,
        startIndex: Int = 0,
        limit: Int = AppConfig.PAGE_SIZE,
        recursive: Boolean = true,
        ids: String? = null,
        fields: String = CARD_FIELDS,
    ): ItemsResult = getJson(
        "/Items",
        mapOf(
            "userId" to userId,
            "ParentId" to parentId,
            "IncludeItemTypes" to types,
            "Recursive" to recursive,
            "SortBy" to sortBy,
            "SortOrder" to sortOrder,
            "Filters" to filters,
            "Genres" to genres.takeIf { it.isNotEmpty() }?.joinToString("|"),
            "Years" to years.takeIf { it.isNotEmpty() }?.joinToString(","),
            "SearchTerm" to search,
            "StartIndex" to startIndex,
            "Limit" to limit,
            "Ids" to ids,
            "Fields" to fields,
            "EnableImageTypes" to "Primary,Backdrop,Thumb",
            "ImageTypeLimit" to 1,
            "EnableTotalRecordCount" to true,
        )
    )

    suspend fun item(id: String): BaseItem =
        items(ids = id, types = null, recursive = false, sortBy = "SortName", sortOrder = "Ascending", fields = DETAIL_FIELDS, limit = 1)
            .items.firstOrNull() ?: error("Элемент не найден")

    suspend fun resume(limit: Int = AppConfig.PAGE_SIZE, startIndex: Int = 0): ItemsResult = items(
        types = AppConfig.PLAYABLE_TYPES, filters = "IsResumable", sortBy = "DatePlayed", sortOrder = "Descending",
        limit = limit, startIndex = startIndex,
    )

    suspend fun nextUp(seriesId: String? = null, limit: Int = 30): List<BaseItem> =
        getJson<ItemsResult>(
            "/Shows/NextUp",
            mapOf("userId" to userId, "SeriesId" to seriesId, "Limit" to limit, "Fields" to CARD_FIELDS)
        ).items

    suspend fun seasons(seriesId: String): List<BaseItem> =
        getJson<ItemsResult>("/Shows/$seriesId/Seasons", mapOf("userId" to userId, "Fields" to CARD_FIELDS)).items

    suspend fun episodes(seriesId: String, seasonId: String? = null, startItemId: String? = null, limit: Int? = null): List<BaseItem> =
        getJson<ItemsResult>(
            "/Shows/$seriesId/Episodes",
            mapOf(
                "userId" to userId, "SeasonId" to seasonId, "StartItemId" to startItemId,
                "Limit" to limit, "Fields" to "Overview,PrimaryImageAspectRatio",
            )
        ).items

    suspend fun nextEpisodeAfter(episode: BaseItem): BaseItem? {
        val sid = episode.seriesId ?: return null
        return episodes(sid, startItemId = episode.id, limit = 2).getOrNull(1)
    }

    suspend fun genres(): List<String> =
        getJson<ItemsResult>(
            "/Genres",
            mapOf("userId" to userId, "IncludeItemTypes" to AppConfig.CONTENT_TYPES, "Recursive" to true, "SortBy" to "SortName")
        ).items.mapNotNull { it.name }

    suspend fun setFavorite(itemId: String, favorite: Boolean) {
        val uid = userId ?: return
        runCatching {
            if (favorite) postEmpty("/UserFavoriteItems/$itemId", mapOf("userId" to uid))
            else deleteEmpty("/UserFavoriteItems/$itemId", mapOf("userId" to uid))
        }.getOrElse {
            if (favorite) postEmpty("/Users/$uid/FavoriteItems/$itemId") else deleteEmpty("/Users/$uid/FavoriteItems/$itemId")
        }
    }

    suspend fun setPlayed(itemId: String, played: Boolean) {
        val uid = userId ?: return
        runCatching {
            if (played) postEmpty("/UserPlayedItems/$itemId", mapOf("userId" to uid))
            else deleteEmpty("/UserPlayedItems/$itemId", mapOf("userId" to uid))
        }.getOrElse {
            if (played) postEmpty("/Users/$uid/PlayedItems/$itemId") else deleteEmpty("/Users/$uid/PlayedItems/$itemId")
        }
    }

    // ---------------- Воспроизведение ----------------

    suspend fun playbackInfo(itemId: String): PlaybackInfoResponse =
        http.post("$baseUrl/Items/$itemId/PlaybackInfo") {
            auth(mapOf("userId" to userId))
            contentType(ContentType.Application.Json)
            setBody(mapOf("UserId" to (userId ?: "")))
        }.body()

    /** Прямое воспроизведение файла без перекодирования */
    fun directStreamUrl(itemId: String, mediaSourceId: String?, playSessionId: String?): String =
        "$baseUrl/Videos/$itemId/stream?static=true" +
            q("mediaSourceId", mediaSourceId) + q("PlaySessionId", playSessionId) +
            q("DeviceId", deviceId) + q("api_key", token)

    /**
     * HLS без перекодирования видео (только «переупаковка», как делает веб-версия).
     * Нужен, когда файл нельзя перематывать напрямую: перемотка и сцены работают как в браузере.
     * Звук приводится к AAC, если исходный кодек устройство может не потянуть.
     */
    fun remuxUrl(itemId: String, mediaSourceId: String?, playSessionId: String?): String =
        "$baseUrl/Videos/$itemId/master.m3u8?VideoCodec=h264,hevc,vp9,av1&AudioCodec=aac,mp3,opus,flac" +
            "&AllowVideoStreamCopy=true&AllowAudioStreamCopy=true&SegmentContainer=mp4&SegmentLength=3&MinSegments=1" +
            "&TranscodingMaxAudioChannels=6&MaxAudioChannels=6&MaxStreamingBitrate=200000000" +
            q("MediaSourceId", mediaSourceId) + q("PlaySessionId", playSessionId) +
            q("DeviceId", deviceId) + q("api_key", token)

    /** HLS с перекодированием в H.264 + AAC (если устройство не тянет оригинал) */
    fun transcodeUrl(itemId: String, mediaSourceId: String?, playSessionId: String?, maxBitrate: Int = 20_000_000): String =
        "$baseUrl/Videos/$itemId/master.m3u8?VideoCodec=h264&AudioCodec=aac,mp3" +
            "&MaxAudioChannels=2&TranscodingMaxAudioChannels=2&AudioBitrate=192000" +
            "&VideoBitrate=$maxBitrate&MaxStreamingBitrate=$maxBitrate&SegmentContainer=ts&BreakOnNonKeyFrames=true" +
            q("MediaSourceId", mediaSourceId) + q("PlaySessionId", playSessionId) +
            q("DeviceId", deviceId) + q("api_key", token)

    suspend fun reportStart(r: PlaybackReport) = runCatching { postReport("/Sessions/Playing", r) }
    suspend fun reportProgress(r: PlaybackReport) = runCatching { postReport("/Sessions/Playing/Progress", r) }
    suspend fun reportStopped(r: PlaybackReport) = runCatching { postReport("/Sessions/Playing/Stopped", r) }

    private suspend fun postReport(path: String, r: PlaybackReport) {
        http.post(baseUrl + path) {
            auth(emptyMap())
            contentType(ContentType.Application.Json)
            setBody(r)
        }
    }

    // ---------------- Картинки ----------------

    fun posterUrl(item: BaseItem, maxHeight: Int = 480): String? = when {
        item.isEpisode && item.seriesId != null && item.seriesPrimaryImageTag != null ->
            "$baseUrl/Items/${item.seriesId}/Images/Primary?tag=${item.seriesPrimaryImageTag}&maxHeight=$maxHeight&quality=90"
        item.imageTags["Primary"] != null ->
            "$baseUrl/Items/${item.id}/Images/Primary?tag=${item.imageTags["Primary"]}&maxHeight=$maxHeight&quality=90"
        else -> null
    }

    fun primaryUrl(item: BaseItem, maxWidth: Int = 600): String? =
        item.imageTags["Primary"]?.let { "$baseUrl/Items/${item.id}/Images/Primary?tag=$it&maxWidth=$maxWidth&quality=90" }

    fun backdropUrl(item: BaseItem, maxWidth: Int = 1280): String? = when {
        item.backdropImageTags.isNotEmpty() ->
            "$baseUrl/Items/${item.id}/Images/Backdrop/0?tag=${item.backdropImageTags[0]}&maxWidth=$maxWidth&quality=85"
        item.parentBackdropItemId != null && item.parentBackdropImageTags.isNotEmpty() ->
            "$baseUrl/Items/${item.parentBackdropItemId}/Images/Backdrop/0?tag=${item.parentBackdropImageTags[0]}&maxWidth=$maxWidth&quality=85"
        item.imageTags["Thumb"] != null ->
            "$baseUrl/Items/${item.id}/Images/Thumb?tag=${item.imageTags["Thumb"]}&maxWidth=$maxWidth&quality=85"
        else -> primaryUrl(item, maxWidth)
    }

    fun chapterImageUrl(itemId: String, index: Int, tag: String?, maxWidth: Int = 480): String? =
        tag?.let { "$baseUrl/Items/$itemId/Images/Chapter/$index?tag=$it&maxWidth=$maxWidth&quality=85" }

    /** Сцены фильма (главы MKV), с картинками, если сервер их извлёк */
    fun chaptersOf(item: BaseItem): List<com.adultaristocrat.cinema.Chapter> =
        item.chapters.mapIndexed { i, c ->
            com.adultaristocrat.cinema.Chapter(
                name = c.name?.takeIf { it.isNotBlank() } ?: "Сцена ${i + 1}",
                startMs = c.startPositionTicks / TICKS_PER_MS,
                imageUrl = chapterImageUrl(item.id, i, c.imageTag),
            )
        }

    fun personUrl(p: PersonDto, maxHeight: Int = 300): String? =
        p.primaryImageTag?.let { "$baseUrl/Items/${p.id}/Images/Primary?tag=$it&maxHeight=$maxHeight&quality=90" }

    fun userImageUrl(user: UserDto): String? =
        user.primaryImageTag?.let { "$baseUrl/Users/${user.id}/Images/Primary?tag=$it&maxHeight=200" }

    private fun q(name: String, value: String?): String =
        if (value.isNullOrEmpty()) "" else "&$name=${value.encodeURLParameter()}"

    companion object {
        const val CARD_FIELDS = "PrimaryImageAspectRatio,Genres,ProductionLocations,Overview"
        const val DETAIL_FIELDS =
            "PrimaryImageAspectRatio,Genres,ProductionLocations,Overview,People,Taglines,RemoteTrailers,ProviderIds,OriginalTitle,MediaSources,MediaStreams,ChildCount,Chapters"

        const val TICKS_PER_MS = 10_000L

        fun normalizeServer(input: String): String {
            var s = input.trim().trimEnd('/')
            if (s.isEmpty()) return s
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            return s
        }
    }
}
