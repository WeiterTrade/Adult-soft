package com.adultaristocrat.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Скачивание библиотеки на ПК: обычные файлы, докачка через «.part» */
class DesktopFileStore : LocalFileStore {
    override fun join(vararg parts: String): String =
        parts.filter { it.isNotEmpty() }.drop(1).fold(File(parts.first { it.isNotEmpty() })) { f, p -> File(f, p) }.path

    override fun size(path: String): Long = File(path).let { if (it.isFile) it.length() else -1L }

    override fun writeText(path: String, text: String) {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeText(text, Charsets.UTF_8)
    }

    override suspend fun download(url: String, path: String, onProgress: (done: Long, total: Long) -> Unit) {
        withContext(Dispatchers.IO) {
            val target = File(path)
            target.parentFile?.mkdirs()
            val part = File(target.path + ".part")
            var have = if (part.isFile) part.length() else 0L
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw HttpStatusError(code)
                if (code == 200) have = 0 // сервер не умеет докачку — сначала
                val len = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                val total = if (len >= 0) have + len else -1L
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(have)
                    out.seek(have)
                    conn.inputStream.use { inp ->
                        val buf = ByteArray(1 shl 20)
                        var done = have
                        var tick = 0
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (++tick % 8 == 0) {
                                coroutineContext.ensureActive()
                                withContext(Dispatchers.Main) { onProgress(done, total) }
                            }
                        }
                        withContext(Dispatchers.Main) { onProgress(done, total) }
                    }
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
            } finally {
                conn.disconnect()
            }
            Unit
        }
    }

    override fun openFolder(path: String) {
        runCatching { java.awt.Desktop.getDesktop().open(File(path)) }
    }
}
