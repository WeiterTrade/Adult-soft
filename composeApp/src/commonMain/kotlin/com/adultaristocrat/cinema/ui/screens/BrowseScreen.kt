package com.adultaristocrat.cinema.ui.screens

import com.adultaristocrat.cinema.AppConfig
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.AppTab
import com.adultaristocrat.cinema.Route
import com.adultaristocrat.cinema.api.ItemsResult
import com.adultaristocrat.cinema.ui.AAChip
import com.adultaristocrat.cinema.ui.GridModel
import com.adultaristocrat.cinema.ui.PagedGrid
import com.adultaristocrat.cinema.ui.SectionTitle

class BrowseModel(state: AppState, val tab: AppTab?, val folder: Route.Folder?) {
    private val api = state.api

    val title: String = folder?.title ?: when (tab) {
        AppTab.CONTINUE -> "Продолжить просмотр"
        AppTab.RANDOM -> "Случайная подборка"
        AppTab.HISTORY -> "История просмотров"
        AppTab.FAVORITES -> "Избранное"
        AppTab.COLLECTIONS -> "Коллекции"
        else -> tab?.title ?: ""
    }

    val wide = tab == AppTab.COLLECTIONS

    val emptyText: String = when (tab) {
        AppTab.CONTINUE -> "Вы ещё ничего не начали смотреть"
        AppTab.HISTORY -> "История пуста"
        AppTab.FAVORITES -> "Добавляйте фильмы в избранное кнопкой с закладкой"
        else -> "Здесь пока пусто"
    }

    val grid = GridModel(state.scope) { start, limit ->
        when {
            folder != null -> api.items(
                parentId = folder.parentId, types = AppConfig.CONTENT_TYPES, sortBy = "ProductionYear,SortName",
                sortOrder = "Descending", startIndex = start, limit = limit,
            )
            tab == AppTab.CONTINUE -> {
                if (start > 0) ItemsResult(emptyList(), 0) else {
                    val resume = runCatching { api.resume(limit = 100).items }.getOrDefault(emptyList())
                    val next = if (AppConfig.SHOW_SERIES) runCatching { api.nextUp(limit = 50) }.getOrDefault(emptyList()) else emptyList()
                    val all = (resume + next).distinctBy { it.id }
                    ItemsResult(all, all.size)
                }
            }
            tab == AppTab.RANDOM -> {
                if (start > 0) ItemsResult(emptyList(), 0)
                else api.items(types = AppConfig.CONTENT_TYPES, sortBy = "Random", limit = 60).let { ItemsResult(it.items, it.items.size) }
            }
            // История: и досмотренные, и начатые фильмы — новые сверху
            tab == AppTab.HISTORY -> {
                if (start > 0) ItemsResult(emptyList(), 0)
                else if (state.offlineMode) {
                    val ids = state.offline.state.positions.keys
                    val list = state.offline.items().filter { it.id in ids }
                    ItemsResult(list, list.size)
                } else {
                    val played = runCatching {
                        api.items(types = AppConfig.PLAYABLE_TYPES, filters = "IsPlayed", sortBy = "DatePlayed", sortOrder = "Descending", limit = 200).items
                    }.getOrDefault(emptyList())
                    val started = runCatching {
                        api.items(types = AppConfig.PLAYABLE_TYPES, filters = "IsResumable", sortBy = "DatePlayed", sortOrder = "Descending", limit = 200).items
                    }.getOrDefault(emptyList())
                    val all = (played + started).distinctBy { it.id }
                        .sortedByDescending { it.userData?.lastPlayedDate ?: "" }
                    ItemsResult(all, all.size)
                }
            }
            tab == AppTab.FAVORITES -> api.items(
                types = AppConfig.CONTENT_TYPES, filters = "IsFavorite", sortBy = "SortName", sortOrder = "Ascending",
                startIndex = start, limit = limit,
            )
            tab == AppTab.COLLECTIONS -> api.items(
                types = "BoxSet", sortBy = "SortName", sortOrder = "Ascending", startIndex = start, limit = limit,
            )
            else -> ItemsResult(emptyList(), 0)
        }
    }
}

@Composable
fun BrowseScreen(state: AppState, route: Route, tab: AppTab?, folder: Route.Folder?) {
    val model = state.model(route) { BrowseModel(state, tab, folder) }
    PagedGrid(
        model = model.grid,
        api = state.api,
        wide = model.wide,
        emptyText = model.emptyText,
        modifier = Modifier.fillMaxSize(),
        onItemClick = { item ->
            when {
                item.type == "BoxSet" -> state.navigate(Route.Folder(item.id, item.name ?: ""))
                tab == AppTab.CONTINUE -> state.navigate(Route.Player(item.id))
                else -> state.navigate(Route.Detail(item.id))
            }
        },
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(model.title, Modifier.weight(1f))
                    if (tab == AppTab.RANDOM) {
                        AAChip("Перемешать", false, { model.grid.reload() }, icon = Icons.Rounded.Shuffle)
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        },
    )
}
