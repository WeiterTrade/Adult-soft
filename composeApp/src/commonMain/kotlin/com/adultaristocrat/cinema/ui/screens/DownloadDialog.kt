package com.adultaristocrat.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.download.DownloadEntry
import com.adultaristocrat.cinema.download.LibraryDownloader
import com.adultaristocrat.cinema.offline.OfflineSource
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.AAButton
import com.adultaristocrat.cinema.ui.AAChip
import com.adultaristocrat.cinema.ui.AATextField
import kotlinx.coroutines.launch

/** Скачивание библиотеки (ПК): выбрать фильмы и папку → скачать в формате офлайн-библиотеки */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadDialog(state: AppState, onClose: () -> Unit) {
    val dl = state.downloads
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.9f)
                .background(Color(0xF5080000), RoundedCornerShape(18.dp))
                .border(1.dp, AA.Border, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Download, null, tint = AA.Red)
                Spacer(Modifier.width(10.dp))
                Text("Скачать библиотеку", color = AA.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Close, "Закрыть", tint = AA.Text,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            if (dl.running || dl.finished) ProgressView(state, onClose) else PickView(state)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PickView(state: AppState) {
    val dl = state.downloads
    val scope = rememberCoroutineScope()
    var views by remember { mutableStateOf<List<BaseItem>>(emptyList()) }
    var view by remember { mutableStateOf<BaseItem?>(null) }
    val cache = remember { mutableStateMapOf<String, List<BaseItem>>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    // id → что качать (раздел и папки запоминаются при выборе)
    val selected = remember { mutableStateMapOf<String, DownloadEntry>() }
    var dest by remember { mutableStateOf(dl.lastDestination) }

    LaunchedEffect(Unit) {
        runCatching { state.api.views() }.onSuccess { v ->
            views = v
            if (view == null) view = v.firstOrNull()
        }.onFailure { error = "Не удалось загрузить разделы" }
    }
    val v = view
    LaunchedEffect(v?.id) {
        if (v == null || cache.containsKey(v.id)) return@LaunchedEffect
        loading = true
        error = null
        runCatching { state.api.libraryItems(v.id) }
            .onSuccess { cache[v.id] = it }
            .onFailure { error = "Не удалось загрузить фильмы" }
        loading = false
    }
    val items = v?.let { cache[it.id] }.orEmpty()
    val rel = remember(items) { LibraryDownloader.relativeDirs(items) }
    val shown = if (query.isBlank()) items else items.filter { (it.name ?: "").contains(query.trim(), ignoreCase = true) }

    fun entry(it: BaseItem) = DownloadEntry(it, v?.name ?: "Фильмы", rel[it.id] ?: "")
    fun toggle(it: BaseItem) {
        if (selected.containsKey(it.id)) selected.remove(it.id) else selected[it.id] = entry(it)
    }

    // Разделы
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        views.forEach { x -> AAChip(x.name ?: "", x.id == v?.id, { view = x }) }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        AATextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Поиск по названию…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = AA.TextDim) },
            colors = aaFieldColors(), modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        val allOn = shown.isNotEmpty() && shown.all { selected.containsKey(it.id) }
        AAChip(if (allOn) "Снять все" else "Выбрать все", allOn, {
            if (allOn) shown.forEach { selected.remove(it.id) } else shown.forEach { selected[it.id] = entry(it) }
        })
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            loading -> CircularProgressIndicator(color = AA.Red, modifier = Modifier.align(Alignment.Center))
            error != null -> Text(error ?: "", color = AA.TextDim, modifier = Modifier.align(Alignment.Center))
            shown.isEmpty() -> Text("Здесь нет фильмов", color = AA.TextDim, modifier = Modifier.align(Alignment.Center))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { m ->
                    val on = selected.containsKey(m.id)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (on) Color(0x33E50914) else Color.Transparent)
                            .clickable { toggle(m) }.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (on) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = if (on) AA.Red else AA.TextDim)
                        Spacer(Modifier.width(10.dp))
                        AsyncImage(
                            model = state.api.posterUrl(m, 120), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 34.dp, height = 48.dp).clip(RoundedCornerShape(4.dp)).background(AA.SurfaceHigh),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (m.name ?: "") + (m.productionYear?.let { " ($it)" } ?: ""),
                                color = AA.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(rel[m.id] ?: "", color = AA.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        m.mediaSources.firstOrNull()?.size?.let {
                            Text(LibraryDownloader.formatSize(it), color = AA.TextDim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    // Качество скачивания
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Качество:", color = AA.Text)
        Spacer(Modifier.width(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            com.adultaristocrat.cinema.Quality.entries.forEach { q ->
                AAChip(q.title, dl.quality == q, { dl.updateQuality(q) })
            }
        }
    }
    if (dl.quality != com.adultaristocrat.cinema.Quality.ORIGINAL) {
        Text(
            "Сервер уменьшит видео до ${dl.quality.title} (${dl.quality.hint}) — файл будет в 3–10 раз меньше. " +
                "Сцены сохранятся отдельным файлом рядом с видео. Скачивание идёт со скоростью перекодирования на сервере.",
            color = AA.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
        )
    }
    Spacer(Modifier.height(10.dp))
    // Папка и запуск
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Folder, null, tint = AA.TextDim)
        Spacer(Modifier.width(8.dp))
        Text(
            dest.ifBlank { "Папка не выбрана" }, color = if (dest.isBlank()) AA.TextDim else AA.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        AAButton("Выбрать папку", Icons.Rounded.FolderOpen, {
            scope.launch { state.platform.pickFolder("Куда скачать библиотеку")?.let { dest = it; dl.rememberDestination(it) } }
        }, primary = false)
    }
    Spacer(Modifier.height(10.dp))
    val q = dl.quality
    val size = selected.values.sumOf { e ->
        if (q == com.adultaristocrat.cinema.Quality.ORIGINAL) e.item.mediaSources.firstOrNull()?.size ?: 0L
        else (q.bitrate + 128_000).toLong() / 8 * ((e.item.runTimeTicks ?: 0L) / 10_000_000L)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Выбрано: ${selected.size}" + if (size > 0) " · " + (if (q == com.adultaristocrat.cinema.Quality.ORIGINAL) "" else "≈ ") + LibraryDownloader.formatSize(size) else "",
            color = AA.Text, modifier = Modifier.weight(1f),
        )
        AAButton("Скачать", Icons.Rounded.Download, {
            if (dest.isBlank()) {
                scope.launch {
                    state.platform.pickFolder("Куда скачать библиотеку")?.let {
                        dest = it
                        dl.start(selected.values.toList(), it)
                    }
                }
            } else dl.start(selected.values.toList(), dest)
        })
    }
}

