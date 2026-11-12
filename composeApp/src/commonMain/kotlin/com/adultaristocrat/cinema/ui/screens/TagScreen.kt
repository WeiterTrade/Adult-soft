package com.adultaristocrat.cinema.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adultaristocrat.cinema.AppConfig
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.Route
import com.adultaristocrat.cinema.TagKind
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.ItemFilter
import com.adultaristocrat.cinema.api.ItemsResult
import com.adultaristocrat.cinema.ui.Countries
import com.adultaristocrat.cinema.ui.FlagImage
import com.adultaristocrat.cinema.ui.GridModel
import com.adultaristocrat.cinema.ui.PagedGrid
import com.adultaristocrat.cinema.ui.SectionTitle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Все фильмы одного жанра или одной страны */
class TagModel(private val state: AppState, private val route: Route.Tag) {
    private val api = state.api
    private val sortBy = "ProductionYear,PremiereDate,SortName"
    private val lock = Mutex()
    private var index: List<BaseItem>? = null

    /** Ключ страны (ISO-код или название) */
    private val countryKey = Countries.key(route.value)

    private suspend fun countryMatches(): List<BaseItem> = lock.withLock {
        val cached = index
        if (cached != null) return@withLock cached
        val list = api.facetIndex(null, sortBy, "Descending")
            .filter { m -> m.productionLocations.any { Countries.key(it) == countryKey } }
        index = list
        list
    }

    val grid = GridModel(state.scope) { start, limit ->
        val offline = state.offlineMode || route.offline
        when {
            offline -> state.offline.query(
                if (route.kind == TagKind.GENRE) ItemFilter(genres = setOf(route.value))
                else ItemFilter(countries = setOf(countryKey)),
                null, start, limit,
            )
            route.kind == TagKind.GENRE -> api.items(
                types = AppConfig.CONTENT_TYPES, genres = setOf(route.value),
                sortBy = sortBy, sortOrder = "Descending", startIndex = start, limit = limit,
            )
            else -> {
                // Сервер не фильтрует по странам — отбираем сами, карточки догружаем по Id
                val all = countryMatches()
                val page = all.drop(start).take(limit)
                val loaded: Map<String, BaseItem> = if (page.isEmpty()) emptyMap() else api.items(
                    ids = page.joinToString(",") { it.id }, types = null,
                    sortBy = sortBy, sortOrder = "Descending", limit = page.size,
                ).items.associateBy { it.id }
                ItemsResult(page.mapNotNull { loaded[it.id] }, all.size)
            }
        }
    }
}

@Composable
fun TagScreen(state: AppState, route: Route.Tag) {
    val model = state.model(route) { TagModel(state, route) }
    val country = if (route.kind == TagKind.COUNTRY) Countries.find(route.value) else null
    PagedGrid(
        model = model.grid,
        api = state.api,
        modifier = Modifier.fillMaxSize(),
        emptyText = "Других фильмов здесь нет",
        onItemClick = { state.navigate(Route.Detail(it.id)) },
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (country != null) {
                        FlagImage(country)
                        Spacer(Modifier.width(10.dp))
                    }
                    val total = model.grid.total
                    val name = if (route.kind == TagKind.GENRE) "Жанр: ${route.title}" else route.title
                    SectionTitle(if (total > 0) "$name ($total)" else name, Modifier.weight(1f))
                }
            }
        },
    )
}
