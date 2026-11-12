package com.adultaristocrat.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adultaristocrat.cinema.AppConfig
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.ItemFilter
import com.adultaristocrat.cinema.ui.AATextField
import com.adultaristocrat.cinema.ui.Countries
import com.adultaristocrat.cinema.ui.FlagImage
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.AAButton
import com.adultaristocrat.cinema.ui.AAChip
import com.adultaristocrat.cinema.ui.LocalUi
import kotlinx.coroutines.delay

private const val MAX_YEARS = 5

/** Страна, на которую есть фильмы */
data class CountryOption(val key: String, val title: String, val count: Int) {
    val country: Countries.Country? get() = Countries.byKey(key)
    val letter: String get() = title.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
}

/** Что реально есть в разделе — только это показываем в фильтре */
data class FilterFacets(
    val genres: List<String>,
    val years: List<Int>,
    val countries: List<CountryOption>,
) {
    companion object {
        fun from(items: List<BaseItem>, genres: List<String>): FilterFacets {
            val years = items.mapNotNull { it.productionYear }.filter { it > 0 }.distinct().sortedDescending()
            val counts = LinkedHashMap<String, Pair<String, Int>>()
            items.forEach { item ->
                item.productionLocations.map { it.trim() }.filter { it.isNotEmpty() }
                    .associateBy { Countries.key(it) }
                    .forEach { (k, name) ->
                        val prev = counts[k]
                        counts[k] = (prev?.first ?: Countries.title(k, name)) to ((prev?.second ?: 0) + 1)
                    }
            }
            val countries = counts.map { (k, v) -> CountryOption(k, v.first, v.second) }.sortedBy { it.title.lowercase() }
            return FilterFacets(genres, years, countries)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterDialog(
    initial: ItemFilter,
    facets: FilterFacets?,
    onApply: (ItemFilter) -> Unit,
) {
    var types by remember { mutableStateOf(initial.types) }
    var selGenres by remember { mutableStateOf(initial.genres) }
    var years by remember { mutableStateOf(initial.years) }
    var countries by remember { mutableStateOf(initial.countries) }
    val ui = LocalUi.current
    val firstFocus = remember { FocusRequester() }

    fun current() = ItemFilter(types, selGenres, years, countries)

    Dialog(
        onDismissRequest = { onApply(current()) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(if (ui.isPhone) 0.96f else 0.86f)
                .fillMaxHeight(0.9f)
                .background(Color(0xF5080000), RoundedCornerShape(18.dp))
                .border(1.dp, AA.Border, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (AppConfig.SHOW_SERIES) {
                    Label("Тип контента")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AAChip("Все", types.isEmpty(), { types = emptySet() })
                        AAChip("Фильмы", "Movie" in types, { types = toggle(types, "Movie") })
                        AAChip("Сериалы", "Series" in types, { types = toggle(types, "Series") })
                    }
                }

                if (facets == null) {
                    CircularProgressIndicator(color = AA.Red, modifier = Modifier.padding(16.dp))
                } else {
                    Label("Жанры")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AAChip("Все жанры", selGenres.isEmpty(), { selGenres = emptySet() }, Modifier.focusRequester(firstFocus))
                        facets.genres.forEach { g ->
                            AAChip(g, g in selGenres, { selGenres = toggle(selGenres, g) })
                        }
                    }

                    if (facets.years.isNotEmpty()) {
                        Row {
                            Label("Годы")
                            Text(
                                "  можно выбрать до $MAX_YEARS",
                                color = AA.TextDim, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 18.dp),
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AAChip("Все годы", years.isEmpty(), { years = emptySet() })
                            // Только годы, на которые есть фильмы
                            facets.years.forEach { y ->
                                AAChip(y.toString(), y in years, {
                                    years = if (y in years) years - y
                                    else if (years.size < MAX_YEARS) years + y
                                    else years
                                })
                            }
                        }
                    }

                    if (facets.countries.isNotEmpty()) {
                        Label("Страны")
                        CountryPicker(facets.countries, countries) { countries = it }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                if (ui.isTv) "«Назад» тоже применяет фильтр" else "",
                color = AA.TextDim, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AAButton("Сбросить", Icons.Rounded.Close, { onApply(ItemFilter()) }, primary = false)
                AAButton("Применить", Icons.Rounded.Check, { onApply(current()) })
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun CountryChip(o: CountryOption, withCount: Boolean, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    val c = o.country
    val flag: (@Composable () -> Unit)? = if (c == null) null else { { FlagImage(c) } }
    AAChip(
        if (withCount) "${o.title}  ${o.count}" else o.title,
        o.key in selected,
        { onChange(toggle(selected, o.key)) },
        leading = flag,
    )
}

/** Выбор стран: поиск, популярные, алфавит и список с флагами */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountryPicker(
    options: List<CountryOption>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var letter by remember { mutableStateOf<String?>(null) }
    val chip = Arrangement.spacedBy(8.dp)

    AATextField(
        value = query,
        onValueChange = { query = it; if (it.isNotEmpty()) letter = null },
        singleLine = true,
        placeholder = { Text("Поиск страны…") },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AA.TextDim) },
        colors = aaFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))

    // Выбранные — всегда сверху, чтобы было видно, что отмечено
    val chosen = options.filter { it.key in selected }
    if (chosen.isNotEmpty()) {
        FlowRow(horizontalArrangement = chip, verticalArrangement = chip) {
            AAChip("Все страны", false, { onChange(emptySet()) }, icon = Icons.Rounded.Close)
            chosen.forEach { CountryChip(it, false, selected, onChange) }
        }
        Spacer(Modifier.height(10.dp))
    }

    val q = query.trim().lowercase()
    if (q.isEmpty() && letter == null && options.size > 8) {
        // Популярные — страны, где больше всего фильмов
        Text("Популярные", color = AA.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(horizontalArrangement = chip, verticalArrangement = chip) {
            options.sortedByDescending { it.count }.take(6).forEach { CountryChip(it, true, selected, onChange) }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (q.isEmpty() && options.size > 8) {
        val letters = options.map { it.letter }.distinct().sorted()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AAChip("Все", letter == null, { letter = null })
            letters.forEach { l -> AAChip(l, letter == l, { letter = if (letter == l) null else l }) }
        }
        Spacer(Modifier.height(10.dp))
    }

    val list = options.filter { o ->
        (letter == null || o.letter == letter) &&
            (q.isEmpty() || o.title.lowercase().contains(q) || o.key.contains(q))
    }
    if (list.isEmpty()) {
        Text("Ничего не найдено", color = AA.TextDim)
    } else {
        FlowRow(horizontalArrangement = chip, verticalArrangement = chip) {
            list.forEach { CountryChip(it, true, selected, onChange) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = AA.Text,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

private fun <T> toggle(set: Set<T>, v: T): Set<T> = if (v in set) set - v else set + v
