package com.adultaristocrat.cinema.offline

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * Офлайн-библиотека: SMB (jcifs-ng) + маленький HTTP-сервер на 127.0.0.1,
 * через который ExoPlayer / VLC и загрузчик картинок читают файлы с перемоткой (Range).
 * Один и тот же код собирается и для Android, и для ПК.
 */
class JvmOfflineEngine : OfflineEngine {

    private val baseContext: CIFSContext by lazy {
        val p = Properties()
        p.setProperty("jcifs.smb.client.minVersion", "SMB1")
        p.setProperty("jcifs.smb.client.maxVersion", "SMB311")
        p.setProperty("jcifs.smb.client.responseTimeout", "15000")
        p.setProperty("jcifs.smb.client.soTimeout", "35000")
        p.setProperty("jcifs.resolveOrder", "DNS,BCAST")
        p.setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
        BaseContext(PropertyConfiguration(p))
    }

    /** host → учётные данные */
    private val creds = ConcurrentHashMap<String, Pair<String, String>>()

    /**
     * Папки без пароля: Windows с отключённым «доступом с парольной защитой» пускает
     * то гостем, то анонимно, то как пользователя «Guest». Пробуем все варианты и запоминаем рабочий.
     */
    private val guestMode = ConcurrentHashMap<String, Int>()
    private val guestContexts: List<CIFSContext> by lazy {
        listOf(
            baseContext.withGuestCrendentials(),
            baseContext.withAnonymousCredentials(),
            baseContext.withCredentials(NtlmPasswordAuthenticator("", "Guest", "")),
        )
    }

