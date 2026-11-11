package com.adultaristocrat.cinema.offline

import java.io.EOFException

/**
 * Чтение глав (сцен) прямо из MKV-файла — без сервера.
 * Формат Matroska (EBML): ищем элемент Chapters через SeekHead или среди элементов верхнего уровня.
 * Читается всего несколько килобайт из начала (и, если главы в конце, из места, куда указывает SeekHead).
 */
object MkvChapters {
    private const val ID_EBML = 0x1A45DFA3L
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_SEEKHEAD = 0x114D9B74L
    private const val ID_SEEK = 0x4DBBL
    private const val ID_SEEK_ID = 0x53ABL
    private const val ID_SEEK_POSITION = 0x53ACL
    private const val ID_CLUSTER = 0x1F43B675L
    private const val ID_CHAPTERS = 0x1043A770L
    private const val ID_EDITION = 0x45B9L
    private const val ID_EDITION_DEFAULT = 0x45DBL
    private const val ID_EDITION_HIDDEN = 0x45BDL
    private const val ID_ATOM = 0xB6L
    private const val ID_TIME_START = 0x91L
    private const val ID_FLAG_HIDDEN = 0x98L
    private const val ID_FLAG_ENABLED = 0x4598L
    private const val ID_DISPLAY = 0x80L
    private const val ID_STRING = 0x85L
    private const val ID_LANGUAGE = 0x437CL
    private const val ID_LANGUAGE_BCP47 = 0x437DL

    /** Источник байтов: читать в buf с позиции pos, вернуть число прочитанных байт */
    fun interface ReadAt {
        fun read(pos: Long, buf: ByteArray): Int
    }

    private class Reader(private val src: ReadAt, val length: Long) {
        var pos = 0L
        private val buf = ByteArray(64 * 1024)
        private var bufStart = -1L
        private var bufLen = 0

        fun byte(): Int {
            if (bufStart < 0 || pos < bufStart || pos >= bufStart + bufLen) {
                if (pos >= length) throw EOFException()
                bufLen = src.read(pos, buf)
                bufStart = pos
                if (bufLen <= 0) throw EOFException()
            }
            val b = buf[(pos - bufStart).toInt()].toInt() and 0xFF
            pos++
            return b
        }

        fun bytes(n: Int): ByteArray = ByteArray(n) { byte().toByte() }

        /** ID элемента (вместе с маркерными битами), 1–4 байта */
        fun id(): Long {
            val first = byte()
            var len = 1
            var mask = 0x80
            while (len <= 4 && (first and mask) == 0) {
                len++; mask = mask shr 1
            }
            if (len > 4) throw IllegalStateException("bad id")
            var v = first.toLong()
            repeat(len - 1) { v = (v shl 8) or byte().toLong() }
            return v
        }

        /** Размер данных, 1–8 байт; -1 = «неизвестный размер» */
        fun size(): Long {
            val first = byte()
            var len = 1
            var mask = 0x80
            while (len <= 8 && (first and mask) == 0) {
                len++; mask = mask shr 1
            }
            if (len > 8) throw IllegalStateException("bad size")
            var v = (first and (mask - 1)).toLong()
            var allOnes = v == (mask - 1).toLong()
            repeat(len - 1) {
                val b = byte()
                if (b != 0xFF) allOnes = false
                v = (v shl 8) or b.toLong()
            }
            return if (allOnes) -1 else v
        }

        fun uint(size: Long): Long {
            var v = 0L
            repeat(size.toInt().coerceIn(0, 8)) { v = (v shl 8) or byte().toLong() }
            return v
        }

        fun string(size: Long): String = String(bytes(size.toInt().coerceIn(0, 4096)), Charsets.UTF_8).trimEnd('\u0000').trim()
    }

    private data class Atom(val startNs: Long, val names: Map<String, String>, val hidden: Boolean)

