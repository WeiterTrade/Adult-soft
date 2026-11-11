package com.adultaristocrat.cinema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adultaristocrat.cinema.Chapter
import com.adultaristocrat.cinema.Platform
import com.adultaristocrat.cinema.PlaybackRequest
import com.adultaristocrat.cinema.PlayerControl
import com.adultaristocrat.cinema.PlayerEvents
import com.adultaristocrat.cinema.TrackOption
import com.adultaristocrat.cinema.ui.screens.formatTime
import kotlinx.coroutines.delay

private enum class Panel { NONE, SCENES, AUDIO, SUBS }

private const val HIDE_AFTER_TICKS = 8 // 8 × 0,5 с = 4 с

/**
 * Общий интерфейс плеера для Android, Android TV и ПК:
 * перемотка, пауза, сцены (главы MKV), озвучка, субтитры.
 */
@Composable
fun PlayerOverlay(
    request: PlaybackRequest,
    control: PlayerControl?,
    events: PlayerEvents,
    platform: Platform,
) {
    val ui = LocalUi.current
    val chapters = request.chapters

    var pos by remember { mutableStateOf(request.startPositionMs) }
    var dur by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(true) }
    var idleTicks by remember { mutableStateOf(0) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var backHandled by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val barFocus = remember { FocusRequester() }

    fun activity() {
        idleTicks = 0
        visible = true
    }

    fun seekTo(ms: Long) {
        val c = control ?: return
        val target = ms.coerceIn(0, if (dur > 0) dur - 1000 else Long.MAX_VALUE)
        c.seekTo(target)
        pos = target
        activity()
    }

    fun togglePause() {
        control?.togglePause()
        activity()
    }

    fun currentChapter(): Int = chapters.indexOfLast { it.startMs <= pos + 500 }

    fun nextChapter() {
        val i = chapters.indexOfFirst { it.startMs > pos + 1000 }
        if (i >= 0) seekTo(chapters[i].startMs)
    }

    fun prevChapter() {
        val cur = currentChapter()
        val target = when {
            cur >= 0 && pos - chapters[cur].startMs > 3000 -> chapters[cur].startMs
            cur > 0 -> chapters[cur - 1].startMs
            else -> 0L
        }
        seekTo(target)
    }

    // Опрос плеера, автоскрытие кнопок, отчёт прогресса серверу
    LaunchedEffect(control) {
        var n = 0
        while (true) {
            delay(500)
            val c = control ?: continue
            val p = c.positionMs
            if (p >= 0) pos = p
            if (c.durationMs > 0) dur = c.durationMs
            playing = c.isPlaying
            buffering = c.isBuffering
            idleTicks++
            if (++n % 2 == 0 && p > 0) events.onProgress(p, dur, !playing)
            if (visible && playing && panel == Panel.NONE && idleTicks >= HIDE_AFTER_TICKS) visible = false
        }
    }

    LaunchedEffect(visible, panel) {
        delay(60)
        runCatching {
            if (visible && panel == Panel.NONE && !ui.isPhone) barFocus.requestFocus()
            else if (!visible) rootFocus.requestFocus()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                val isBack = e.key == Key.Back || e.key == Key.Escape
                if (isBack) {
                    if (down) {
                        backHandled = when {
                            panel != Panel.NONE -> { panel = Panel.NONE; activity(); true }
                            visible && ui.isTv -> { visible = false; true }
                            e.key == Key.Escape -> { events.onBack(); true }
                            else -> false
                        }
                        return@onPreviewKeyEvent backHandled
                    } else {
                        val h = backHandled
                        backHandled = false
                        return@onPreviewKeyEvent h
                    }
                }
                if (!down) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar, Key.K -> { togglePause(); true }
                    Key.MediaNext, Key.ChannelUp, Key.PageDown, Key.N -> { nextChapter(); true }
                    Key.MediaPrevious, Key.ChannelDown, Key.PageUp, Key.P -> { prevChapter(); true }
                    // «Стоп» на пульте — выйти из фильма (позиция сохраняется)
                    Key.MediaStop, Key.MediaClose -> { events.onBack(); true }
                    // Меню / Инфо / цветные кнопки пульта
                    Key.Menu, Key.Info, Key.Guide, Key.ProgramRed -> {
                        if (chapters.isNotEmpty()) { panel = if (panel == Panel.SCENES) Panel.NONE else Panel.SCENES; activity() }
                        true
                    }
                    Key.ProgramGreen -> { panel = if (panel == Panel.AUDIO) Panel.NONE else Panel.AUDIO; activity(); true }
                    Key.ProgramYellow, Key.Captions -> { panel = if (panel == Panel.SUBS) Panel.NONE else Panel.SUBS; activity(); true }
                    Key.MediaFastForward -> { seekTo(pos + 30_000); true }
                    Key.MediaRewind -> { seekTo(pos - 30_000); true }
                    Key.F -> if (platform.canToggleFullscreen) { platform.toggleFullscreen(); true } else false
                    else -> {
                        if (!visible && panel == Panel.NONE) {
                            when (e.key) {
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { togglePause(); true }
                                Key.DirectionLeft -> { seekTo(pos - 10_000); true }
                                Key.DirectionRight -> { seekTo(pos + 10_000); true }
                                Key.DirectionUp, Key.DirectionDown -> { activity(); true }
                                else -> { activity(); false }
                            }
                        } else {
                            idleTicks = 0
                            false
                        }
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable()
    ) {
        // Нажатия по видео: на ПК — пауза, на телефоне — показать/скрыть кнопки
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            if (ev.type == PointerEventType.Move && ui.isDesktop) activity()
                        }
                    }
                }
                .pointerInput(ui.isDesktop) {
                    detectTapGestures(
                        onTap = {
                            if (panel != Panel.NONE) panel = Panel.NONE
                            else if (ui.isDesktop) togglePause()
                            else if (visible) visible = false else activity()
                        },
                        onDoubleTap = { if (platform.canToggleFullscreen) platform.toggleFullscreen() },
                    )
                }
        )

        if (buffering || control == null) {
            CircularProgressIndicator(color = AA.Red, modifier = Modifier.align(Alignment.Center).size(56.dp))
        } else if (!playing && !visible) {
            Icon(
                Icons.Rounded.Pause, null, tint = Color(0xCCFFFFFF),
                modifier = Modifier.align(Alignment.Center).size(84.dp).clip(CircleShape).background(Color(0x66000000)).padding(12.dp),
            )
        }

        if (visible || panel != Panel.NONE) {
            // ---------- Верх ----------
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().background(AA.topScrim)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CtrlButton(Icons.Rounded.ArrowBack, "Назад") { events.onBack() }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        request.title, color = Color.White, fontSize = if (ui.isPhone) 16.sp else 20.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val ci = currentChapter()
                    if (ci >= 0) {
                        Text(
                            "Сцена ${ci + 1} из ${chapters.size} · ${chapters[ci].name}",
                            color = AA.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // ---------- Низ ----------
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(AA.bottomScrim)
                    .padding(horizontal = if (ui.isPhone) 12.dp else 24.dp, vertical = 12.dp)
            ) {
                if (panel == Panel.SCENES) {
                    ScenesStrip(chapters, currentChapter(), ui.isPhone) { ch ->
                        seekTo(ch.startMs)
                        panel = Panel.NONE
                    }
                    Spacer(Modifier.height(12.dp))
                }

                SeekBar(
                    pos = pos, dur = dur, chapters = chapters,
                    focusRequester = barFocus,
                    onSeek = { seekTo(it) },
                    onToggle = { togglePause() },
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatTime(pos)} / ${formatTime(dur)}",
                        color = Color.White, fontSize = 14.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (dur > 0) Text("осталось ${formatTime(dur - pos)}", color = AA.TextDim, fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (ui.isPhone) 2.dp else 8.dp),
                ) {
                    CtrlButton(Icons.Rounded.Replay10, "−10 с") { seekTo(pos - 10_000) }
                    CtrlButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Пауза", big = true) { togglePause() }
                    CtrlButton(Icons.Rounded.Forward10, "+10 с") { seekTo(pos + 10_000) }
                    if (chapters.isNotEmpty()) {
                        CtrlButton(Icons.Rounded.SkipPrevious, "Предыдущая сцена") { prevChapter() }
                        CtrlButton(Icons.Rounded.SkipNext, "Следующая сцена") { nextChapter() }
                    }
                    Spacer(Modifier.weight(1f))
                    if (chapters.isNotEmpty()) {
                        CtrlButton(Icons.Rounded.ViewCarousel, "Сцены", label = if (ui.isPhone) null else "Сцены", active = panel == Panel.SCENES) {
                            panel = if (panel == Panel.SCENES) Panel.NONE else Panel.SCENES
                            activity()
                        }
                    }
                    CtrlButton(Icons.Rounded.Audiotrack, "Озвучка", active = panel == Panel.AUDIO) {
                        panel = if (panel == Panel.AUDIO) Panel.NONE else Panel.AUDIO
                        activity()
                    }
                    CtrlButton(Icons.Rounded.Subtitles, "Субтитры", active = panel == Panel.SUBS) {
                        panel = if (panel == Panel.SUBS) Panel.NONE else Panel.SUBS
                        activity()
                    }
                    if (platform.canToggleFullscreen) {
                        CtrlButton(
                            if (platform.isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Во весь экран (F11)",
                        ) { platform.toggleFullscreen(); activity() }
                    }
                }
            }
        }

        if (panel == Panel.AUDIO || panel == Panel.SUBS) {
            val c = control
            val options = when {
                c == null -> emptyList()
                panel == Panel.AUDIO -> c.audioTracks()
                else -> c.subtitleTracks()
            }
            TrackPanel(
                title = if (panel == Panel.AUDIO) "Озвучка" else "Субтитры",
                options = options,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) { opt ->
                if (panel == Panel.AUDIO) c?.selectAudio(opt.id) else c?.selectSubtitle(opt.id)
                panel = Panel.NONE
                activity()
            }
        }
    }
}

