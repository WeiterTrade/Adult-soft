package com.adultaristocrat.cinema.offline

/**
 * Терпимый разбор Kodi/tinyMediaManager .nfo.
 * Не падает на HTML-сущностях (&nbsp;), «мусоре» после </movie>, ссылках в конце файла,
 * BOM и разных кодировках (UTF-8, UTF-16, Windows-1251).
 */
class Nfo internal constructor(private val top: String, private val full: String) {

    private fun rx(tag: String) = Regex(
        "<$tag(?:\\s[^>]*)?>(.*?)</$tag\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun text(tag: String): String? = rx(tag).find(top)?.groupValues?.get(1)?.let { clean(it) }?.takeIf { it.isNotEmpty() }

    fun list(tag: String): List<String> = rx(tag).findAll(top)
        .flatMap { clean(it.groupValues[1]).split(" / ", "/", "|", ";").asSequence() }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    fun rating(): Float? {
        // Простой вариант: <rating>7.4</rating>
        rx("rating").find(top)?.groupValues?.get(1)?.takeIf { !it.contains('<') }
            ?.let { clean(it).replace(',', '.').toFloatOrNull() }?.let { return it }
        // Новый вариант: <ratings><rating name="imdb" default="true"><value>7.4</value></rating></ratings>
        val block = rx("ratings").find(top)?.groupValues?.get(1) ?: return null
        val items = Regex(
            "<rating(\\s[^>]*)?>(.*?)</rating\\s*>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(block).toList()
        fun value(s: String) = rx("value").find(s)?.groupValues?.get(1)?.let { clean(it) }?.replace(',', '.')?.toFloatOrNull()
        items.firstOrNull { it.groupValues[1].contains("default=\"true\"", ignoreCase = true) }?.let { value(it.groupValues[2]) }?.let { return it }
        return items.firstNotNullOfOrNull { value(it.groupValues[2]) }
    }

    /** (имя, роль, фото) */
    fun actors(): List<Triple<String, String?, String?>> = rx("actor").findAll(full).mapNotNull { m ->
        val a = m.groupValues[1]
        fun child(t: String) = rx(t).find(a)?.groupValues?.get(1)?.let { clean(it) }?.takeIf { it.isNotEmpty() }
        val name = child("name") ?: return@mapNotNull null
        Triple(name, child("role"), child("thumb"))
    }.toList()

    companion object {
        private val entities = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
            "laquo" to "«", "raquo" to "»", "mdash" to "—", "ndash" to "–", "hellip" to "…", "copy" to "©",
        )

        fun clean(raw: String): String {
            var s = raw.trim()
            // CDATA
            s = s.replace(Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL), "$1")
            // Сущности
            s = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(s) { m ->
                val e = m.groupValues[1]
                when {
                    e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                    e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                    else -> entities[e.lowercase()] ?: m.value
                }
            }
            // HTML-теги внутри описания (<br>, <i>)
            s = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
            return s.trim()
        }

        private fun decode(bytes: ByteArray): String {
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).removePrefix("\uFEFF")
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).removePrefix("\uFEFF")
            val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
            val declared = Regex("encoding\\s*=\\s*[\"']([A-Za-z0-9_\\-]+)[\"']").find(head)?.groupValues?.get(1)
            if (declared != null && !declared.equals("utf-8", true) && !declared.equals("utf8", true)) {
                runCatching { return String(bytes, charset(declared)) }
            }
            val utf8 = String(bytes, Charsets.UTF_8)
            if (utf8.contains('\uFFFD')) {
                runCatching { return String(bytes, charset("windows-1251")) }
            }
            return utf8
        }

        fun parse(bytes: ByteArray): Nfo? {
            val text = decode(bytes)
            // Корень: <movie>…</movie> (или любой другой корневой тег Kodi)
            val root = Regex(
                "<(movie|episodedetails|tvshow|musicvideo)(?:\\s[^>]*)?>(.*)</\\1\\s*>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(text)?.groupValues?.get(2) ?: return null
            // Для полей верхнего уровня убираем вложенные блоки, где есть свои name/title/thumb
            val top = root
                .replace(Regex("<actor(?:\\s[^>]*)?>.*?</actor\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                .replace(Regex("<fileinfo(?:\\s[^>]*)?>.*?</fileinfo\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                .replace(Regex("<set(?:\\s[^>]*)?>.*?</set\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            return Nfo(top, root)
        }
    }
}
