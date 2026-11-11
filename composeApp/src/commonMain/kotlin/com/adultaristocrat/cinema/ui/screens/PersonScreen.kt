package com.adultaristocrat.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adultaristocrat.cinema.AppConfig
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.Route
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.Countries
import com.adultaristocrat.cinema.ui.AdaptivePhoto
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import com.adultaristocrat.cinema.ui.GridModel
import com.adultaristocrat.cinema.ui.LocalUi
import com.adultaristocrat.cinema.ui.PagedGrid
import com.adultaristocrat.cinema.ui.SectionTitle
import com.adultaristocrat.cinema.ui.rememberFocusVisual
import kotlinx.coroutines.launch

class PersonModel(state: AppState, val personId: String, val name: String) {
    private val api = state.api
    var person by mutableStateOf<BaseItem?>(null)
    var bioExpanded by mutableStateOf(false)
    val photoFocus = androidx.compose.ui.focus.FocusRequester()

    /** Фильмы с этим актёром, которые есть в кинотеатре */
    val grid = GridModel(state.scope) { start, limit ->
        api.items(
            types = AppConfig.CONTENT_TYPES, personIds = personId,
            sortBy = "ProductionYear,PremiereDate,SortName", sortOrder = "Descending",
            startIndex = start, limit = limit,
        )
    }

    init {
        state.scope.launch { person = runCatching { api.person(personId) }.getOrNull() }
    }
}

private val MONTHS = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

/** "1984-11-24..." → (год, месяц, день) */
private fun parseDate(iso: String?): Triple<Int, Int, Int>? {
    val p = iso?.take(10)?.split("-") ?: return null
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    if (y < 1800) return null
    return Triple(y, m, d)
}

private fun ruDate(t: Triple<Int, Int, Int>) = "${t.third} ${MONTHS.getOrElse(t.second - 1) { "" }} ${t.first}"

private fun yearsWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m100 in 11..14 -> "лет"
        m10 == 1 -> "год"
        m10 in 2..4 -> "года"
        else -> "лет"
    }
}

private fun ageBetween(from: Triple<Int, Int, Int>, to: Triple<Int, Int, Int>): Int {
    var age = to.first - from.first
    if (to.second < from.second || (to.second == from.second && to.third < from.third)) age--
    return age
}

@Composable
fun PersonScreen(state: AppState, route: Route.Person) {
    val model = state.model(route) { PersonModel(state, route.personId, route.name) }
    val ui = LocalUi.current
    val api = state.api
    val person = model.person

    PagedGrid(
        model = model.grid,
        api = api,
        modifier = Modifier.fillMaxSize(),
        emptyText = "Других фильмов с этим актёром в кинотеатре нет",
        onItemClick = { state.navigate(Route.Detail(it.id)) },
        headerFocus = model.photoFocus,
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "person") {
                Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                    if (!ui.isTv) {
                        val fv = rememberFocusVisual()
                        Row(
                            Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (fv.active) AA.Selector else Color.Transparent)
                                .clickable(interactionSource = fv.interaction, indication = null) { state.back() }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ArrowBack, "Назад", tint = AA.Text)
                            Spacer(Modifier.width(6.dp))
                            Text("Назад", color = AA.TextDim)
                        }
                    }
                    Row {
                        // Фото: рамка подстраивается под пропорции снимка (без серых полос).
                        // Фото можно выбрать пультом — так со списка фильмов всегда можно подняться наверх.
                        val photoFv = rememberFocusVisual()
                        Box(
                            Modifier.width(if (ui.isPhone) 130.dp else 220.dp)
                                .focusRequester(model.photoFocus)
                                .focusable(interactionSource = photoFv.interaction)
                        ) {
                            AdaptivePhoto(
                                url = "${api.baseUrl}/Items/${route.personId}/Images/Primary?maxHeight=900&quality=90",
                                name = route.name,
                                cornerRadius = 16.dp,
                                borderWidth = if (photoFv.active) 3.dp else 1.5.dp,
                                borderColor = if (photoFv.active) AA.Red else AA.BorderDark,
                                initialsSize = 34.sp,
                            )
                        }
                        Spacer(Modifier.width(if (ui.isPhone) 14.dp else 24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(person?.name ?: route.name, style = MaterialTheme.typography.headlineMedium, color = AA.Text)
                            Spacer(Modifier.height(10.dp))
                            val born = parseDate(person?.premiereDate)
                            val died = parseDate(person?.endDate)
                            val today = parseDate(state.platform.todayIso)
                            if (born != null) {
                                val age = ageBetween(born, died ?: today ?: born)
                                val ageText = if (died == null && age in 1..120) " ($age ${yearsWord(age)})" else ""
                                InfoLine("Дата рождения", ruDate(born) + ageText)
                            }
                            if (died != null) InfoLine("Дата смерти", ruDate(died))
                            person?.productionLocations?.firstOrNull()?.let { place ->
                                // Место рождения обычно «Город, Страна» — переводим страну, если знаем
                                val parts = place.split(",").map { it.trim() }
                                val c = Countries.find(parts.lastOrNull())
                                val text = if (c != null) (parts.dropLast(1) + c.ru).joinToString(", ") else place
                                InfoLine("Место рождения", text)
                            }
                            val bio = person?.overview?.replace(Regex("<[^>]*>"), "")?.trim()
                            if (!bio.isNullOrEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Биография", style = MaterialTheme.typography.titleMedium, color = AA.Text)
                                Spacer(Modifier.height(4.dp))
                                val fv = rememberFocusVisual()
                                Text(
                                    bio,
                                    color = Color(0xFFD0D0D0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (model.bioExpanded) Int.MAX_VALUE else if (ui.isPhone) 6 else 8,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (fv.active) AA.Selector else Color.Transparent)
                                        .clickable(interactionSource = fv.interaction, indication = null) {
                                            model.bioExpanded = !model.bioExpanded
                                        }
                                        .padding(4.dp),
                                )
                                if (!model.bioExpanded) {
                                    Text("Показать полностью", color = AA.RedHover, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val total = model.grid.total
                    SectionTitle(if (total > 0) "Фильмы в кинотеатре ($total)" else "Фильмы в кинотеатре")
                }
            }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", color = AA.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(value, color = AA.TextDim, fontSize = 15.sp)
    }
}
