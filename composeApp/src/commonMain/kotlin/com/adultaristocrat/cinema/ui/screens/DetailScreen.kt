package com.adultaristocrat.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.Chapter
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.rounded.Movie
import com.adultaristocrat.cinema.Route
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.JellyfinApi
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.AAButton
import com.adultaristocrat.cinema.ui.AAChip
import com.adultaristocrat.cinema.ui.Badge
import com.adultaristocrat.cinema.ui.AdaptivePhoto
import com.adultaristocrat.cinema.ui.Countries
import com.adultaristocrat.cinema.ui.IconAction
import com.adultaristocrat.cinema.ui.LoadingBox
import com.adultaristocrat.cinema.ui.LocalUi
import com.adultaristocrat.cinema.ui.MessageBox
import com.adultaristocrat.cinema.ui.formatRating
import com.adultaristocrat.cinema.ui.rememberFocusVisual
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DetailModel(private val state: AppState, private val id: String) {
    private val api = state.api
    var item by mutableStateOf<BaseItem?>(null)
    var error by mutableStateOf<String?>(null)
    var seasons by mutableStateOf<List<BaseItem>>(emptyList())
    var selectedSeason by mutableStateOf<String?>(null)
    var episodes by mutableStateOf<List<BaseItem>>(emptyList())
    var nextUp by mutableStateOf<BaseItem?>(null)
    var shownOnce = false

    private val offline = id.startsWith("off:")

    init { load() }

    fun load() {
        error = null
        if (offline) {
            item = state.offline.find(id)
            if (item == null) error = "Фильм не найден в офлайн-библиотеке. Пересканируйте папки."
            return
        }
        state.scope.launch {
            try {
                val it = api.item(id)
                item = it
                if (it.isSeries) {
                    nextUp = runCatching { api.nextUp(seriesId = it.id, limit = 1).firstOrNull() }.getOrNull()
                    seasons = runCatching { api.seasons(it.id) }.getOrDefault(emptyList())
                    val season = nextUp?.seasonId?.takeIf { s -> seasons.any { x -> x.id == s } } ?: seasons.firstOrNull()?.id
                    if (season != null) selectSeason(season)
                }
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
        }
    }

    /** Обновить состояние (прогресс просмотра) после возвращения из плеера */
    fun refresh() {
        if (offline) {
            state.offline.find(id)?.let { item = it }
            return
        }
        state.scope.launch {
            runCatching { api.item(id) }.onSuccess { item = it }
            val s = item
            if (s != null && s.isSeries) {
                nextUp = runCatching { api.nextUp(seriesId = s.id, limit = 1).firstOrNull() }.getOrNull()
                selectedSeason?.let { selectSeason(it) }
            }
        }
    }

    fun selectSeason(seasonId: String) {
        selectedSeason = seasonId
        val sid = item?.id ?: return
        state.scope.launch {
            episodes = runCatching { api.episodes(sid, seasonId) }.getOrDefault(emptyList())
        }
    }

    fun toggleFavorite() {
        val it = item ?: return
        val fav = !(it.userData?.isFavorite ?: false)
        item = it.copy(userData = (it.userData ?: com.adultaristocrat.cinema.api.UserData()).copy(isFavorite = fav))
        if (offline) return
        state.scope.launch { runCatching { api.setFavorite(it.id, fav) } }
    }

    fun togglePlayed() {
        val it = item ?: return
        val played = !(it.userData?.played ?: false)
        item = it.copy(userData = (it.userData ?: com.adultaristocrat.cinema.api.UserData()).copy(played = played))
        if (offline) {
            if (played) state.offline.markWatched(it.id) else state.offline.savePosition(it.id, 0, 0)
            return
        }
        state.scope.launch { runCatching { api.setPlayed(it.id, played) }; refresh() }
    }

    /** Что запускать по кнопке «Смотреть» */
    val playTarget: BaseItem?
        get() {
            val it = item ?: return null
            return if (it.isSeries) nextUp ?: episodes.firstOrNull() else it
        }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    fun two(v: Long) = if (v < 10) "0$v" else "$v"
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}

@Composable
fun DetailScreen(state: AppState, route: Route.Detail) {
    val model = state.model(route) { DetailModel(state, route.itemId) }
    LaunchedEffect(Unit) {
        if (model.shownOnce) model.refresh()
        model.shownOnce = true
    }
    val item = model.item
    when {
        model.error != null -> MessageBox("Не удалось загрузить: ${model.error}", actionText = "Повторить") { model.load() }
        item == null -> LoadingBox()
        else -> DetailContent(state, model, item)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(state: AppState, model: DetailModel, item: BaseItem) {
    val ui = LocalUi.current
    val api = state.api
    val playFocus = remember { FocusRequester() }

    Box(Modifier.fillMaxSize()) {
        // Фон
        AsyncImage(
            model = api.backdropUrl(item, 1600),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.22f,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color(0xF2000000)))))

        if (ui.isPhone) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp).padding(bottom = 90.dp)) {
                BackRow(state)
                TitleBlock(item)
                Spacer(Modifier.height(10.dp))
                // Постер (обложка), как на ТВ
                Box(
                    Modifier.fillMaxWidth(0.66f).align(Alignment.CenterHorizontally).aspectRatio(AA.POSTER_RATIO)
                        .clip(RoundedCornerShape(16.dp)).background(AA.SurfaceHigh)
                        .border(1.5.dp, AA.BorderDark, RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = api.posterUrl(item, 900) ?: api.backdropUrl(item, 1000),
                        contentDescription = item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                    )
                    item.officialRating?.let {
                        Badge(it, Modifier.align(Alignment.TopStart).padding(8.dp), bg = Color(0xFFD32F2F))
                    }
                    item.runtimeMinutes?.let {
                        Badge(runtimeText(it), Modifier.align(Alignment.BottomEnd).padding(8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                InfoBody(state, model, item, playFocus)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 16.dp, top = 12.dp, end = 16.dp)) {
                Column(Modifier.fillMaxWidth(0.3f).fillMaxHeight().padding(top = 8.dp, end = 18.dp)) {
                    if (!ui.isTv) BackRow(state)
                    Box(Modifier.fillMaxWidth().aspectRatio(AA.POSTER_RATIO).clip(RoundedCornerShape(16.dp)).background(AA.SurfaceHigh)
                        .border(1.5.dp, AA.BorderDark, RoundedCornerShape(16.dp))) {
                        AsyncImage(
                            model = api.posterUrl(item, 900), contentDescription = item.name,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                        )
                        item.officialRating?.let {
                            Badge(it, Modifier.align(Alignment.TopStart).padding(8.dp), bg = Color(0xFFD32F2F))
                        }
                        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            item.episodeLabel?.let { Badge(it) } ?: Spacer(Modifier.width(1.dp))
                            item.runtimeMinutes?.let { Badge(runtimeText(it)) }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp)) {
                    // Название по-русски всегда видно сверху (не уезжает при прокрутке)
                    TitleBlock(item)
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                        InfoBody(state, model, item, playFocus)
                    }
                }
            }
        }
    }

    LaunchedEffect(item.id) {
        if (ui.isTv) {
            delay(150)
            runCatching { playFocus.requestFocus() }
        }
    }
}

