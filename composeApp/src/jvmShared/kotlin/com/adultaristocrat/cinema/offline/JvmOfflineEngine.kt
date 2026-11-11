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
import org.w3c.dom.Element
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
import javax.xml.parsers.DocumentBuilderFactory
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

    private fun ctx(host: String, user: String? = null, password: String? = null): CIFSContext {
        val u = user ?: creds[host.lowercase()]?.first ?: ""
        val pw = password ?: creds[host.lowercase()]?.second ?: ""
        return if (u.isBlank()) baseContext.withGuestCrendentials()
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
        SmbFile("smb://$host/", ctx(host, user, password)).listFiles()
            .map { it.name.trimEnd('/') }
            .filter { !it.endsWith("$") }
            .sorted()
    }

    override suspend fun listDirs(source: OfflineSource): List<String> = withContext(Dispatchers.IO) {
        creds[source.host.lowercase()] = source.user to source.password
        SmbFile(source.smbUrl, ctx(source.host, source.user, source.password)).listFiles()
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
            val root = SmbFile(src.smbUrl, ctx(src.host, src.user, src.password))
            walk(root, 0, out, onProgress)
        }
        out
    }

    private suspend fun walk(dir: SmbFile, depth: Int, out: MutableList<OfflineMovie>, onProgress: (String, Int) -> Unit) {
        if (depth > 12) return
        coroutineContext.ensureActive()
        val files = runCatching { dir.listFiles().toList() }.getOrElse { return }
        onProgress(dir.path.removePrefix("smb://"), out.size)

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

    private fun buildMovie(video: SmbFile, files: List<SmbFile>, order: Int): OfflineMovie {
        val base = video.name.substringBeforeLast('.')
        fun find(vararg names: String): SmbFile? =
            names.firstNotNullOfOrNull { want -> files.firstOrNull { it.name.equals(want, ignoreCase = true) } }

        val nfoFile = find("$base.nfo", "movie.nfo")
        val poster = find(
            "$base-poster.jpg", "$base-poster.png", "poster.jpg", "poster.png",
            "folder.jpg", "cover.jpg", "$base.jpg", "$base.png",
        )
        val fanart = find("$base-fanart.jpg", "$base-fanart.png", "fanart.jpg", "fanart.png", "backdrop.jpg", "$base-landscape.jpg")
        val actorsDir = files.firstOrNull { it.name.equals(".actors/", ignoreCase = true) }
        val actorPics: Map<String, String> = actorsDir?.let { d ->
            runCatching {
                d.listFiles().filter { !it.name.endsWith("/") }
                    .associate { it.name.substringBeforeLast('.').lowercase() to it.path }
            }.getOrNull()
        } ?: emptyMap()

        val nfo = nfoFile?.let { runCatching { parseNfo(readText(it)) }.getOrNull() }

        fun actorThumb(name: String, nfoThumb: String?): String? {
            if (!nfoThumb.isNullOrBlank() && (nfoThumb.startsWith("http://") || nfoThumb.startsWith("https://"))) return nfoThumb
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
            fanart = fanart?.path,
            actors = nfo?.actors()?.map { (n, role, thumb) -> OfflineActor(n, role, actorThumb(n, thumb)) }
                ?: actorPics.keys.map { OfflineActor(it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }, null, actorPics[it]) },
            directors = nfo?.list("director").orEmpty(),
            chapters = if (video.name.lowercase().endsWith(".mkv")) readChapters(video) else emptyList(),
            order = order,
        )
    }

    /** Сцены из глав MKV — читается только заголовок файла */
    private fun readChapters(f: SmbFile): List<OfflineChapter> = runCatching {
        val len = f.length()
        SmbRandomAccessFile(f, "r").use { raf ->
            MkvChapters.read({ pos, buf ->
                raf.seek(pos)
                raf.read(buf, 0, buf.size)
            }, len)
        }
    }.getOrDefault(emptyList())

    private fun readText(f: SmbFile): String {
        val bytes = f.inputStream.use { it.readBytes() }
        var s = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        // В конце Kodi-NFO может быть ссылка — отрезаем всё после </movie>
        val end = s.lastIndexOf("</movie>", ignoreCase = true)
        if (end >= 0) s = s.substring(0, end + "</movie>".length)
        return s
    }

    private class Nfo(val root: Element) {
        private fun direct(tag: String): List<Element> {
            val res = mutableListOf<Element>()
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                if (n is Element && n.tagName.equals(tag, ignoreCase = true)) res += n
            }
            return res
        }

        fun text(tag: String): String? = direct(tag).firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }

        fun list(tag: String): List<String> =
            direct(tag).flatMap { it.textContent.split("/", "|") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        fun rating(): Float? {
            text("rating")?.replace(',', '.')?.toFloatOrNull()?.let { return it }
            val ratings = direct("ratings").firstOrNull() ?: return null
            val items = ratings.getElementsByTagName("rating")
            var best: Float? = null
            for (i in 0 until items.length) {
                val e = items.item(i) as? Element ?: continue
                val v = (e.getElementsByTagName("value").item(0)?.textContent ?: continue).replace(',', '.').toFloatOrNull()
                if (e.getAttribute("default") == "true") return v
                if (best == null) best = v
            }
            return best
        }

        fun actors(): List<Triple<String, String?, String?>> = direct("actor").mapNotNull { a ->
            fun child(t: String) = a.getElementsByTagName(t).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            val name = child("name") ?: return@mapNotNull null
            Triple(name, child("role"), child("thumb"))
        }
    }

    private fun parseNfo(xml: String): Nfo? {
        val f = DocumentBuilderFactory.newInstance()
        runCatching { f.isNamespaceAware = false }
        val doc = f.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
        val root = doc.documentElement ?: return null
        return Nfo(root)
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

        if (!target.startsWith("/f/$key")) return fail(403, "Forbidden")
        val query = target.substringAfter('?', "")
        val smb = query.split("&").firstOrNull { it.startsWith("u=") }?.let { URLDecoder.decode(it.substring(2), "UTF-8") }
            ?: return fail(400, "Bad Request")

        val file = SmbFile(smb, ctx(hostOf(smb)))
        val length = runCatching { file.length() }.getOrElse { return fail(404, "Not Found") }
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
        SmbRandomAccessFile(file, "r").use { raf ->
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
