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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.AAButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.AppTab
import com.adultaristocrat.cinema.Route
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.ItemsResult
import com.adultaristocrat.cinema.ui.AAChip
import com.adultaristocrat.cinema.ui.GridModel
import com.adultaristocrat.cinema.ui.PagedGrid
import com.adultaristocrat.cinema.ui.SectionTitle

class BrowseModel(state: AppState, val tab: AppTab?, val folder: Route.Folder?) {
    private val api = state.api
    /** Экран уже показывали — при возврате История и Избранное обновляются */
    var shownOnce = false

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
                    val ids = state.offline.state.positions.filterValues { it != 0L }.keys
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
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var removeFav by remember { mutableStateOf<BaseItem?>(null) }

    // История и избранное меняются, пока мы смотрим фильмы — обновляем при каждом открытии
    LaunchedEffect(Unit) {
        if (model.shownOnce && (tab == AppTab.HISTORY || tab == AppTab.FAVORITES)) model.grid.reload()
        model.shownOnce = true
    }

    removeFav?.let { item ->
        val okFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = { removeFav = null }) {
            Column(
                Modifier.widthIn(max = 460.dp).background(Color(0xF5080000), RoundedCornerShape(18.dp))
                    .border(1.dp, AA.Border, RoundedCornerShape(18.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Удалить?", color = AA.Text, style = MaterialTheme.typography.titleLarge)
                Text("Удалить «${item.name ?: "фильм"}» из избранного?", color = AA.TextDim)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AAButton("Отмена", Icons.Rounded.Close, { removeFav = null }, primary = false)
                    AAButton("Удалить", Icons.Rounded.Delete, {
                        removeFav = null
                        state.scope.launch {
                            val ok = runCatching { state.api.setFavorite(item.id, false) }.isSuccess
                            if (ok) {
                                model.grid.items.removeAll { it.id == item.id }
                                state.showToast("Удалено из избранного")
                            } else {
                                state.showToast("Не удалось удалить — нет связи с сервером")
                            }
                        }
                    }, Modifier.focusRequester(okFocus))
                }
            }
        }
        LaunchedEffect(item.id) {
            delay(100)
            runCatching { okFocus.requestFocus() }
        }
    }

    /** Очистка истории: на сервере фильмы помечаются «не просмотрено» (сбрасывается и позиция) */
    fun clearHistory() {
        confirmClear = false
        clearing = true
        val items = model.grid.items.toList()
        state.scope.launch {
            try {
                if (state.offlineMode) {
                    items.forEach { state.offline.savePosition(it.id, 0, 0) }
                    state.offline.flush()
                } else {
                    items.forEach { runCatching { state.api.setPlayed(it.id, false) } }
                }
                state.showToast("История просмотров очищена")
            } finally {
                clearing = false
                model.grid.reload()
            }
        }
    }

    if (confirmClear) {
        val okFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = { confirmClear = false }) {
            Column(
                Modifier.widthIn(max = 460.dp).background(Color(0xF5080000), RoundedCornerShape(18.dp))
                    .border(1.dp, AA.Border, RoundedCornerShape(18.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Очистить историю просмотров?", color = AA.Text, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Фильмы пропадут из истории, а места остановки сбросятся — «Продолжить» начнёт их сначала.",
                    color = AA.TextDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AAButton("Очистить", Icons.Rounded.DeleteSweep, { clearHistory() }, Modifier.focusRequester(okFocus))
                    AAButton("Отмена", Icons.Rounded.Close, { confirmClear = false }, primary = false)
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(100)
            runCatching { okFocus.requestFocus() }
        }
    }

    PagedGrid(
        model = model.grid,
        api = state.api,
        wide = model.wide,
        emptyText = model.emptyText,
        modifier = Modifier.fillMaxSize(),
        onItemLongClick = if (tab == AppTab.FAVORITES) ({ item -> removeFav = item }) else null,
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
                    // Корзина — очистить историю просмотров
                    if (tab == AppTab.HISTORY && model.grid.items.isNotEmpty()) {
                        AAChip(
                            if (clearing) "Очистка…" else "", false,
                            { if (!clearing) confirmClear = true }, icon = Icons.Rounded.DeleteSweep,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        },
    )
}
