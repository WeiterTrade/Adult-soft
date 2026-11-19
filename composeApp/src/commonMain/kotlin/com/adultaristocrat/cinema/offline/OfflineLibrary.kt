package com.adultaristocrat.cinema.offline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adultaristocrat.cinema.Platform
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.ItemFilter
import com.adultaristocrat.cinema.api.ItemsResult
import com.adultaristocrat.cinema.api.JellyfinApi
import com.adultaristocrat.cinema.api.PersonDto
import com.adultaristocrat.cinema.api.UserData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Офлайн-библиотека: фильмы из сетевых папок (NAS / компьютер в локальной сети).
 * Хранится в файле offline.json в личной папке приложения.
 */
class OfflineLibrary(private val platform: Platform, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val engine: OfflineEngine? get() = platform.offlineEngine
    val supported: Boolean get() = engine != null

    var state by mutableStateOf(load())
        private set

    var scanning by mutableStateOf(false)
        private set
    var scanStatus by mutableStateOf<String?>(null)
        private set
    private var scanJob: Job? = null

    val enabled: Boolean get() = state.enabled && supported
    val hasMovies: Boolean get() = enabled && state.movies.isNotEmpty()

    init {
        engine?.setSources(state.sources)
    }

    private fun load(): OfflineState =
        platform.readFile(FILE)?.let { runCatching { json.decodeFromString(OfflineState.serializer(), it) }.getOrNull() }
            ?: OfflineState()

    private fun save() {
        val text = json.encodeToString(OfflineState.serializer(), state)
        platform.writeFile(FILE, text)
    }

    private fun update(s: OfflineState) {
        state = s
        save()
    }

    fun setEnabled(v: Boolean) = update(state.copy(enabled = v))

    fun setForceOffline(v: Boolean) = update(state.copy(forceOffline = v))

    val forceOffline: Boolean get() = enabled && state.forceOffline

    fun addSource(src: OfflineSource) {
        if (state.sources.any { it.smbUrl.equals(src.smbUrl, ignoreCase = true) }) return
        update(state.copy(sources = state.sources + src))
        engine?.setSources(state.sources)
    }

    fun removeSource(src: OfflineSource) {
        val sources = state.sources - src
        val movies = state.movies.filterNot { it.video.startsWith(src.smbUrl, ignoreCase = true) }
        update(state.copy(sources = sources, movies = movies))
        engine?.setSources(sources)
    }

    fun scan(onDone: (Int) -> Unit = {}) {
        val e = engine ?: return
        if (scanning) return
        scanning = true
        scanStatus = "Подготовка…"
        scanJob = scope.launch {
            try {
                val found = e.scan(state.sources) { folder, n ->
                    scope.launch { scanStatus = "Найдено: $n · $folder" }
                }
                // Новые сверху: последние найденные получают больший номер
                val keepPositions = state.positions.filterKeys { k -> found.any { it.id == k } }
                scope.launch { scanStatus = "Биографии…" }
                val bios = runCatching { e.scanBios(state.sources) }.getOrDefault(emptyList())
                val bioMap = HashMap<String, OfflineBio>()
                bios.forEach { b -> (listOf(b.name) + b.aliases).forEach { bioMap[normName(it)] = b } }
                update(state.copy(movies = found, positions = keepPositions, bios = bioMap, lastScan = "${found.size} фильмов"))
                scanStatus = "Готово: ${found.size} фильмов" + if (bios.isNotEmpty()) ", биографий: ${bios.size}" else ""
                onDone(found.size)
            } catch (c: CancellationException) {
                scanStatus = "Сканирование остановлено"
                throw c
            } catch (ex: Exception) {
                scanStatus = "Ошибка: ${ex.message ?: ex}"
            } finally {
                scanning = false
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    // ---------------- Просмотр ----------------

    fun savePosition(id: String, positionMs: Long, durationMs: Long) {
        val watched = durationMs > 0 && positionMs > durationMs * 0.92
        val v = if (watched) -1L else positionMs
        state = state.copy(positions = state.positions + (id to v))
        // Пишем на диск не на каждый тик, а примерно раз в 10 секунд — это вызывается раз в секунду
        if (++saveTick % 10 == 0 || watched) save()
    }

    private var saveTick = 0

    fun flush() = save()

    fun markWatched(id: String) = update(state.copy(positions = state.positions + (id to -1L)))

    fun positionOf(id: String): Long = state.positions[id]?.takeIf { it > 0 } ?: 0L

    fun lastResumable(): BaseItem? {
        val ids = state.positions.filterValues { it > 0 }.keys
        return items().firstOrNull { it.id in ids }
    }

    // ---------------- Каталог ----------------

    private var cachedFor: OfflineState? = null
    private var cached: List<BaseItem> = emptyList()

    /** Все фильмы как BaseItem (для общих экранов) — новые сверху */
    fun items(): List<BaseItem> {
        val s = state
        if (cachedFor !== s) {
            cached = s.movies.sortedByDescending { it.order }.map { toItem(it, s) }
            cachedFor = s
        }
        return cached
    }

    fun find(id: String): BaseItem? = items().firstOrNull { it.id == id }

    // ---------------- Актёры и режиссёры ----------------

    fun normName(s: String): String =
        s.replace('_', ' ').replace('.', ' ').replace(Regex("\\s+"), " ").trim().lowercase()

    /** Фильмы с этим человеком (актёр или режиссёр) */
    fun moviesWith(personId: String): List<BaseItem> = items().filter { m -> m.people.any { it.id == personId } }

    /** Фото человека из папок .actors (или стандартная картинка режиссёра) */
    fun personPhoto(personId: String): String? = items().asSequence()
        .flatMap { it.people.asSequence() }
        .filter { it.id == personId }
        .sortedBy { if (it.type == "Actor") 0 else 1 }
        .firstNotNullOfOrNull { it.localThumb }

    /** Карточка человека для страницы актёра: биография из .bio */
    fun person(personId: String, name: String): BaseItem {
        val b = state.bios[normName(name)]
        return BaseItem(
            id = personId,
            name = b?.name?.takeIf { it.isNotBlank() } ?: name,
            originalTitle = b?.realName,
            type = "Person",
            overview = b?.text,
            premiereDate = b?.birthDate,
            endDate = b?.deathDate,
            productionLocations = listOfNotNull(b?.birthPlace),
            localFacts = b?.facts.orEmpty().mapNotNull { if (it.size >= 2) it[0] to it[1] else null },
        )
    }

    fun query(filter: ItemFilter, search: String?, start: Int, limit: Int): ItemsResult {
        var list = items()
        if (filter.genres.isNotEmpty()) list = list.filter { m -> m.genres.any { it in filter.genres } }
        if (filter.years.isNotEmpty()) list = list.filter { it.productionYear in filter.years }
        if (filter.countries.isNotEmpty()) list = list.filter { m ->
            m.productionLocations.any { com.adultaristocrat.cinema.ui.Countries.key(it) in filter.countries }
        }
        if (!search.isNullOrBlank()) {
            val q = search.trim().lowercase()
            list = list.filter { m ->
                (m.name ?: "").lowercase().contains(q) || (m.originalTitle ?: "").lowercase().contains(q) ||
                    m.people.any { (it.name ?: "").lowercase().contains(q) }
            }
        }
        return ItemsResult(list.drop(start).take(limit), list.size)
    }

    private fun toItem(m: OfflineMovie, s: OfflineState): BaseItem {
        val e = engine
        fun url(p: String?) = if (p == null || e == null) null else e.fileUrl(p)
        val pos = s.positions[m.id]
        val runtimeTicks = m.runtimeMin?.let { it * 60L * 1000L * JellyfinApi.TICKS_PER_MS }
        val actorPhotos = m.actors.associate { it.name.trim().lowercase() to url(it.thumb) }
        val people = m.actors.map { a ->
            PersonDto(id = "offp:" + a.name, name = a.name, role = a.role, type = "Actor", localThumb = url(a.thumb))
        } + m.directors.map { d ->
            // Режиссёр, который снимается и сам, — с фото актрисы; иначе стандартная картинка
            val photo = actorPhotos[d.trim().lowercase()] ?: e?.directorPhotoUrl()
            PersonDto(id = "offp:" + d, name = d, type = "Director", localThumb = photo)
        }
        // Кадр для сцены: чуть позже начала (в самом начале часто чёрный экран или заставка)
        val chapterThumbs = m.chapters.mapIndexed { i, c ->
            val next = m.chapters.getOrNull(i + 1)?.startMs ?: (c.startMs + 120_000)
            val at = c.startMs + ((next - c.startMs) / 4).coerceIn(2_000, 20_000)
            e?.sceneThumbUrl(m.video, at)
        }
        return BaseItem(
            id = m.id,
            name = m.title,
            originalTitle = m.originalTitle,
            type = "Movie",
            overview = m.plot,
            taglines = listOfNotNull(m.tagline),
            productionYear = m.year,
            premiereDate = m.premiered,
            communityRating = m.rating,
            officialRating = m.mpaa,
            genres = m.genres,
            productionLocations = m.countries,
            runTimeTicks = runtimeTicks,
            userData = UserData(
                playbackPositionTicks = (pos?.takeIf { it > 0 } ?: 0L) * JellyfinApi.TICKS_PER_MS,
                played = pos == -1L,
                playedPercentage = if (pos != null && pos > 0 && m.runtimeMin != null && m.runtimeMin > 0)
                    (pos / 600.0 / m.runtimeMin).coerceIn(0.0, 100.0) else null,
            ),
            people = people,
            chapters = m.chapters.map {
                com.adultaristocrat.cinema.api.ChapterDto(startPositionTicks = it.startMs * JellyfinApi.TICKS_PER_MS, name = it.name)
            },
            localPoster = url(m.poster),
            localBack = url(m.back),
            localChapterThumbs = chapterThumbs,
            localFanart = url(m.fanart),
            localVideo = url(m.video),
        )
    }

    companion object {
        private const val FILE = "offline.json"
    }
}