/** Полоса перемотки с отметками сцен. В фокусе (пульт): ←/→ — перемотка, ОК — пауза */
@Composable
private fun SeekBar(
    pos: Long,
    dur: Long,
    chapters: List<Chapter>,
    focusRequester: FocusRequester,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .focusRequester(focusRequester)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onSeek(pos - 10_000); true }
                    Key.DirectionRight -> { onSeek(pos + 10_000); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onToggle(); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .pointerInput(dur) {
                detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toLong()) }
            }
            .pointerInput(dur) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        dragFraction?.let { if (dur > 0) onSeek((it * dur).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val h = if (focused) 8.dp else 5.dp
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(4.dp)).background(Color(0x55B40000))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(AA.Red))
        }
        // Отметки сцен
        if (dur > 0) {
            chapters.forEach { ch ->
                if (ch.startMs > 0) {
                    val x = w * (ch.startMs.toFloat() / dur).coerceIn(0f, 1f)
                    Box(Modifier.offset(x = x).width(2.dp).height(h).background(Color(0xCCFFFFFF)))
                }
            }
        }
        // Бегунок
        Box(
            Modifier.offset(x = w * fraction - 8.dp).size(if (focused) 18.dp else 14.dp)
                .clip(CircleShape).background(if (focused) Color.White else AA.Red)
                .border(2.dp, AA.Red, CircleShape)
        )
    }
}

