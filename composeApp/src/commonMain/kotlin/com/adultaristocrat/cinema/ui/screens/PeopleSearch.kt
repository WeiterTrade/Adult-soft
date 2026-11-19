package com.adultaristocrat.cinema.ui.screens

import com.adultaristocrat.cinema.AppState

/** Найденный человек: имя как на сервере + русское написание (из биографии) */
data class PersonHit(val id: String, val name: String, val ruName: String?, val photo: String?)

/**
 * Поиск актрис и режиссёров по имени — по-английски («Angela White») и по-русски («Анджела Уайт»).
 * Русское имя берётся из начала биографии: «Анджела Уайт (Angela White) — …».
 */
class PeopleIndex(private val entries: List<Entry>) {
    class Entry(val hit: PersonHit, val keys: List<String>)

    fun search(query: String, limit: Int = 40): List<PersonHit> {
        val words = norm(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || query.trim().length < 2) return emptyList()
        return entries.asSequence()
            .mapNotNull { e ->
                // Лучше всего — имя начинается с запроса; хуже — запрос где-то внутри
                val score = e.keys.minOfOrNull { k ->
                    when {
                        k.startsWith(words.joinToString(" ")) -> 0
                        words.all { w -> k.split(' ').any { it.startsWith(w) } } -> 1
                        words.all { w -> k.contains(w) } -> 2
                        else -> 9
                    }
                } ?: 9
                if (score < 9) e.hit to score else null
            }
            .sortedWith(compareBy({ it.second }, { it.first.name }))
            .map { it.first }
            .distinctBy { it.id }
            .take(limit)
            .toList()
    }

    companion object {
        fun norm(s: String): String = s.lowercase().replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        /** «Анджела Уайт (Angela White), настоящее имя …» → русское имя и всё, что в скобках */
        fun namesFromBio(text: String?): List<String> {
            val t = text?.replace(Regex("<[^>]*>"), "")?.trim()?.removePrefix("Биография")?.trim() ?: return emptyList()
            val first = t.lineSequence().firstOrNull { it.isNotBlank() }?.take(400) ?: return emptyList()
            val out = mutableListOf<String>()
            Regex("^([^()\\n,—–-]{2,60}?)\\s*\\(([^)]{2,80})\\)").find(first)?.let { m ->
                out += m.groupValues[1].trim()
                out += m.groupValues[2].split(',', ';').map { it.trim() }
            }
            // «настоящее имя Мелисса Мари Хатчисон (Melissa Marie Hutchison)»
            Regex("настоящее имя\\s+([^()\\n,—–]{2,60}?)\\s*\\(([^)]{2,80})\\)", RegexOption.IGNORE_CASE).find(first)?.let { m ->
                out += m.groupValues[1].trim()
                out += m.groupValues[2].trim()
            }
            return out.filter { it.isNotBlank() }
        }

        fun entry(hit: PersonHit, extra: List<String>): Entry =
            Entry(hit, (listOf(hit.name) + listOfNotNull(hit.ruName) + extra).map { norm(it) }.filter { it.isNotEmpty() }.distinct())

        /** Индекс для сервера (один раз за сеанс) */
        suspend fun online(state: AppState): PeopleIndex {
            val persons = state.api.allPersons()
            return PeopleIndex(persons.mapNotNull { p ->
                val name = p.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val names = namesFromBio(p.overview)
                entry(PersonHit(p.id, name, names.firstOrNull(), state.api.personImageUrl(p)), names)
            })
        }

        /** Индекс для офлайн-библиотеки: люди из фильмов + биографии из .bio */
        fun offline(state: AppState): PeopleIndex {
            val lib = state.offline
            val seen = LinkedHashMap<String, com.adultaristocrat.cinema.api.PersonDto>()
            lib.items().forEach { m ->
                m.people.forEach { p ->
                    val old = seen[p.id]
                    if (old == null || (old.localThumb == null && p.localThumb != null && p.type == "Actor")) seen[p.id] = p
                }
            }
            return PeopleIndex(seen.values.mapNotNull { p ->
                val name = p.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val bio = lib.state.bios[lib.normName(name)]
                val names = namesFromBio(bio?.text) + listOfNotNull(bio?.realName) + bio?.aliases.orEmpty()
                entry(PersonHit(p.id, name, names.firstOrNull(), p.localThumb), names)
            })
        }
    }
}
