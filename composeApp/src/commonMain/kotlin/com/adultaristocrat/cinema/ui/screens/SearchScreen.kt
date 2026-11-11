package com.adultaristocrat.cinema.ui.screens

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

class SearchModel(state: AppState) {
    var query by mutableStateOf("")
    var lastSearched = ""
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
    }

    PagedGrid(
        model = model.grid,
        api = state.api,
        modifier = Modifier.fillMaxSize(),
        autoFocus = false,
        emptyText = if (model.query.trim().length < 2) "Введите название фильма" else "Ничего не найдено",
        onItemClick = { state.navigate(Route.Detail(it.id)) },
        header = {
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                AATextField(
                    value = model.query,
                    onValueChange = { model.query = it },
                    singleLine = true,
                    placeholder = { Text("Поиск по библиотеке") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AA.TextDim) },
                    colors = aaFieldColors(),
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        },
    )
}
