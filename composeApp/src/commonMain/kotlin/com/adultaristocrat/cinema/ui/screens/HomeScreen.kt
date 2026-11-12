package com.adultaristocrat.cinema.ui.screens

import com.adultaristocrat.cinema.AppConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.Route
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.ItemFilter
import com.adultaristocrat.cinema.api.ItemsResult
import com.adultaristocrat.cinema.ui.Countries
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.adultaristocrat.cinema.ui.AAChip
import com.adultaristocrat.cinema.ui.GridModel
import com.adultaristocrat.cinema.ui.LocalUi
import com.adultaristocrat.cinema.ui.PagedGrid
import com.adultaristocrat.cinema.ui.SectionTitle
import com.adultaristocrat.cinema.ui.WideCard
import kotlinx.coroutines.launch

/** Что показывать на главной */
sealed class HomeMode {
    data object All : HomeMode()
    data object Latest : HomeMode()
    data class Library(val id: String) : HomeMode()
    /** Фильмы из сетевых папок */
    data object Offline : HomeMode()
}

class HomeModel(private val state: AppState) {
    private val api = state.api
    var views by mutableStateOf<List<BaseItem>>(emptyList())
    var collections by mutableStateOf<List<BaseItem>>(emptyList())
    var mode by mutableStateOf<HomeMode>(if (state.offlineMode) HomeMode.Offline else HomeMode.Latest)
    var filter by mutableStateOf(ItemFilter())
    /** Что реально есть в разделе: жанры, годы, страны (null — загружается) */
    var facets by mutableStateOf<FilterFacets?>(null)
    var showFilter by mutableStateOf(false)

    // Лёгкий список фильмов текущего раздела — для фильтра по странам и списков годов/стран
    private val indexLock = Mutex()
    private var index: List<BaseItem>? = null
    private var indexMode: HomeMode? = null

    private fun sortOf(m: HomeMode): Pair<String, String> = when (m) {
        HomeMode.All -> "ProductionYear,PremiereDate,SortName" to "Descending"
        else -> "DateCreated,SortName" to "Descending"
    }

    private suspend fun index(): List<BaseItem> = indexLock.withLock {
        val m = mode
        val cached = index
        if (cached != null && indexMode == m) return@withLock cached
        val (sortBy, order) = sortOf(m)
        val list: List<BaseItem> = when (m) {
            HomeMode.Offline -> state.offline.items()
            HomeMode.All, HomeMode.Latest -> api.facetIndex(null, sortBy, order)
            is HomeMode.Library -> api.facetIndex(m.id, sortBy, order)
        }
        index = list
        indexMode = m
        list
    }

    private fun matches(item: BaseItem, f: ItemFilter): Boolean =
        (f.genres.isEmpty() || item.genres.any { g -> g in f.genres }) &&
            (f.years.isEmpty() || item.productionYear in f.years) &&
            (f.countries.isEmpty() || item.productionLocations.any { c -> Countries.key(c) in f.countries })

    val grid = GridModel(state.scope) { start, limit ->
        val f = filter
        val types = f.types.takeIf { it.isNotEmpty() }?.joinToString(",") ?: AppConfig.CONTENT_TYPES
        val m = mode
        val (sortBy, order) = sortOf(m)
        when {
            m == HomeMode.Offline -> state.offline.query(f, null, start, limit)
            // Сервер не умеет фильтровать по странам — отбираем сами, а карточки догружаем по Id
            f.countries.isNotEmpty() -> {
                val all = index().filter { matches(it, f) }
                val page = all.drop(start).take(limit)
                val loaded = if (page.isEmpty()) emptyList() else api.items(
                    ids = page.joinToString(",") { it.id }, types = null,
                    sortBy = sortBy, sortOrder = order, limit = page.size,
                ).items.associateBy { it.id }
                ItemsResult(page.mapNotNull { loaded[it.id] }, all.size)
            }
            else -> api.items(
                parentId = (m as? HomeMode.Library)?.id, types = types, sortBy = sortBy, sortOrder = order,
                genres = f.genres, years = f.years, startIndex = start, limit = limit,
            )
        }
    }

    init {
        if (!state.offlineMode) loadServerData()
    }

    private fun loadServerData() {
        state.scope.launch { runCatching { api.views() }.onSuccess { views = it } }
        state.scope.launch {
            runCatching { api.items(types = "BoxSet", sortBy = "DateCreated", sortOrder = "Descending", limit = 20) }
                .onSuccess { collections = it.items }
        }
    }

    fun select(m: HomeMode) {
        if (m == mode) return
        mode = m
        facets = null
        grid.reload()
    }

    fun openFilter() {
        showFilter = true
        if (facets == null) state.scope.launch {
            val list = runCatching { index() }.getOrDefault(emptyList())
            var genres = list.flatMap { it.genres }.distinct().sorted()
            if (genres.isEmpty() && !state.offlineMode) genres = runCatching { api.genres() }.getOrDefault(emptyList())
            facets = FilterFacets.from(list, genres)
        }
    }

    fun applyFilter(f: ItemFilter) {
        showFilter = false
        if (f != filter) {
            filter = f
            grid.reload()
        }
    }
}

@Composable
fun HomeScreen(state: AppState, route: Route.Tab) {
    val model = state.model(route) { HomeModel(state) }
    val ui = LocalUi.current
    val api = state.api

    PagedGrid(
        model = model.grid,
        api = api,
        onItemClick = { state.navigate(Route.Detail(it.id)) },
        modifier = Modifier.fillMaxSize(),
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "chips") {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        if (!state.offlineMode) {
                            item { AAChip("Новые", model.mode == HomeMode.Latest, { model.select(HomeMode.Latest) }) }
                            item { AAChip("Все", model.mode == HomeMode.All, { model.select(HomeMode.All) }) }
                            items(model.views, key = { it.id }) { v ->
                                AAChip(v.name ?: "", model.mode == HomeMode.Library(v.id), { model.select(HomeMode.Library(v.id)) })
                            }
                        }
                        if (state.offlineMode && state.offline.hasMovies) {
                            item {
                                AAChip(
                                    "Офлайн", model.mode == HomeMode.Offline, { model.select(HomeMode.Offline) },
                                    icon = androidx.compose.material.icons.Icons.Rounded.FolderShared,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    AAChip(
                        if (model.filter.isActive) "Фильтр •" else "", model.filter.isActive,
                        { model.openFilter() }, icon = Icons.Rounded.FilterList,
                    )
                }
            }
            if (model.collections.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "collections") {
                    Column {
                        SectionTitle("Подборки")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        ) {
                            items(model.collections, key = { it.id }) { c ->
                                WideCard(
                                    c, api,
                                    onClick = { state.navigate(Route.Folder(c.id, c.name ?: "")) },
                                    modifier = Modifier.width(if (ui.isPhone) 240.dp else 300.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                val title = when (val m = model.mode) {
                    HomeMode.Latest -> "Новые поступления"
                    HomeMode.All -> "Все фильмы"
                    is HomeMode.Library -> model.views.firstOrNull { it.id == m.id }?.name ?: "Библиотека"
                    HomeMode.Offline -> "Офлайн-библиотека"
                }
                SectionTitle(title)
            }
        },
    )

    if (model.showFilter) {
        FilterDialog(model.filter, model.facets) { model.applyFilter(it) }
    }
}