private fun runtimeText(min: Int): String =
    if (min >= 60) "${min / 60} ч ${min % 60} мин" else "$min мин"

@Composable
private fun BackRow(state: AppState) {
    val fv = rememberFocusVisual()
    Row(
        Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(12.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleBlock(item: BaseItem) {
    val title = if (item.isEpisode) item.seriesName ?: item.name else item.name
    Text(title ?: "", style = MaterialTheme.typography.headlineMedium, color = AA.Text)
    if (item.isEpisode) {
        Text("${item.episodeLabel ?: ""}  ${item.name ?: ""}", style = MaterialTheme.typography.titleMedium, color = AA.TextDim)
    }
    val ratings = buildList {
        item.communityRating?.takeIf { it > 0 }?.let { add("IMDb" to formatRating(it)) }
        item.criticRating?.takeIf { it > 0 }?.let { add("Критики" to "${it.toInt()}%") }
    }
    if (ratings.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ratings.forEach { (k, v) ->
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F3A12))
                        .border(1.dp, Color(0xFF3F6B1F), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(k, color = Color(0xFF9CCC65), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(v, color = Color(0xFFC5E1A5), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoBody(state: AppState, model: DetailModel, item: BaseItem, playFocus: FocusRequester) {
    val api = state.api
    val ui = LocalUi.current

    // Год, страна (с флагом), жанры
    val tags: List<Pair<String, String?>> = buildList {
        item.yearLabel?.let { add(it to null) }
        item.productionLocations.take(2).forEach { loc ->
            val c = Countries.find(loc)
            add((c?.ru ?: loc) to c?.flagUrl)
        }
        item.genres.take(5).forEach { add(it to null) }
    }
    if (tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { (t, flag) ->
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(AA.SurfaceHigher)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (flag != null) {
                        AsyncImage(
                            model = flag, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 20.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(t, color = AA.Text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    // Кнопки
    val target = model.playTarget
    val posMs = (target?.userData?.playbackPositionTicks ?: 0L) / JellyfinApi.TICKS_PER_MS
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (target != null) {
            val label = buildString {
                append(if (posMs > 0) "Продолжить" else "Смотреть")
                if (item.isSeries) target.episodeLabel?.let { append(" $it") }
                if (posMs > 0) append(" с ${formatTime(posMs)}")
            }
            AAButton(
                label, Icons.Rounded.PlayArrow, { state.navigate(Route.Player(target.id)) },
                Modifier.focusRequester(playFocus).then(if (ui.isPhone) Modifier.fillMaxWidth() else Modifier),
            )
            if (posMs > 0) {
                AAButton("С начала", Icons.Rounded.Replay, { state.navigate(Route.Player(target.id, fromStart = true)) }, primary = false)
            }
        }
        item.remoteTrailers.firstOrNull { !it.url.isNullOrBlank() }?.let { tr ->
            AAButton("Трейлер", Icons.Rounded.Slideshow, { state.platform.openUrl(tr.url!!) }, primary = false)
        }
    }
    Spacer(Modifier.height(10.dp))

    // Действия
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val fav = item.userData?.isFavorite == true
        IconAction(if (fav) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Избранное", { model.toggleFavorite() }, active = fav)
        val played = item.userData?.played == true
        IconAction(if (played) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline, "Просмотрено", { model.togglePlayed() }, active = played)
    }

    // Описание
    val overview = item.overview
    if (!overview.isNullOrBlank()) {
        Spacer(Modifier.height(14.dp))
        Text("Описание", style = MaterialTheme.typography.titleLarge, color = AA.Text)
        Spacer(Modifier.height(6.dp))
        // Оригинальное название (или слоган) — перед описанием
        val sub = item.taglines.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: item.originalTitle?.takeIf { it.isNotBlank() && it != item.name }
        sub?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = AA.Text, fontWeight = FontWeight.Bold)
        }
        Text(
            overview.replace(Regex("<[^>]*>"), ""),
            style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD0D0D0),
        )
    }

    // Сезоны и серии
    if (item.isSeries && model.seasons.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Text("Сезоны", style = MaterialTheme.typography.titleLarge, color = AA.Text)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
            items(model.seasons, key = { it.id }) { s ->
                AAChip(s.name ?: "Сезон ${s.indexNumber ?: ""}", s.id == model.selectedSeason, { model.selectSeason(s.id) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.episodes.forEach { ep ->
                EpisodeRow(ep, api, ui.isPhone) { state.navigate(Route.Player(ep.id)) }
            }
        }
    }

    // Сцены (главы MKV)
    val chapters = remember(item.id, item.chapters.size) { api.chaptersOf(item) }
    if (chapters.isNotEmpty() && !item.isSeries) {
        Spacer(Modifier.height(18.dp))
        Text("Сцены", style = MaterialTheme.typography.titleLarge, color = AA.Text)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
            itemsIndexed(chapters) { i, ch ->
                SceneCard(i, ch, if (ui.isPhone) 150.dp else 200.dp) {
                    state.navigate(Route.Player(item.id, startMs = ch.startMs))
                }
            }
        }
    }

    // Актёры и режиссёры — прямоугольные портреты 2:3 (как 1000×1500 у Kodi)
    val actors = item.people.filter { it.type == "Actor" || it.type == "GuestStar" }.take(30)
    val directors = item.people.filter { it.type == "Director" }.take(10)
    val openPerson: (com.adultaristocrat.cinema.api.PersonDto) -> Unit = { p ->
        if (!p.id.startsWith("offp:")) state.navigate(Route.Person(p.id, p.name ?: ""))
    }
    PeopleRow("В ролях", actors, api, ui.isPhone, openPerson)
    PeopleRow("Режиссёры", directors, api, ui.isPhone, openPerson)
}

@Composable
private fun PeopleRow(
    title: String,
    people: List<com.adultaristocrat.cinema.api.PersonDto>,
    api: JellyfinApi,
    compact: Boolean,
    onClick: (com.adultaristocrat.cinema.api.PersonDto) -> Unit,
) {
    if (people.isEmpty()) return
    Spacer(Modifier.height(18.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, color = AA.Text)
    Spacer(Modifier.height(8.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        contentPadding = PaddingValues(4.dp),
    ) {
        items(people) { p ->
            val fv = rememberFocusVisual()
            val shape = RoundedCornerShape(12.dp)
            val w = if (compact) 92.dp else 120.dp
            Column(
                Modifier.width(w).clickable(interactionSource = fv.interaction, indication = null) { onClick(p) },
            ) {
                // Рамка подстраивается под пропорции фото — без серых полос
                AdaptivePhoto(
                    url = api.personUrl(p, 600),
                    name = p.name ?: "",
                    borderWidth = if (fv.active) 3.dp else 1.dp,
                    borderColor = if (fv.active) AA.Red else AA.BorderDark,
                    initialsSize = 26.sp,
                    minRatio = 0.5f,
                    maxRatio = 1.0f,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    p.name ?: "", color = if (fv.active) AA.RedHover else AA.Text, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (p.type != "Director" && !p.role.isNullOrBlank()) {
                    Text(p.role, color = AA.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: BaseItem, api: JellyfinApi, compact: Boolean, onClick: () -> Unit) {
    val fv = rememberFocusVisual()
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (fv.active) AA.Selector else Color(0x99101010))
            .border(if (fv.active) 2.dp else 1.dp, if (fv.active) AA.Red else AA.BorderDark, shape)
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(if (compact) 120.dp else 180.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(AA.SurfaceHigh)) {
            AsyncImage(
                model = api.primaryUrl(ep, 400), contentDescription = ep.name,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            val pct = ep.userData?.playedPercentage
            if (pct != null && pct > 0) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth((pct / 100.0).toFloat().coerceIn(0f, 1f)).height(4.dp).background(AA.Red))
            }
            if (ep.userData?.played == true) {
                Icon(Icons.Rounded.CheckCircle, null, tint = AA.Green, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${ep.indexNumber ?: ""}. ${ep.name ?: ""}",
                style = MaterialTheme.typography.titleSmall,
                color = if (fv.active) AA.RedHover else AA.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            ep.runtimeMinutes?.let { Text(runtimeText(it), color = AA.TextDim, fontSize = 12.sp) }
            if (!ep.overview.isNullOrBlank()) {
                Text(
                    ep.overview, color = Color(0xFFBDBDBD), fontSize = 13.sp,
                    maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SceneCard(index: Int, ch: Chapter, width: Dp, onClick: () -> Unit) {
    val fv = rememberFocusVisual()
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.width(width).clip(shape)
            .background(if (fv.active) AA.Selector else Color(0x99101010))
            .border(if (fv.active) 3.dp else 1.dp, if (fv.active) AA.Red else AA.BorderDark, shape)
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(AA.SurfaceHigh)) {
            if (ch.imageUrl != null) {
                AsyncImage(
                    model = ch.imageUrl, contentDescription = ch.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.Movie, null, tint = AA.Border, modifier = Modifier.align(Alignment.Center).size(36.dp))
            }
            Icon(
                Icons.Rounded.PlayArrow, null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(if (fv.active) 40.dp else 0.dp),
            )
            Text(
                formatTime(ch.startMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${index + 1}. ${ch.name}", color = if (fv.active) AA.RedHover else AA.Text,
            fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}