    /** Выполнить действие с подходящим входом: для папок без пароля — перебор гостевых вариантов */
    private fun <T> withAuth(host: String, user: String?, password: String?, block: (CIFSContext) -> T): T {
        val u = user ?: creds[host.lowercase()]?.first ?: ""
        if (u.isNotBlank()) return block(ctx(host, user, password))
        val key = host.lowercase()
        val first = guestMode[key] ?: 0
        val order = listOf(first) + guestContexts.indices.filter { it != first }
        var last: Throwable? = null
        for (i in order) {
            try {
                val r = block(guestContexts[i])
                guestMode[key] = i
                return r
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("Нет доступа")
    }

    private fun ctx(host: String, user: String? = null, password: String? = null): CIFSContext {
        val u = user ?: creds[host.lowercase()]?.first ?: ""
        val pw = password ?: creds[host.lowercase()]?.second ?: ""
        return if (u.isBlank()) guestContexts[guestMode[host.lowercase()] ?: 0]
        else {
            // Поддержка «ДОМЕН\пользователь»
            val domain = if (u.contains("\\")) u.substringBefore("\\") else null
            val name = if (u.contains("\\")) u.substringAfter("\\") else u
            baseContext.withCredentials(NtlmPasswordAuthenticator(domain, name, pw))
        }
    }

    private fun hostOf(smbUrl: String): String = smbUrl.removePrefix("smb://").substringBefore("/")

    override fun setSources(sources: List<OfflineSource>) {
        creds.clear()
        sources.forEach { creds[it.host.lowercase()] = it.user to it.password }
    }

    // ---------------- Поиск в сети ----------------

    override suspend fun discover(onFound: (OfflineHost) -> Unit): Unit = withContext(Dispatchers.IO) {
        val prefixes = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { ia ->
                    val a = ia.address
                    if (a is Inet4Address && a.isSiteLocalAddress) a.hostAddress.substringBeforeLast('.') else null
                }
                .distinct()
        }.getOrDefault(emptyList())
        val sem = Semaphore(64)
        coroutineScope {
            for (prefix in prefixes) for (i in 1..254) {
                launch {
                    sem.withPermit {
                        val ip = "$prefix.$i"
                        if (portOpen(ip, 445, 400)) {
                            val name = runCatching {
                                InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
                            }.getOrNull()
                            onFound(OfflineHost(ip, name))
                        }
                    }
                }
            }
        }
    }

    private fun portOpen(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs); true }
    } catch (e: Exception) {
        false
    }

    // ---------------- Папки ----------------

    override suspend fun listShares(host: String, user: String, password: String): List<String> = withContext(Dispatchers.IO) {
        creds[host.lowercase()] = user to password
        withAuth(host, user, password) { c -> SmbFile("smb://$host/", c).listFiles() }
            .map { it.name.trimEnd('/') }
            .filter { !it.endsWith("$") }
            .sorted()
    }

    override suspend fun listDirs(source: OfflineSource): List<String> = withContext(Dispatchers.IO) {
        creds[source.host.lowercase()] = source.user to source.password
        withAuth(source.host, source.user, source.password) { c -> SmbFile(source.smbUrl, c).listFiles() }
            .filter { it.name.endsWith("/") && !it.name.startsWith(".") }
            .map { it.name.trimEnd('/') }
            .sortedBy { it.lowercase() }
    }

    // ---------------- Сканирование ----------------

    private val videoExt = setOf("mkv", "mp4", "m4v", "avi", "mov", "wmv", "ts", "m2ts", "webm", "mpg", "mpeg", "flv")
    private val skipDirs = setOf("extras", "trailers", "sample", "samples", "featurettes", "behind the scenes", "\$recycle.bin")

    override suspend fun scan(
        sources: List<OfflineSource>,
        onProgress: (folder: String, found: Int) -> Unit,
    ): List<OfflineMovie> = withContext(Dispatchers.IO) {
        setSources(sources)
        val out = mutableListOf<OfflineMovie>()
        for (src in sources) {
            walk(rootOf(src), 0, out, onProgress)
        }
        out
    }

    /** Папки .bio: в корне источника и на 1–2 уровня ниже (например, в папке студии) */
    override suspend fun scanBios(sources: List<OfflineSource>): List<OfflineBio> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, OfflineBio>()
        fun look(dir: VFile, depth: Int) {
            val files = runCatching { dir.listFiles().toList() }.getOrElse { return }
            files.firstOrNull { it.name.equals(".bio/", ignoreCase = true) }?.let { bioDir ->
                runCatching { bioDir.listFiles().toList() }.getOrDefault(emptyList())
                    .filter { f -> f.name.lowercase().let { it.endsWith(".txt") || it.endsWith(".md") } }
                    .forEach { f ->
                        runCatching {
                            val text = Nfo.decode(f.readBytes())
                            BioParser.parse(f.name, text)
                        }.getOrNull()?.let { out[it.name.lowercase()] = it }
                    }
            }
            if (depth < 2) files.filter { it.name.endsWith("/") && !it.name.startsWith(".") }.forEach { look(it, depth + 1) }
        }
        for (src in sources) runCatching { look(rootOf(src), 0) }
        out.values.toList()
    }

    /** Корень источника: сетевая папка (SMB) или папка на этом компьютере */
    private fun rootOf(src: OfflineSource): VFile =
        if (src.isLocal) LocalVFile(java.io.File(src.localPath))
        else runCatching {
            withAuth(src.host, src.user, src.password) { c -> SmbVFile(SmbFile(src.smbUrl, c)).also { it.listFiles() } }
        }.getOrElse { SmbVFile(SmbFile(src.smbUrl, ctx(src.host, src.user, src.password))) }

    /** Файл по сохранённому пути (smb://… или local:…) */
    private fun openPath(path: String): VFile =
        if (path.startsWith(LocalVFile.PREFIX)) LocalVFile(java.io.File(path.removePrefix(LocalVFile.PREFIX)))
        else withAuth(hostOf(path), null, null) { c -> SmbVFile(SmbFile(path, c)).also { it.length() } }

    private suspend fun walk(dir: VFile, depth: Int, out: MutableList<OfflineMovie>, onProgress: (String, Int) -> Unit) {
        if (depth > 12) return
        coroutineContext.ensureActive()
        val files = runCatching { dir.listFiles().toList() }.getOrElse { return }
        onProgress(dir.path.removePrefix("smb://").removePrefix(LocalVFile.PREFIX), out.size)

        val videos = files.filter { f ->
            val n = f.name.lowercase()
            !n.endsWith("/") && n.substringAfterLast('.', "") in videoExt && !n.contains("sample") && !n.contains("-trailer")
        }
        for (v in videos) {
            runCatching { buildMovie(v, files, out.size) }.getOrNull()?.let { out += it }
        }
        for (d in files) {
            val n = d.name.lowercase().trimEnd('/')
            if (d.name.endsWith("/") && !n.startsWith(".") && n !in skipDirs) walk(d, depth + 1, out, onProgress)
        }
    }

    private fun buildMovie(video: VFile, files: List<VFile>, order: Int): OfflineMovie {
        val base = video.name.substringBeforeLast('.')
        fun find(vararg names: String): VFile? =
            names.firstNotNullOfOrNull { want -> files.firstOrNull { it.name.equals(want, ignoreCase = true) } }

        val videosHere = files.count { f ->
            val n = f.name.lowercase()
            !n.endsWith("/") && n.substringAfterLast('.', "") in videoExt && !n.contains("sample") && !n.contains("-trailer")
        }
        // .nfo с именем видео, movie.nfo или единственный .nfo в папке с одним фильмом
        val nfoFile = find("$base.nfo", "movie.nfo")
            ?: if (videosHere == 1) files.singleOrNull { it.name.lowercase().endsWith(".nfo") } else null
        val img = listOf("jpg", "jpeg", "png", "webp")
        fun pics(vararg names: String) = names.flatMap { n -> img.map { "$n.$it" } }.toTypedArray()
        val poster = find(*pics("$base-poster", "poster", "folder", "cover", "$base-cover", "$base-thumb", "$base"))
        val back = find(*pics("$base-back", "$base-backcover", "$base-rear", "$base-boxrear", "back", "backcover", "boxrear", "cover-back"))
        val fanart = find(*pics("$base-fanart", "fanart", "backdrop", "$base-backdrop", "$base-landscape", "landscape"))
        val actorsDir = files.firstOrNull { it.name.equals(".actors/", ignoreCase = true) }
        val actorPics: Map<String, String> = actorsDir?.let { d ->
            runCatching {
                d.listFiles().filter { !it.name.endsWith("/") }
                    .associate { it.name.substringBeforeLast('.').lowercase() to it.path }
            }.getOrNull()
        } ?: emptyMap()

        val nfo = nfoFile?.let { runCatching { readNfo(it) }.getOrNull() }

        fun actorThumb(name: String, nfoThumb: String?): String? {
            if (!nfoThumb.isNullOrBlank() && (nfoThumb.startsWith("http://") || nfoThumb.startsWith("https://"))) return nfoThumb
            // Относительный путь из NFO (.actors/Имя_Фамилия.jpg) — берём имя файла
            nfoThumb?.replace('\\', '/')?.substringAfterLast('/')?.substringBeforeLast('.')?.lowercase()
                ?.let { actorPics[it] }?.let { return it }
            val keys = listOf(name.replace(' ', '_'), name, name.replace(' ', '.'))
            return keys.firstNotNullOfOrNull { actorPics[it.lowercase()] }
        }

        val title = nfo?.text("title") ?: base.replace(Regex("""\s*\(\d{4}\)\s*$"""), "").trim()
        val yearFromName = Regex("""\((\d{4})\)""").find(base)?.groupValues?.get(1)?.toIntOrNull()

        return OfflineMovie(
            id = "off:" + Integer.toHexString(video.path.hashCode()) + Integer.toHexString(video.path.length),
            title = title,
            originalTitle = nfo?.text("originaltitle"),
            year = nfo?.text("year")?.take(4)?.toIntOrNull() ?: nfo?.text("premiered")?.take(4)?.toIntOrNull() ?: yearFromName,
            premiered = nfo?.text("premiered") ?: nfo?.text("releasedate"),
            plot = nfo?.text("plot") ?: nfo?.text("outline"),
            tagline = nfo?.text("tagline"),
            genres = nfo?.list("genre").orEmpty(),
            countries = nfo?.list("country").orEmpty(),
            studios = nfo?.list("studio").orEmpty(),
            rating = nfo?.rating(),
            runtimeMin = nfo?.text("runtime")?.filter { it.isDigit() }?.toIntOrNull(),
            mpaa = nfo?.text("mpaa"),
            video = video.path,
            poster = poster?.path,
            back = back?.path,
            fanart = fanart?.path,
            actors = nfo?.actors()?.takeIf { it.isNotEmpty() }?.map { (n, role, thumb) -> OfflineActor(n, role, actorThumb(n, thumb)) }
                ?: actorPics.keys.map { OfflineActor(it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }, null, actorPics[it]) },
            directors = nfo?.list("director").orEmpty(),
            chapters = (if (video.name.lowercase().endsWith(".mkv")) readChapters(video) else emptyList())
                .ifEmpty { find("$base.chapters.xml", "chapters.xml")?.let { readChaptersXml(it) }.orEmpty() },
            order = order,
        )
    }

    /** Сцены из глав MKV — читается только заголовок файла */
    private fun readChapters(f: VFile): List<OfflineChapter> = runCatching {
        val len = f.length()
        f.openRandom().use { raf ->
            MkvChapters.read({ pos, buf ->
                raf.seek(pos)
                raf.read(buf, 0, buf.size)
            }, len)
        }
    }.getOrDefault(emptyList())

    /** Сцены из файла Matroska XML рядом с видео (скачанные в уменьшенном качестве) */
    private fun readChaptersXml(f: VFile): List<OfflineChapter> = runCatching {
        val text = Nfo.decode(f.readBytes())
        val flags = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        Regex("<ChapterAtom>(.*?)</ChapterAtom>", flags).findAll(text).mapNotNull { m ->
            val atom = m.groupValues[1]
            val t = Regex("<ChapterTimeStart>\\s*(\\d+):(\\d+):(\\d+)(?:\\.(\\d+))?\\s*</ChapterTimeStart>", flags).find(atom)
                ?: return@mapNotNull null
            val (h, mi, s, frac) = t.destructured
            val ms = (h.toLong() * 3600 + mi.toLong() * 60 + s.toLong()) * 1000 + frac.padEnd(3, '0').take(3).toLong()
            val name = Regex("<ChapterString>(.*?)</ChapterString>", flags).find(atom)?.groupValues?.get(1)?.let { Nfo.clean(it) }
            OfflineChapter(name ?: "", ms)
        }.toList()
    }.getOrDefault(emptyList())

    private fun readNfo(f: VFile): Nfo? {
        val bytes = f.readBytes()
        return Nfo.parse(bytes)
    }

    // ---------------- Локальный HTTP-сервер ----------------

    private val key = UUID.randomUUID().toString().replace("-", "")
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "offline-http").apply { isDaemon = true } }

    private val server: ServerSocket by lazy {
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        pool.execute {
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: continue
                pool.execute { runCatching { handle(client) }; runCatching { client.close() } }
            }
        }
        s
    }

    /** Платформа даёт «снимок кадра»: Android — MediaMetadataRetriever, ПК — VLC. Возвращает JPEG. */
    var frameGrabber: ((url: String, ms: Long) -> ByteArray?)? = null

    /** Картинка «Фотографии режиссёров не добавляются» из ресурсов приложения */
    var directorImage: (() -> ByteArray?)? = null

    private val thumbDir: java.io.File by lazy {
        java.io.File(System.getProperty("java.io.tmpdir") ?: ".", "aa-scene-thumbs").also { it.mkdirs() }
    }
    private val thumbLock = Any()
    private val thumbFailed = ConcurrentHashMap.newKeySet<String>()

    override fun sceneThumbUrl(videoSmbPath: String, ms: Long): String? {
        if (frameGrabber == null) return null
        return "http://127.0.0.1:${server.localPort}/t/$key.jpg?ms=$ms&u=" + URLEncoder.encode(videoSmbPath, "UTF-8")
    }

    override fun directorPhotoUrl(): String? =
        if (directorImage == null) null else "http://127.0.0.1:${server.localPort}/d/$key.jpg"

    /** Кадр сцены: берём из кэша или делаем один раз (по одному за раз, чтобы не перегружать устройство) */
    private fun sceneThumb(videoSmb: String, ms: Long): ByteArray? {
        val name = Integer.toHexString(videoSmb.hashCode()) + "_" + videoSmb.length + "_" + ms + ".jpg"
        val f = java.io.File(thumbDir, name)
        if (f.length() > 0) return runCatching { f.readBytes() }.getOrNull()
        if (name in thumbFailed) return null
        val grab = frameGrabber ?: return null
        synchronized(thumbLock) {
            if (f.length() > 0) return runCatching { f.readBytes() }.getOrNull()
            val bytes = runCatching { grab(fileUrl(videoSmb), ms) }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                thumbFailed += name
                return null
            }
            runCatching { f.writeBytes(bytes) }
            return bytes
        }
    }

    override fun fileUrl(smbPath: String): String {
        if (smbPath.startsWith("http://") || smbPath.startsWith("https://")) return smbPath
        val ext = smbPath.substringAfterLast('.', "bin").lowercase()
        return "http://127.0.0.1:${server.localPort}/f/$key.$ext?u=" + URLEncoder.encode(smbPath, "UTF-8")
    }

    private fun mime(ext: String) = when (ext) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "ts", "m2ts" -> "video/mp2t"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    private fun readLine(inp: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = inp.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
            if (sb.length > 16_384) return null
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 60_000
        val inp = socket.getInputStream()
        val requestLine = readLine(inp) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(inp) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: "GET"
        val target = parts.getOrNull(1) ?: "/"
        val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)

        fun fail(code: Int, text: String) {
            out.write("HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
        }

        fun sendBytes(bytes: ByteArray, type: String) {
            out.write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: max-age=86400\r\nConnection: close\r\n\r\n".toByteArray())
            if (!method.equals("HEAD", ignoreCase = true)) out.write(bytes)
            out.flush()
        }
        if (target.startsWith("/d/$key")) {
            val b = runCatching { directorImage?.invoke() }.getOrNull() ?: return fail(404, "Not Found")
            return sendBytes(b, "image/jpeg")
        }
        if (target.startsWith("/t/$key")) {
            val q = target.substringAfter('?', "").split("&")
            val video = q.firstOrNull { it.startsWith("u=") }?.let { URLDecoder.decode(it.substring(2), "UTF-8") } ?: return fail(400, "Bad Request")
            val ms = q.firstOrNull { it.startsWith("ms=") }?.substring(3)?.toLongOrNull() ?: 0L
            val b = sceneThumb(video, ms) ?: return fail(404, "Not Found")
            return sendBytes(b, "image/jpeg")
        }
        if (!target.startsWith("/f/$key")) return fail(403, "Forbidden")
        val query = target.substringAfter('?', "")
        val smb = query.split("&").firstOrNull { it.startsWith("u=") }?.let { URLDecoder.decode(it.substring(2), "UTF-8") }
            ?: return fail(400, "Bad Request")

        val (file, length) = runCatching { openPath(smb).let { f -> f to f.length() } }
            .getOrElse { return fail(404, "Not Found") }
        var start = 0L
        var end = length - 1
        val range = headers["range"]
        val partial = range != null && range.startsWith("bytes=")
        if (partial) {
            val r = range!!.removePrefix("bytes=").split(",")[0]
            val a = r.substringBefore("-").trim()
            val b = r.substringAfter("-").trim()
            if (a.isEmpty()) {
                start = (length - (b.toLongOrNull() ?: 0)).coerceAtLeast(0)
            } else {
                start = a.toLongOrNull() ?: 0
                if (b.isNotEmpty()) end = (b.toLongOrNull() ?: end).coerceAtMost(length - 1)
            }
            if (start >= length) {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$length\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                return
            }
        }
        val count = end - start + 1
        val ext = smb.substringAfterLast('.', "").lowercase()
        val head = StringBuilder()
        head.append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        head.append("Content-Type: ${mime(ext)}\r\n")
        head.append("Accept-Ranges: bytes\r\n")
        head.append("Content-Length: $count\r\n")
        if (partial) head.append("Content-Range: bytes $start-$end/$length\r\n")
        head.append("Cache-Control: max-age=3600\r\n")
        head.append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray())
        if (method.equals("HEAD", ignoreCase = true)) {
            out.flush()
            return
        }
        file.openRandom().use { raf ->
            raf.seek(start)
            val buf = ByteArray(256 * 1024)
            var left = count
            while (left > 0) {
                val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n <= 0) break
                out.write(buf, 0, n)
                left -= n
            }
        }
        out.flush()
    }
}