@Composable
private fun ProgressView(state: AppState, onClose: () -> Unit) {
    val dl = state.downloads
    val store = state.platform.fileStore
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (dl.running) "Скачивание: ${dl.done + 1} из ${dl.total}" else "${dl.step}: ${dl.done} из ${dl.total}",
            color = AA.Text, style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(progress = { dl.progress }, color = AA.Red, trackColor = AA.SurfaceHigh, modifier = Modifier.fillMaxWidth().height(8.dp))
        if (dl.running) {
            Text(dl.current, color = AA.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val f = if (dl.fileTotal > 0) (dl.fileDone.toFloat() / dl.fileTotal).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { f }, color = AA.RedHover, trackColor = AA.SurfaceHigh, modifier = Modifier.fillMaxWidth().height(4.dp))
            Text(
                dl.step + (if (dl.fileTotal > 0) " · ${LibraryDownloader.formatSize(dl.fileDone)} из ${LibraryDownloader.formatSize(dl.fileTotal)}" else "") +
                    (if (dl.speed.isNotBlank()) " · ${dl.speed}" else ""),
                color = AA.TextDim, style = MaterialTheme.typography.bodySmall,
            )
            Text("Окно можно закрыть — скачивание продолжится. Прерванное скачивание докачивается с того же места.", color = AA.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        if (dl.errors.isNotEmpty()) {
            Text("Ошибки (${dl.errors.size}):", color = Color(0xFFFF6B6B))
            dl.errors.takeLast(5).forEach { Text(it, color = AA.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (dl.running) {
                AAButton("Остановить", Icons.Rounded.Stop, { dl.cancel() }, primary = false)
                AAButton("Свернуть", null, onClose)
            } else {
                val dest = dl.lastDestination
                val already = state.offline.state.sources.any { it.isLocal && it.localPath == dest }
                if (dest.isNotBlank() && !already) {
                    AAButton("Добавить в офлайн-библиотеку", Icons.Rounded.FolderShared, {
                        state.offline.addSource(OfflineSource(host = "", share = "", localPath = dest))
                        if (!state.offline.state.enabled) state.offline.setEnabled(true)
                        state.offline.scan()
                        state.showToast("Папка добавлена — идёт сканирование")
                    })
                }
                if (dest.isNotBlank()) AAButton("Открыть папку", Icons.Rounded.FolderOpen, { store?.openFolder(dest) }, primary = false)
                AAButton("Скачать ещё", Icons.Rounded.Download, { dl.reset() }, primary = false)
            }
        }
    }
}
