package com.adultaristocrat.cinema.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.HttpStatusError
import com.adultaristocrat.cinema.LocalFileStore
import com.adultaristocrat.cinema.api.BaseItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Фильм в очереди: куда класть (раздел и папки как на сервере) */
data class DownloadEntry(
    val item: BaseItem,
    val libraryName: String,
    /** Папки внутри раздела, как на сервере: «Серия …/Фильм (2026)» */
    val relDir: String,
)

/**
 * Скачивание библиотеки на компьютер в формате офлайн-библиотеки (как у Kodi):
 * видео, .nfo, -poster/-fanart/-back.jpg, .actors/, а в корне — .bio/ с биографиями.
 * Работает в фоне: окно можно закрыть, скачивание продолжится.
 */
class LibraryDownloader(private val state: AppState) {
    private val api get() = state.api
    private val fs: LocalFileStore? get() = state.platform.fileStore

    var running by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    var current by mutableStateOf("")
        private set
    var step by mutableStateOf("")
        private set
    var fileDone by mutableStateOf(0L)
        private set
    var fileTotal by mutableStateOf(0L)
        private set
    var done by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set
    var speed by mutableStateOf("")
        private set
    val errors = mutableStateListOf<String>()
    var lastDestination by mutableStateOf(state.platform.store.get(K_DEST) ?: "")
        private set

    private var job: Job? = null

    val progress: Float
        get() = if (total == 0) 0f
        else ((done + if (fileTotal > 0) fileDone.toFloat() / fileTotal else 0f) / total).coerceIn(0f, 1f)

    fun rememberDestination(path: String) {
        lastDestination = path
        state.platform.store.put(K_DEST, path)
    }

    /** Качество скачивания (запоминается) */
    var quality by mutableStateOf(com.adultaristocrat.cinema.Quality.of(state.platform.store.get(K_QUALITY)))
        private set

    fun updateQuality(q: com.adultaristocrat.cinema.Quality) {
        quality = q
        state.platform.store.put(K_QUALITY, q.name)
    }

