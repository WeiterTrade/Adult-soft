package com.adultaristocrat.cinema.ui.screens

import com.adultaristocrat.cinema.ui.rememberFocusVisual
import com.adultaristocrat.cinema.ui.SectionTitle
import com.adultaristocrat.cinema.ui.LocalUi
import com.adultaristocrat.cinema.ui.AdaptivePhoto
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import com.adultaristocrat.cinema.ui.AATextField
import com.adultaristocrat.cinema.AppConfig
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.Route
import com.adultaristocrat.cinema.api.ItemsResult
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.GridModel
import com.adultaristocrat.cinema.ui.PagedGrid
import kotlinx.coroutines.delay

class SearchModel(private val state: AppState) {
    var query by mutableStateOf("")
    var lastSearched = ""
    /** Найденные актрисы и режиссёры */
    var people by mutableStateOf<List<PersonHit>>(emptyList())
    private var index: PeopleIndex? = null
    private var indexOffline = false

    suspend fun searchPeople(q: String) {
        if (q.length < 2) {
            people = emptyList()
            return
        }
        if (index == null || indexOffline != state.offlineMode) {
            indexOffline = state.offlineMode
            index = runCatching {
                if (state.offlineMode) PeopleIndex.offline(state) else PeopleIndex.online(state)
            }.getOrNull()
        }
        people = index?.search(q).orEmpty()
    }
    val grid = GridModel(state.scope) { start, limit ->
        val q = query.trim()
        if (q.length < 2) ItemsResult(emptyList(), 0)
        else if (state.offlineMode) state.offline.query(com.adultaristocrat.cinema.api.ItemFilter(), q, start, limit)
        else state.api.items(
            types = AppConfig.CONTENT_TYPES, search = q, sortBy = "SortName", sortOrder = "Ascending",
            startIndex = start, limit = limit,
        )
    }
}

@Composable
fun SearchScreen(state: AppState, route: Route.Tab) {
    val model = state.model(route) { SearchModel(state) }

    LaunchedEffect(model.query) {
        val q = model.query.trim()
        if (q == model.lastSearched) return@LaunchedEffect
        delay(450)
        model.lastSearched = q
        model.grid.reload()
        model.searchPeople(q)
    }

    PagedGrid(
        model = model.grid,
        api = state.api,
        modifier = Modifier.fillMaxSize(),
        autoFocus = false,
        emptyText = when {
            model.query.trim().length < 2 -> "Введите название фильма или имя актрисы"
            model.people.isNotEmpty() -> "Фильмов с таким названием нет"
            else -> "Ничего не найдено"
        },
        onItemClick = { state.navigate(Route.Detail(it.id)) },
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                AATextField(
                    value = model.query,
                    onValueChange = { model.query = it },
                    singleLine = true,
                    placeholder = { Text("Поиск") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AA.TextDim) },
                    colors = aaFieldColors(),
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
            if (model.people.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "people") {
                    PeopleHits(model.people) { p -> state.navigate(Route.Person(p.id, p.name)) }
                }
            }
        },
    )
}

/** Найденные актрисы: фото, имя и русское написание */
@Composable
private fun PeopleHits(people: List<PersonHit>, onClick: (PersonHit) -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.padding(bottom = 6.dp)) {
        SectionTitle("Актрисы и режиссёры")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(4.dp),
        ) {
            items(people, key = { it.id }) { p ->
                val fv = rememberFocusVisual()
                Column(
                    Modifier.width(if (ui.isPhone) 92.dp else 120.dp)
                        .clickable(interactionSource = fv.interaction, indication = null) { onClick(p) },
                ) {
                    AdaptivePhoto(
                        url = p.photo,
                        name = p.name,
                        borderWidth = if (fv.active) 3.dp else 1.dp,
                        borderColor = if (fv.active) AA.Red else AA.BorderDark,
                        initialsSize = 26.sp,
                        minRatio = 0.5f,
                        maxRatio = 1.0f,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        p.name, color = if (fv.active) AA.RedHover else AA.Text, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    p.ruName?.takeIf { it != p.name }?.let {
                        Text(it, color = AA.TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
