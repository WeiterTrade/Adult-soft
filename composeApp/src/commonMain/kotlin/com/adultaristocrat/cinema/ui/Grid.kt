package com.adultaristocrat.cinema.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adultaristocrat.cinema.AppConfig
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.ItemsResult
import com.adultaristocrat.cinema.api.JellyfinApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Параметры раскладки под устройство */
data class UiMetrics(
    val isTv: Boolean,
    val isPhone: Boolean,
    val isDesktop: Boolean,
    val cardMin: Dp,
    val wideMin: Dp,
    val hPad: Dp,
)

val LocalUi = staticCompositionLocalOf {
    UiMetrics(isTv = false, isPhone = true, isDesktop = false, cardMin = 100.dp, wideMin = 260.dp, hPad = 12.dp)
}

/** Постраничная загрузка списка элементов */
class GridModel(
    private val scope: CoroutineScope,
    private val pageSize: Int = AppConfig.PAGE_SIZE,
    private val loader: suspend (start: Int, limit: Int) -> ItemsResult,
) {
    val items = mutableStateListOf<BaseItem>()
    var total by mutableStateOf(-1)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val gridState = LazyGridState()
    var focusIndex by mutableStateOf(0)

    private var job: Job? = null
    private var generation = 0

    val endReached: Boolean get() = total >= 0 && items.size >= total

    fun reload() {
        job?.cancel()
        generation++
        items.clear()
        total = -1
        error = null
        loading = false
        focusIndex = 0
        scope.launch { runCatching { gridState.scrollToItem(0) } }
        loadMore()
    }

    fun loadMore() {
        if (loading || endReached || error != null) return
        loading = true
        val gen = generation
        job = scope.launch {
            try {
                val r = loader(items.size, pageSize)
                if (gen == generation) {
                    items.addAll(r.items)
                    total = when {
                        r.totalRecordCount > 0 -> r.totalRecordCount
                        r.items.size < pageSize -> items.size
                        else -> -1
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) error = e.message ?: e.toString()
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    fun retry() {
        error = null
        loadMore()
    }
}

@Composable
fun PagedGrid(
    model: GridModel,
    api: JellyfinApi,
    onItemClick: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Долгое нажатие на карточку (удержание пальца / OK, правая кнопка мыши) */
    onItemLongClick: ((BaseItem) -> Unit)? = null,
    wide: Boolean = false,
    emptyText: String = "Здесь пока пусто",
    autoFocus: Boolean = true,
    /** Куда перевести фокус по стрелке «вверх» с первого ряда (например, фото актёра) */
    headerFocus: FocusRequester? = null,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    val ui = LocalUi.current
    val requester = remember(model) { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(model) {
        if (model.items.isEmpty() && !model.loading) model.loadMore()
    }
    LaunchedEffect(model) {
        snapshotFlow { model.gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= model.gridState.layoutInfo.totalItemsCount - 15) model.loadMore() }
    }
    if (ui.isTv && autoFocus) {
        LaunchedEffect(model, model.items.isNotEmpty()) {
            if (model.items.isNotEmpty()) {
                delay(120)
                runCatching { requester.requestFocus() }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) ui.wideMin else ui.cardMin),
        state = model.gridState,
        // Пульт: «вверх» с первого ряда фильмов сначала прокручивает к шапке страницы,
        // а не перебрасывает фокус на боковую панель
        modifier = modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || e.key != Key.DirectionUp) return@onPreviewKeyEvent false
            val info = model.gridState.layoutInfo
            fun keyOf(i: Int) = "$i:"
            val firstCard = info.visibleItemsInfo.firstOrNull { (it.key as? String)?.startsWith(keyOf(0)) == true }
            val focused = info.visibleItemsInfo.firstOrNull { (it.key as? String)?.startsWith(keyOf(model.focusIndex)) == true }
            if (firstCard == null || focused == null || focused.row != firstCard.row) return@onPreviewKeyEvent false
            val atTop = model.gridState.firstVisibleItemIndex == 0 && model.gridState.firstVisibleItemScrollOffset == 0
            scope.launch {
                runCatching { model.gridState.animateScrollToItem(0) }
                if (headerFocus != null) {
                    delay(60)
                    runCatching { headerFocus.requestFocus() }
                }
            }
            headerFocus != null || !atTop
        },
        contentPadding = PaddingValues(start = ui.hPad, end = ui.hPad, top = 8.dp, bottom = if (ui.isPhone) 96.dp else 32.dp),
        horizontalArrangement = Arrangement.spacedBy(if (ui.isPhone) 2.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (ui.isPhone) 4.dp else 10.dp),
    ) {
        header?.invoke(this)
        itemsIndexed(model.items, key = { i, it -> "$i:${it.id}" }) { index, item ->
            val m = Modifier
                .onFocusChanged { if (it.isFocused) model.focusIndex = index }
                .then(if (index == model.focusIndex) Modifier.focusRequester(requester) else Modifier)
            if (wide) WideCard(item, api, onClick = { onItemClick(item) }, modifier = m.padding(4.dp))
            else PosterCard(
                item, api, onClick = { onItemClick(item) }, modifier = m, compact = ui.isPhone,
                onLongClick = onItemLongClick?.let { f -> { f(item) } },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
            when {
                model.error != null -> MessageBox("Ошибка загрузки: ${model.error}", Modifier.height(220.dp), "Повторить") { model.retry() }
                model.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AA.Red)
                }
                model.items.isEmpty() && model.endReached -> MessageBox(emptyText, Modifier.height(220.dp))
                else -> Box(Modifier.height(1.dp))
            }
        }
    }
}
