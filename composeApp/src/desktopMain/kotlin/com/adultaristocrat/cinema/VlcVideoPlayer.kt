package com.adultaristocrat.cinema

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.screens.formatTime
import com.sun.jna.NativeLibrary
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** Поиск и инициализация libVLC (своя копия рядом с программой или установленный VLC) */
object Vlc {
    var error: String? = null
        private set

    val factory: MediaPlayerFactory? by lazy { init() }

    private fun init(): MediaPlayerFactory? = try {
        val isWindows = (System.getProperty("os.name") ?: "").startsWith("Windows")
        val libName = if (isWindows) "libvlc.dll" else "libvlc.so"
        val candidates = listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") },
            File(System.getProperty("user.dir") ?: ".", "vlc"),
            File(System.getProperty("user.dir") ?: ".", "app/resources/vlc"),
        )
        val dir = candidates.firstOrNull { File(it, libName).exists() }
        if (dir != null) {
            System.setProperty("jna.library.path", dir.absolutePath)
            NativeLibrary.addSearchPath("libvlc", dir.absolutePath)
            NativeLibrary.addSearchPath("libvlccore", dir.absolutePath)
            NativeLibrary.addSearchPath("vlc", dir.absolutePath)
            NativeLibrary.addSearchPath("vlccore", dir.absolutePath)
        }
        MediaPlayerFactory("--no-video-title-show", "--quiet", "--network-caching=3000", "--no-snapshot-preview")
    } catch (t: Throwable) {
        error = t.message ?: t.toString()
        null
    }
}

/** Принимает кадры от VLC и превращает их в картинку для Compose */
private class FrameSink(private val onFrame: (ImageBitmap) -> Unit) {
    private var width = 0
    private var height = 0
    private var bytes = ByteArray(0)
    private var info: ImageInfo? = null
    private val bitmaps = arrayOf(Bitmap(), Bitmap())
    private var index = 0