// ---------------- Файлы: сетевые (SMB) и на этом компьютере ----------------

internal interface VRandom : java.io.Closeable {
    fun seek(pos: Long)
    fun read(b: ByteArray, off: Int, len: Int): Int
}

/** Файл или папка. У папок имя оканчивается на «/» (как в jcifs) */
internal interface VFile {
    val name: String
    val path: String
    fun listFiles(): List<VFile>
    fun length(): Long
    fun readBytes(): ByteArray
    fun openRandom(): VRandom
}

internal class SmbVFile(private val f: SmbFile) : VFile {
    override val name: String get() = f.name
    override val path: String get() = f.path
    override fun listFiles(): List<VFile> = f.listFiles().map { SmbVFile(it) }
    override fun length(): Long = f.length()
    override fun readBytes(): ByteArray = f.inputStream.use { it.readBytes() }
    override fun openRandom(): VRandom {
        val r = SmbRandomAccessFile(f, "r")
        return object : VRandom {
            override fun seek(pos: Long) = r.seek(pos)
            override fun read(b: ByteArray, off: Int, len: Int): Int = r.read(b, off, len)
            override fun close() = r.close()
        }
    }
}

internal class LocalVFile(private val f: java.io.File) : VFile {
    override val name: String get() = if (f.isDirectory) f.name + "/" else f.name
    override val path: String get() = PREFIX + f.absolutePath
    override fun listFiles(): List<VFile> = (f.listFiles() ?: emptyArray()).map { LocalVFile(it) }
    override fun length(): Long = if (f.exists()) f.length() else throw java.io.FileNotFoundException(f.path)
    override fun readBytes(): ByteArray = f.readBytes()
    override fun openRandom(): VRandom {
        val r = java.io.RandomAccessFile(f, "r")
        return object : VRandom {
            override fun seek(pos: Long) = r.seek(pos)
            override fun read(b: ByteArray, off: Int, len: Int): Int = r.read(b, off, len)
            override fun close() = r.close()
        }
    }

    companion object {
        const val PREFIX = "local:"
    }
}