    fun start(entries: List<DownloadEntry>, dest: String) {
        val store = fs ?: return
        if (running || entries.isEmpty()) return
        rememberDestination(dest)
        running = true
        finished = false
        errors.clear()
        done = 0
        total = entries.size
        job = state.scope.launch {
            val bioDone = HashSet<String>()
            try {
                for (e in entries) {
                    current = e.item.name ?: "Фильм"
                    fileDone = 0
                    fileTotal = 0
                    try {
                        downloadMovie(store, e, dest, bioDone)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (ex: Exception) {
                        errors += "${e.item.name}: ${ex.message ?: ex}"
                    }
                    done++
                }
                step = "Готово"
            } catch (c: CancellationException) {
                step = "Остановлено"
            } finally {
                running = false
                finished = true
                current = ""
                speed = ""
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun reset() {
        if (!running) {
            finished = false
            errors.clear()
        }
    }

    private suspend fun downloadMovie(store: LocalFileStore, e: DownloadEntry, dest: String, bioDone: MutableSet<String>) {
        step = "Сведения о фильме"
        val item = api.fullItem(e.item.id)
        val serverPath = item.path ?: e.item.path ?: ""
        val container = item.mediaSources.firstOrNull()?.container?.substringBefore(',')
        val fileName = safe(serverPath.replace('\\', '/').substringAfterLast('/').ifBlank {
            "${item.name ?: item.id}${item.productionYear?.let { " ($it)" } ?: ""}.${container ?: "mkv"}"
        })
        val base = fileName.substringBeforeLast('.')
        val dirParts = listOf(dest, safe(e.libraryName)) +
            e.relDir.split('/').filter { it.isNotBlank() }.map { safe(it) }
        val dir = store.join(*dirParts.toTypedArray())

        // Картинки
        step = "Обложки"
        if (item.imageTags["Primary"] != null) image(store, api.originalImageUrl(item.id, "Primary"), store.join(dir, "$base-poster.jpg"))
        if (item.backdropImageTags.isNotEmpty()) image(store, api.originalImageUrl(item.id, "Backdrop/0"), store.join(dir, "$base-fanart.jpg"))
        if (item.imageTags["BoxRear"] != null) image(store, api.originalImageUrl(item.id, "BoxRear"), store.join(dir, "$base-back.jpg"))
        if (item.imageTags["Disc"] != null) image(store, api.originalImageUrl(item.id, "Disc"), store.join(dir, "$base-disc.png"))

        // Фото актёров
        step = "Фото актёров"
        val actors = item.people.filter { it.type == "Actor" || it.type == "GuestStar" }
        for (p in actors) {
            if (p.primaryImageTag == null || p.name.isNullOrBlank()) continue
            image(store, api.originalImageUrl(p.id, "Primary"), store.join(dir, ".actors", actorFile(p.name) + ".jpg"))
        }

        // .nfo
        val nfoPath = store.join(dir, "$base.nfo")
        if (store.size(nfoPath) <= 0) store.writeText(nfoPath, Nfo.build(item))

        // Биографии — в корне скачанной библиотеки
        step = "Биографии"
        for (p in item.people) {
            val name = p.name?.takeIf { it.isNotBlank() } ?: continue
            if (!bioDone.add(p.id)) continue
            val path = store.join(dest, ".bio", actorFile(name) + ".txt")
            if (store.size(path) > 0) continue
            val person = runCatching { api.person(p.id) }.getOrNull() ?: continue
            Nfo.bio(person)?.let { store.writeText(path, it) }
        }

        // Сцены — отдельным файлом рядом с видео (при перекодировании сервер их не передаёт).
        // Офлайн-библиотека читает их, если в самом видео сцен нет.
        if (item.chapters.isNotEmpty()) {
            val chPath = store.join(dir, "$base.chapters.xml")
            if (store.size(chPath) <= 0) store.writeText(chPath, Nfo.chaptersXml(item))
        }

        // Видео
        val q = quality
        val original = q == com.adultaristocrat.cinema.Quality.ORIGINAL
        step = if (original) "Видео" else "Видео (${q.title}, сервер перекодирует)"
        val videoPath = store.join(dir, if (original) fileName else "$base.ts")
        val durationSec = (item.runTimeTicks ?: 0L) / com.adultaristocrat.cinema.api.JellyfinApi.TICKS_PER_MS / 1000
        val expected = if (original) item.mediaSources.firstOrNull()?.size ?: 0L
        else (q.bitrate + (if (q.bitrate <= 1_000_000) 96_000 else 160_000)).toLong() / 8 * durationSec
        val have = store.size(videoPath)
        val unfinished = store.size("$videoPath.progress") > 0
        if (have > 0 && !unfinished && (!original || expected <= 0 || have == expected)) return
        val clock = kotlin.time.TimeSource.Monotonic.markNow()
        var lastT = 0L
        var lastB = 0L
        val onProgress: (Long, Long) -> Unit = { d, t ->
            fileDone = d
            fileTotal = if (t > 0) t else expected
            val now = clock.elapsedNow().inWholeMilliseconds
            if (now - lastT >= 1000) {
                val bps = (d - lastB) * 1000.0 / (now - lastT)
                speed = formatSpeed(bps)
                lastT = now
                lastB = d
            }
        }
        if (!original) {
            downloadHls(store, item, q, videoPath, expected, onProgress)
            return
        }
        retrying {
            try {
                store.download(api.downloadUrl(item.id), videoPath, onProgress)
            } catch (ex: HttpStatusError) {
                // Нет права «Скачивание» — берём тот же файл как поток без перекодирования
                if (ex.code == 401 || ex.code == 403 || ex.code == 404) {
                    store.download(api.directStreamUrl(item.id, item.mediaSources.firstOrNull()?.id, null), videoPath, onProgress)
                } else throw ex
            }
        }
    }

    /**
     * Обрыв связи — ждём и пробуем снова (до 30 раз, пауза растёт до минуты).
     * Оригинал докачивается с места обрыва, перекодированное видео — с той же части.
     */
    private suspend fun <T> retrying(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (c: CancellationException) {
                throw c
            } catch (e: HttpStatusError) {
                if (e.code in 400..499) throw e // нет доступа — повтор не поможет
                if (++attempt > 30) throw e
            } catch (e: Exception) {
                if (++attempt > 30) throw e
            }
            val wait = minOf(60, 3 * attempt)
            for (sec in wait downTo 1) {
                step = "Нет связи — повтор через $sec с (попытка $attempt)"
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    /**
     * Перекодированное видео частями (HLS, по 6 секунд) в один файл .ts.
     * Рядом лежит «.progress» — сколько частей уже записано; после обрыва или перезапуска
     * программы скачивание продолжается с первой недокачанной части.
     */
    private suspend fun downloadHls(
        store: LocalFileStore, item: BaseItem, q: com.adultaristocrat.cinema.Quality,
        videoPath: String, expected: Long, onProgress: (Long, Long) -> Unit,
    ) {
        val session = "aadl" + kotlin.random.Random.nextLong().toULong().toString(16)
        val masterUrl = api.hlsDownloadUrl(item.id, item.mediaSources.firstOrNull()?.id, q, session)
        val master = retrying { store.getText(masterUrl) }
        val variant = master.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?: error("Сервер не отдал список частей")
        val variantUrl = resolve(masterUrl, variant)
        val playlist = retrying { store.getText(variantUrl) }
        val segments = playlist.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolve(variantUrl, it) }
        if (segments.isEmpty()) error("Сервер не отдал части видео")

        val part = "$videoPath.part"
        val progressFile = "$videoPath.progress"
        // «частей;байт» — продолжаем с места остановки
        val saved = store.readText(progressFile)?.trim()?.split(';')
        var doneSeg = saved?.getOrNull(0)?.toIntOrNull()?.coerceIn(0, segments.size) ?: 0
        var bytes = saved?.getOrNull(1)?.toLongOrNull() ?: 0L
        if (doneSeg == 0) bytes = 0
        store.truncate(part, bytes) // отрезаем кусок, записанный во время обрыва
        try {
            while (doneSeg < segments.size) {
                val data = retrying { store.getBytes(segments[doneSeg]) }
                store.append(part, data)
                bytes += data.size
                doneSeg++
                store.writeText(progressFile, "$doneSeg;$bytes")
                val estimate = if (doneSeg > 0) bytes / doneSeg * segments.size else expected
                onProgress(bytes, maxOf(estimate, bytes))
                step = "Видео (${q.title}): часть $doneSeg из ${segments.size}"
            }
        } finally {
            api.stopEncoding(session)
        }
        store.delete(videoPath)
        store.rename(part, videoPath)
        store.delete(progressFile)
    }

    private fun resolve(base: String, ref: String): String = when {
        ref.startsWith("http://") || ref.startsWith("https://") -> ref
        ref.startsWith("/") -> base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/') + ref
        else -> base.substringBefore('?').substringBeforeLast('/') + "/" + ref
    }

    private suspend fun image(store: LocalFileStore, url: String, path: String) {
        if (store.size(path) > 0) return
        runCatching { store.download(url, path) { _, _ -> } }
    }

    companion object {
        private const val K_DEST = "downloadDir"
        private const val K_QUALITY = "downloadQuality"

        /** Имя файла без запрещённых в Windows символов */
        fun safe(s: String): String =
            s.replace(Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]"), "_").trim().trimEnd('.', ' ').ifBlank { "_" }

        /** Как у Kodi: «Phoenix Marie» → «Phoenix_Marie» */
        fun actorFile(name: String): String = safe(name).replace(' ', '_')

        fun formatSpeed(bps: Double): String = when {
            bps >= 1_000_000 -> "${(bps / 100_000).toInt() / 10.0} МБ/с"
            bps >= 1_000 -> "${(bps / 1000).toInt()} КБ/с"
            else -> "${bps.toInt()} Б/с"
        }

        fun formatSize(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "${(bytes * 10 / (1L shl 30)) / 10.0} ГБ"
            bytes >= 1L shl 20 -> "${bytes / (1L shl 20)} МБ"
            else -> "${bytes / 1024} КБ"
        }

        /**
         * Папки фильмов внутри раздела: общий для всех фильмов начальный путь — это корень раздела,
         * остальное («Серия …/Фильм (2026)») сохраняем как есть.
         */
        fun relativeDirs(items: List<BaseItem>): Map<String, String> {
            fun segs(p: String?) = (p ?: "").replace('\\', '/').split('/').filter { it.isNotEmpty() }.dropLast(1)
            val dirs = items.associate { it.id to segs(it.path) }
            val withPath = dirs.values.filter { it.isNotEmpty() }
            val root: List<String> = when {
                withPath.isEmpty() -> emptyList()
                withPath.size == 1 -> withPath[0].dropLast(1)
                else -> withPath.reduce { a, b -> a.zip(b).takeWhile { it.first == it.second }.map { it.first } }
            }
            return items.associate { m ->
                val d = dirs[m.id].orEmpty()
                val rel = if (d.isEmpty()) "${m.name ?: m.id}${m.productionYear?.let { y -> " ($y)" } ?: ""}"
                else d.drop(root.size).joinToString("/")
                m.id to rel
            }
        }
    }
}
