package com.adultaristocrat.cinema.ui.screens

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
}

class HomeModel(private val state: AppState) {
    private val api = state.api
    var views by mutableStateOf<List<BaseItem>>(emptyList())
    var collections by mutableStateOf<List<BaseItem>>(emptyList())
    var mode by mutableStateOf<HomeMode>(HomeMode.Latest)
    var filter by mutableStateOf(ItemFilter())
    var genres by mutableStateOf<List<String>?>(null)
    var showFilter by mutableStateOf(false)

    val grid = GridModel(state.scope) { start, limit ->
        val f = filter
        val types = f.types.takeIf { it.isNotEmpty() }?.joinToString(",") ?: "Movie,Series"
        when (val m = mode) {
            HomeMode.All -> api.items(
                types = types, sortBy = "ProductionYear,PremiereDate,SortName", sortOrder = "Descending",
                genres = f.genres, years = f.years, startIndex = start, limit = limit,
            )
            HomeMode.Latest -> api.items(
                types = types, sortBy = "DateCreated,SortName", sortOrder = "Descending",
                genres = f.genres, years = f.years, startIndex = start, limit = limit,
            )
            is HomeMode.Library -> api.items(
                parentId = m.id, types = types, sortBy = "DateCreated,SortName", sortOrder = "Descending",
                genres = f.genres, years = f.years, startIndex = start, limit = limit,
            )
        }
    }

    init {
        state.scope.launch { runCatching { api.views() }.onSuccess { views = it } }
        state.scope.launch {
            runCatching { api.items(types = "BoxSet", sortBy = "DateCreated", sortOrder = "Descending", limit = 20) }
                .onSuccess { collections = it.items }
        }
    }

    fun select(m: HomeMode) {
        if (m == mode) return
        mode = m
        grid.reload()
    }

    fun openFilter() {
        showFilter = true
        if (genres == null) state.scope.launch { genres = runCatching { api.genres() }.getOrDefault(emptyList()) }
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
                        item { AAChip("Новые", model.mode == HomeMode.Latest, { model.select(HomeMode.Latest) }) }
                        item { AAChip("Все", model.mode == HomeMode.All, { model.select(HomeMode.All) }) }
                        items(model.views, key = { it.id }) { v ->
                            AAChip(v.name ?: "", model.mode == HomeMode.Library(v.id), { model.select(HomeMode.Library(v.id)) })
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
                    HomeMode.All -> "Все фильмы и сериалы"
                    is HomeMode.Library -> model.views.firstOrNull { it.id == m.id }?.name ?: "Библиотека"
                }
                SectionTitle(title)
            }
        },
    )

    if (model.showFilter) {
        FilterDialog(model.filter, model.genres, state.platform.currentYear) { model.applyFilter(it) }
    }
}
