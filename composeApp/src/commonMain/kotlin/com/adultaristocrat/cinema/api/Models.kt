package com.adultaristocrat.cinema.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String,
)

@Serializable
data class AuthResult(
    @SerialName("User") val user: UserDto? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
)

@Serializable
data class UserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

@Serializable
data class PublicSystemInfo(
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("Id") val id: String? = null,
)

@Serializable
data class ItemsResult(
    @SerialName("Items") val items: List<BaseItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)

@Serializable
data class UserData(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
)

@Serializable
data class PersonDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

@Serializable
data class TrailerDto(
    @SerialName("Url") val url: String? = null,
    @SerialName("Name") val name: String? = null,
)

@Serializable
data class ChapterDto(
    @SerialName("StartPositionTicks") val startPositionTicks: Long = 0,
    @SerialName("Name") val name: String? = null,
    @SerialName("ImageTag") val imageTag: String? = null,
)

@Serializable
data class BaseItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("CommunityRating") val communityRating: Float? = null,
    @SerialName("CriticRating") val criticRating: Float? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("ProductionLocations") val productionLocations: List<String> = emptyList(),
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String> = emptyList(),
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("UserData") val userData: UserData? = null,
    @SerialName("People") val people: List<PersonDto> = emptyList(),
    @SerialName("RemoteTrailers") val remoteTrailers: List<TrailerDto> = emptyList(),
    @SerialName("ProviderIds") val providerIds: Map<String, String> = emptyMap(),
    @SerialName("MediaSources") val mediaSources: List<MediaSource> = emptyList(),
    @SerialName("Chapters") val chapters: List<ChapterDto> = emptyList(),
) {
    val isSeries get() = type == "Series"
    val isEpisode get() = type == "Episode"
    val isMovie get() = type == "Movie"
    val isFolderLike get() = type == "BoxSet" || type == "CollectionFolder" || type == "Folder" || type == "UserView" || type == "Season"

    /** "S1E3" для серий */
    val episodeLabel: String?
        get() = if (isEpisode && parentIndexNumber != null && indexNumber != null) "S${parentIndexNumber}E${indexNumber}" else null

    /** "2026" или "2026-…" для идущих сериалов */
    val yearLabel: String?
        get() {
            val y = productionYear ?: return null
            if (isSeries) {
                if (status == "Continuing") return "$y-…"
                val end = endDate?.take(4)?.toIntOrNull()
                if (end != null && end != y) return "$y-$end"
            }
            return y.toString()
        }

    val runtimeMinutes: Int? get() = runTimeTicks?.let { (it / 600_000_000L).toInt() }.takeIf { (it ?: 0) > 0 }
}

@Serializable
data class MediaStream(
    @SerialName("Index") val index: Int = 0,
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("IsExternal") val isExternal: Boolean = false,
)

@Serializable
data class MediaSource(
    @SerialName("Id") val id: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStream> = emptyList(),
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
)

@Serializable
data class PlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<MediaSource> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null,
)

@Serializable
data class PlaybackReport(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("PlayMethod") val playMethod: String = "DirectPlay",
)

/** Параметры фильтра каталога */
data class ItemFilter(
    val types: Set<String> = emptySet(),        // Movie, Series
    val genres: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
) {
    val isActive get() = types.isNotEmpty() || genres.isNotEmpty() || years.isNotEmpty()
}
