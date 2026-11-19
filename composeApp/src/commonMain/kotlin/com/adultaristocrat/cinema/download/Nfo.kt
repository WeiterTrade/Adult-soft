package com.adultaristocrat.cinema.download

import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.JellyfinApi

/** .nfo (Kodi) и текст биографии для скачанной библиотеки */
internal object Nfo {
    private fun x(s: String?): String = (s ?: "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun build(item: BaseItem): String {
        val sb = StringBuilder()
        fun tag(name: String, value: String?) {
            if (!value.isNullOrBlank()) sb.append("    <$name>").append(x(value.trim())).append("</$name>\n")
        }
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<movie>\n")
        tag("title", item.name)
        tag("originaltitle", item.originalTitle)
        tag("sorttitle", item.sortName)
        tag("mpaa", item.officialRating)
        tag("plot", item.overview?.replace(Regex("<[^>]*>"), ""))
        tag("tagline", item.taglines.firstOrNull())
        tag("premiered", item.premiereDate?.take(10))
        tag("year", item.productionYear?.toString())
        item.productionLocations.forEach { tag("country", it) }
        item.studios.forEach { tag("studio", it.name) }
        item.people.filter { it.type == "Director" }.forEach { tag("director", it.name) }
        item.genres.forEach { tag("genre", it) }
        item.communityRating?.takeIf { it > 0 }?.let { tag("rating", it.toString()) }
        item.runTimeTicks?.let { tag("runtime", (it / JellyfinApi.TICKS_PER_MS / 60_000).toString()) }
        item.providerIds.forEach { (k, v) -> sb.append("    <uniqueid type=\"").append(x(k.lowercase())).append("\">").append(x(v)).append("</uniqueid>\n") }
        item.people.filter { it.type == "Actor" || it.type == "GuestStar" }.forEach { p ->
            val name = p.name ?: return@forEach
            sb.append("    <actor>\n")
            sb.append("        <name>").append(x(name)).append("</name>\n")
            if (!p.role.isNullOrBlank()) sb.append("        <role>").append(x(p.role)).append("</role>\n")
            if (p.primaryImageTag != null) {
                sb.append("        <thumb>.actors/").append(x(LibraryDownloader.actorFile(name))).append(".jpg</thumb>\n")
            }
            sb.append("    </actor>\n")
        }
        sb.append("</movie>\n")
        return sb.toString()
    }

    /** Сцены в формате Matroska XML (его понимает и mkvmerge: --chapters файл.xml) */
    fun chaptersXml(item: BaseItem): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Chapters>\n  <EditionEntry>\n")
        item.chapters.forEachIndexed { i, c ->
            val ms = c.startPositionTicks / JellyfinApi.TICKS_PER_MS
            val h = ms / 3_600_000
            val m = ms / 60_000 % 60
            val s = ms / 1000 % 60
            val f = ms % 1000
            fun two(v: Long) = v.toString().padStart(2, '0')
            val time = "${two(h)}:${two(m)}:${two(s)}.${f.toString().padStart(3, '0')}000000"
            sb.append("    <ChapterAtom>\n")
            sb.append("      <ChapterTimeStart>").append(time).append("</ChapterTimeStart>\n")
            sb.append("      <ChapterDisplay>\n        <ChapterString>")
                .append(x(c.name?.takeIf { it.isNotBlank() } ?: "Сцена ${i + 1}")).append("</ChapterString>\n")
            sb.append("        <ChapterLanguage>rus</ChapterLanguage>\n      </ChapterDisplay>\n    </ChapterAtom>\n")
        }
        sb.append("  </EditionEntry>\n</Chapters>\n")
        return sb.toString()
    }

    /** Биография в том же виде, что и файлы в .bio (см. tools/bio) */
    fun bio(p: BaseItem): String? {
        val text = p.overview?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            ?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty()
        val born = p.premiereDate?.take(10)?.let(::ruDate)
        val died = p.endDate?.take(10)?.let(::ruDate)
        val place = p.productionLocations.firstOrNull()
        if (text.isEmpty() && born == null && place == null) return null
        val sb = StringBuilder()
        if (!text.startsWith("Биография", ignoreCase = true)) sb.append("Биография\n")
        sb.append(text)
        // Если «Краткая справка» уже есть в тексте с сервера — не дублируем
        if (!text.contains("Краткая справка", ignoreCase = true) && (born != null || died != null || place != null)) {
            sb.append("\n\nКраткая справка:\n\n")
            born?.let { sb.append("* Дата рождения: ").append(it).append('\n') }
            died?.let { sb.append("* Дата смерти: ").append(it).append('\n') }
            place?.let { sb.append("* Место рождения: ").append(it).append('\n') }
        }
        return sb.toString().trim() + "\n"
    }

    /** 1981-09-21 → 21.09.1981 */
    private fun ruDate(iso: String): String? {
        val p = iso.split("-")
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        if (y < 1800) return null
        return "${p[2]}.${p[1]}.${p[0]}"
    }
}
