package com.adultaristocrat.cinema.offline

/**
 * Биография из текстового файла (.txt / .md) — ровно в том виде, как её присылает чат:
 *
 *   Биография
 *   Абзац 1…
 *   Абзац 2…
 *   Краткая справка (для дополнительных полей метаданных):
 *   * Настоящее имя: …
 *   * Дата рождения: 21 сентября 1981 г.
 *   * Место рождения: …
 *   * Рост: 175 см
 *
 * Имя человека берётся из имени файла: Phoenix_Marie.txt → Phoenix Marie.
 */
object BioParser {
    private val months = listOf("янв", "фев", "мар", "апр", "ма", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

    fun parse(fileName: String, raw: String): OfflineBio? {
        val name = fileName.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        if (name.isEmpty()) return null
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').lines().map { clean(it) }
        val refIdx = lines.indexOfFirst { it.lowercase().startsWith("краткая справка") }
        val bodyLines = (if (refIdx >= 0) lines.subList(0, refIdx) else lines)
            .filter { it.isNotBlank() }
            .filterIndexed { i, l -> !(i == 0 && l.trimEnd(':').equals("биография", ignoreCase = true)) }
        val text = bodyLines.joinToString("\n").trim().takeIf { it.isNotEmpty() }

        var realName: String? = null
        var aliases = emptyList<String>()
        var birth: String? = null
        var death: String? = null
        var place: String? = null
        val facts = mutableListOf<List<String>>()
        if (refIdx >= 0) {
            for (l in lines.drop(refIdx + 1)) {
                val item = l.trimStart('*', '-', '•', '·', ' ', '\t').trim()
                val i = item.indexOf(':')
                if (i <= 0) continue
                val key = item.substring(0, i).trim()
                val value = item.substring(i + 1).trim()
                if (value.isEmpty()) continue
                when (key.lowercase()) {
                    "настоящее имя", "полное имя" -> realName = value
                    "псевдонимы", "псевдоним", "также известна как", "также известен как" ->
                        aliases = value.split(',', ';', '/').map { it.trim() }.filter { it.isNotEmpty() }
                    "дата рождения" -> birth = date(value) ?: value
                    "дата смерти" -> death = date(value) ?: value
                    "место рождения" -> place = value
                    else -> facts += listOf(key, value)
                }
            }
        }
        if (aliases.isNotEmpty()) facts.add(0, listOf("Псевдонимы", aliases.joinToString(", ")))
        return OfflineBio(name, text, realName, aliases, birth, death, place, facts)
    }

    private fun clean(s: String): String = s.replace("**", "").replace("__", "").trim().trimStart('#').trim()

    /** «21 сентября 1981 г.», «21.09.1981», «1981-09-21» → 1981-09-21 */
    fun date(s: String): String? {
        Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(s)?.let { m ->
            return iso(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})").find(s)?.let { m ->
            return iso(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        Regex("(\\d{1,2})\\s+([А-Яа-яЁё]+)\\s+(\\d{4})").find(s)?.let { m ->
            val w = m.groupValues[2].lowercase()
            val month = when {
                w.startsWith("мар") -> 3
                w.startsWith("ма") -> 5
                else -> months.indexOfFirst { w.startsWith(it) } + 1
            }
            if (month > 0) return iso(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
        }
        return null
    }

    private fun iso(y: Int, m: Int, d: Int) = "%04d-%02d-%02d".format(y, m, d)
}