    fun read(src: ReadAt, length: Long): List<OfflineChapter> {
        val r = Reader(src, length)
        // Заголовок EBML
        if (r.id() != ID_EBML) return emptyList()
        val ebmlSize = r.size()
        r.pos += ebmlSize

        // Segment
        var segStart = -1L
        var segEnd = length
        repeat(8) {
            if (segStart >= 0) return@repeat
            val id = r.id()
            val size = r.size()
            if (id == ID_SEGMENT) {
                segStart = r.pos
                if (size > 0) segEnd = minOf(length, segStart + size)
            } else {
                if (size < 0) return emptyList()
                r.pos += size
            }
        }
        if (segStart < 0) return emptyList()

        var chaptersPos = -1L
        var p = segStart
        var count = 0
        while (p < segEnd && count < 64) {
            r.pos = p
            val id = r.id()
            val size = r.size()
            val dataStart = r.pos
            when (id) {
                ID_SEEKHEAD -> {
                    val end = dataStart + size
                    while (r.pos < end) {
                        val cid = r.id()
                        val csize = r.size()
                        val cEnd = r.pos + csize
                        if (cid == ID_SEEK) {
                            var seekId = 0L
                            var seekPos = -1L
                            while (r.pos < cEnd) {
                                val sid = r.id()
                                val ssize = r.size()
                                when (sid) {
                                    ID_SEEK_ID -> seekId = r.uint(ssize)
                                    ID_SEEK_POSITION -> seekPos = r.uint(ssize)
                                    else -> r.pos += ssize
                                }
                            }
                            if (seekId == ID_CHAPTERS && seekPos >= 0) chaptersPos = segStart + seekPos
                        }
                        r.pos = cEnd
                    }
                }
                ID_CHAPTERS -> return parseChapters(r, dataStart, size)
                ID_CLUSTER -> break
            }
            if (size < 0) break
            p = dataStart + size
            count++
        }

        if (chaptersPos in segStart until length) {
            r.pos = chaptersPos
            if (r.id() == ID_CHAPTERS) {
                val size = r.size()
                return parseChapters(r, r.pos, size)
            }
        }
        return emptyList()
    }

    private fun parseChapters(r: Reader, start: Long, size: Long): List<OfflineChapter> {
        val end = if (size < 0) r.length else start + size
        r.pos = start
        val editions = mutableListOf<Pair<Boolean, List<Atom>>>()
        while (r.pos < end && editions.size < 16) {
            val id = r.id()
            val sz = r.size()
            val eEnd = r.pos + sz
            if (id == ID_EDITION) {
                var isDefault = false
                var hidden = false
                val atoms = mutableListOf<Atom>()
                while (r.pos < eEnd && atoms.size < 1000) {
                    val cid = r.id()
                    val csz = r.size()
                    val cEnd = r.pos + csz
                    when (cid) {
                        ID_EDITION_DEFAULT -> isDefault = r.uint(csz) == 1L
                        ID_EDITION_HIDDEN -> hidden = r.uint(csz) == 1L
                        ID_ATOM -> atoms += parseAtom(r, cEnd)
                    }
                    r.pos = cEnd
                }
                if (!hidden) editions += isDefault to atoms
            }
            r.pos = eEnd
        }
        val atoms = editions.firstOrNull { it.first && it.second.isNotEmpty() }?.second
            ?: editions.firstOrNull { it.second.isNotEmpty() }?.second
            ?: return emptyList()
        return atoms.filter { !it.hidden }
            .sortedBy { it.startNs }
            .mapIndexed { i, a ->
                val name = a.names["rus"] ?: a.names["ru"] ?: a.names.values.firstOrNull { it.isNotBlank() }
                OfflineChapter(name?.takeIf { it.isNotBlank() } ?: "Сцена ${i + 1}", a.startNs / 1_000_000L)
            }
    }

    private fun parseAtom(r: Reader, end: Long): Atom {
        var start = 0L
        var hidden = false
        val names = LinkedHashMap<String, String>()
        while (r.pos < end) {
            val id = r.id()
            val sz = r.size()
            val cEnd = r.pos + sz
            when (id) {
                ID_TIME_START -> start = r.uint(sz)
                ID_FLAG_HIDDEN -> hidden = r.uint(sz) == 1L
                ID_FLAG_ENABLED -> if (r.uint(sz) == 0L) hidden = true
                ID_DISPLAY -> {
                    var text: String? = null
                    var lang = "und"
                    while (r.pos < cEnd) {
                        val did = r.id()
                        val dsz = r.size()
                        val dEnd = r.pos + dsz
                        when (did) {
                            ID_STRING -> text = r.string(dsz)
                            ID_LANGUAGE, ID_LANGUAGE_BCP47 -> lang = r.string(dsz).lowercase().substringBefore('-')
                        }
                        r.pos = dEnd
                    }
                    if (text != null && lang !in names) names[lang] = text
                }
            }
            r.pos = cEnd
        }
        return Atom(start, names, hidden)
    }
}