@Composable
private fun CtrlButton(
    icon: ImageVector,
    description: String,
    big: Boolean = false,
    label: String? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val fv = rememberFocusVisual()
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .clip(shape)
            .background(
                when {
                    fv.active -> AA.Red
                    active -> AA.Selector
                    else -> Color(0x33000000)
                }
            )
            .border(if (fv.active) 2.dp else 0.dp, if (fv.active) Color.White else Color.Transparent, shape)
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (label != null) 12.dp else 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, description, tint = if (active && !fv.active) AA.RedHover else Color.White, modifier = Modifier.size(if (big) 36.dp else 28.dp))
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/** Лента сцен с картинками */
@Composable
private fun ScenesStrip(chapters: List<Chapter>, current: Int, compact: Boolean, onSelect: (Chapter) -> Unit) {
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (current > 0) listState.scrollToItem((current - 1).coerceAtLeast(0))
        delay(80)
        runCatching { focus.requestFocus() }
    }
    Text("Сцены", color = Color.White, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        itemsIndexed(chapters) { i, ch ->
            val fv = rememberFocusVisual()
            val isCur = i == current
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    .width(if (compact) 150.dp else 210.dp)
                    .then(if (i == current.coerceAtLeast(0)) Modifier.focusRequester(focus) else Modifier)
                    .clip(shape)
                    .background(if (fv.active) AA.Selector else Color(0xCC101010))
                    .border(
                        if (fv.active) 3.dp else if (isCur) 2.dp else 1.dp,
                        if (fv.active) Color.White else if (isCur) AA.Red else AA.BorderDark,
                        shape,
                    )
                    .clickable(interactionSource = fv.interaction, indication = null) { onSelect(ch) }
                    .padding(6.dp)
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(AA.SurfaceHigh)) {
                    if (ch.imageUrl != null) {
                        AsyncImage(
                            model = ch.imageUrl, contentDescription = ch.name,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(Icons.Rounded.Movie, null, tint = AA.Border, modifier = Modifier.align(Alignment.Center).size(36.dp))
                    }
                    Text(
                        formatTime(ch.startMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 4.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${i + 1}. ${ch.name}", color = if (fv.active || isCur) Color.White else AA.TextDim,
                    fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Выбор озвучки / субтитров */
@Composable
private fun TrackPanel(title: String, options: List<TrackOption>, modifier: Modifier, onSelect: (TrackOption) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(options.size) {
        delay(80)
        runCatching { focus.requestFocus() }
    }
    val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    Column(
        modifier
            .padding(16.dp)
            .widthIn(min = 260.dp, max = 420.dp)
            .fillMaxHeight(0.75f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF0080000))
            .border(1.dp, AA.Border, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (options.isEmpty()) {
            Text("Нет вариантов", color = AA.TextDim)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(options) { i, opt ->
                val fv = rememberFocusVisual()
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (i == selectedIndex) Modifier.focusRequester(focus) else Modifier)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (fv.active) AA.Red else if (opt.selected) AA.Selector else Color.Transparent)
                        .clickable(interactionSource = fv.interaction, indication = null) { onSelect(opt) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (opt.selected) AA.RedBright else Color.Transparent)
                            .border(1.dp, AA.TextDim, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(opt.label, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