    val bufferFormatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            width = sourceWidth
            height = sourceHeight
            info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            bytes = ByteArray(sourceWidth * sourceHeight * 4)
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }

        override fun allocatedBuffers(buffers: Array<ByteBuffer>) {}
    }

    val renderCallback = object : RenderCallback {
        override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<ByteBuffer>, bufferFormat: BufferFormat) {
            val inf = info ?: return
            val buf = nativeBuffers[0]
            buf.rewind()
            buf.get(bytes, 0, minOf(bytes.size, buf.remaining()))
            val bmp = bitmaps[index]
            index = 1 - index
            bmp.installPixels(inf, bytes, width * 4)
            onFrame(bmp.asComposeImageBitmap())
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VlcVideoPlayer(request: PlaybackRequest, events: PlayerEvents, modifier: Modifier, platform: DesktopPlatform) {
    val factory = Vlc.factory
    if (factory == null) {
        LaunchedEffect(Unit) {
            events.onError("Не найден VLC. Установите VLC 3.0 (64-бит) с videolan.org. ${Vlc.error ?: ""}")
        }
        return
    }

    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var position by remember { mutableStateOf(request.startPositionMs) }
    var duration by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastActivity by remember { mutableStateOf(System.currentTimeMillis()) }
    var muted by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var subsMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    val player: EmbeddedMediaPlayer = remember(request.key) {
        val p = factory.mediaPlayers().newEmbeddedMediaPlayer()
        val sink = FrameSink { frame = it }
        p.videoSurface().set(factory.videoSurfaces().newVideoSurface(sink.bufferFormatCallback, sink.renderCallback, true))
        p
    }

    DisposableEffect(player) {
        val listener = object : MediaPlayerEventAdapter() {
            override fun finished(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater { events.onEnded() }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater { events.onError("VLC не смог открыть поток") }
            }
        }
        player.events().addMediaPlayerEventListener(listener)
        val startSec = request.startPositionMs / 1000.0
        if (startSec > 1) player.media().play(request.url, ":start-time=$startSec")
        else player.media().play(request.url)
        onDispose {
            player.events().removeMediaPlayerEventListener(listener)
            thread(isDaemon = true) {
                runCatching { player.controls().stop() }
                runCatching { player.release() }
            }
        }
    }

    // Опрос позиции + отчёт серверу раз в секунду
    LaunchedEffect(player) {
        var n = 0
        while (true) {
            delay(500)
            val t = player.status().time()
            val len = player.status().length()
            if (t >= 0) position = t
            if (len > 0) duration = len
            playing = player.status().isPlaying
            if (++n % 2 == 0 && t > 0) events.onProgress(t, len, !playing)
            if (controlsVisible && playing && System.currentTimeMillis() - lastActivity > 3500 && !audioMenu && !subsMenu) {
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun poke() {
        lastActivity = System.currentTimeMillis()
        controlsVisible = true
    }

    fun togglePause() {
        player.controls().pause()
        poke()
    }

    fun seekBy(ms: Long) {
        player.controls().skipTime(ms)
        poke()
    }

    Box(
        modifier
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPointerEvent(PointerEventType.Move) { poke() }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.Spacebar, Key.Enter, Key.K -> { togglePause(); true }
                    Key.DirectionLeft, Key.J -> { seekBy(-10_000); true }
                    Key.DirectionRight, Key.L -> { seekBy(10_000); true }
                    Key.DirectionUp -> { player.audio().setVolume((player.audio().volume() + 10).coerceAtMost(150)); poke(); true }
                    Key.DirectionDown -> { player.audio().setVolume((player.audio().volume() - 10).coerceAtLeast(0)); poke(); true }
                    Key.F -> { platform.toggleFullscreen(); true }
                    Key.M -> { player.audio().mute(); muted = player.audio().isMute; poke(); true }
                    else -> false
                }
            }
    ) {
        val f = frame
        if (f != null) {
            Image(
                bitmap = f,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { togglePause() },
            )
        } else {
            CircularProgressIndicator(color = AA.Red, modifier = Modifier.align(Alignment.Center))
        }

        if (controlsVisible || !playing) {
            // Верхняя панель
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth().background(AA.topScrim).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { events.onBack() }) {
                    Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    request.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            // Нижняя панель
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(AA.bottomScrim)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                val dur = duration.coerceAtLeast(1)
                Slider(
                    value = seeking ?: (position.toFloat() / dur).coerceIn(0f, 1f),
                    onValueChange = { seeking = it; poke() },
                    onValueChangeFinished = {
                        seeking?.let { player.controls().setTime((it * dur).toLong()) }
                        seeking = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = AA.Red, activeTrackColor = AA.Red, inactiveTrackColor = Color(0x55B40000),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, "−10 с", tint = Color.White) }
                    IconButton(onClick = { togglePause() }) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Пауза", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, "+10 с", tint = Color.White) }
                    Spacer(Modifier.width(8.dp))
                    val shown = seeking?.let { (it * dur).toLong() } ?: position
                    Text("${formatTime(shown)} / ${formatTime(duration)}", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = { player.audio().mute(); muted = player.audio().isMute; poke() }) {
                        Icon(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Звук", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { audioMenu = true; poke() }) { Icon(Icons.Rounded.Audiotrack, "Озвучка", tint = Color.White) }
                        DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                            val current = player.audio().track()
                            player.audio().trackDescriptions().forEach { t ->
                                DropdownMenuItem(
                                    text = { Text((if (t.id() == current) "● " else "   ") + t.description()) },
                                    onClick = { player.audio().setTrack(t.id()); audioMenu = false },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { subsMenu = true; poke() }) { Icon(Icons.Rounded.Subtitles, "Субтитры", tint = Color.White) }
                        DropdownMenu(expanded = subsMenu, onDismissRequest = { subsMenu = false }) {
                            val current = player.subpictures().track()
                            player.subpictures().trackDescriptions().forEach { t ->
                                DropdownMenuItem(
                                    text = { Text((if (t.id() == current) "● " else "   ") + t.description()) },
                                    onClick = { player.subpictures().setTrack(t.id()); subsMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { platform.toggleFullscreen(); poke() }) {
                        Icon(if (platform.isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "Во весь экран", tint = Color.White)
                    }
                }
            }
        }
    }
}
