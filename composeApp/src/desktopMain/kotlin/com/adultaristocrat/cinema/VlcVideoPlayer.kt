package com.adultaristocrat.cinema

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.sun.jna.NativeLibrary
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
        MediaPlayerFactory("--no-video-title-show", "--quiet", "--network-caching=1500", "--no-snapshot-preview", "--input-fast-seek")
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


/** Управление VLC для общего интерфейса плеера */
private class VlcControl(private val player: EmbeddedMediaPlayer) : PlayerControl {
    @Volatile var buffering = true

    override val positionMs: Long get() = player.status().time()
    override val durationMs: Long get() = player.status().length().coerceAtLeast(0)
    override val isPlaying: Boolean get() = player.status().isPlaying
    override val isBuffering: Boolean get() = buffering

    override fun togglePause() {
        player.controls().pause()
    }

    override fun seekTo(ms: Long) {
        player.controls().setTime(ms.coerceAtLeast(0))
    }

    override fun audioTracks(): List<TrackOption> {
        val cur = player.audio().track()
        return player.audio().trackDescriptions()
            .filter { it.id() >= 0 }
            .map { TrackOption(it.id().toString(), it.description() ?: "Дорожка ${it.id()}", it.id() == cur) }
    }

    override fun selectAudio(id: String) {
        id.toIntOrNull()?.let { player.audio().setTrack(it) }
    }

    override fun subtitleTracks(): List<TrackOption> {
        val cur = player.subpictures().track()
        val list = player.subpictures().trackDescriptions()
            .filter { it.id() >= 0 }
            .map { TrackOption(it.id().toString(), it.description() ?: "Субтитры ${it.id()}", it.id() == cur) }
        return listOf(TrackOption("off", "Выключены", cur < 0)) + list
    }

    override fun selectSubtitle(id: String) {
        player.subpictures().setTrack(if (id == "off") -1 else id.toIntOrNull() ?: -1)
    }
}

@Composable
fun VlcVideoSurface(
    request: PlaybackRequest,
    events: PlayerEvents,
    onControl: (PlayerControl?) -> Unit,
    modifier: Modifier,
) {
    val factory = Vlc.factory
    if (factory == null) {
        LaunchedEffect(Unit) {
            events.onError("Не найден VLC. Установите VLC 3.0 (64-бит) с videolan.org. ${Vlc.error ?: ""}")
        }
        return
    }

    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    val player: EmbeddedMediaPlayer = remember(request.key) {
        val p = factory.mediaPlayers().newEmbeddedMediaPlayer()
        val sink = FrameSink { frame = it }
        p.videoSurface().set(factory.videoSurfaces().newVideoSurface(sink.bufferFormatCallback, sink.renderCallback, true))
        p
    }

    DisposableEffect(player) {
        val control = VlcControl(player)
        val listener = object : MediaPlayerEventAdapter() {
            override fun finished(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater { events.onEnded() }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater { events.onError("VLC не смог открыть поток") }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                control.buffering = newCache < 100f
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                control.buffering = false
            }
        }
        player.events().addMediaPlayerEventListener(listener)
        val startSec = request.startPositionMs / 1000.0
        // input-fast-seek: перемотка к ближайшему ключевому кадру — сцены переключаются быстрее
        if (startSec > 1) player.media().play(request.url, ":start-time=$startSec", ":input-fast-seek")
        else player.media().play(request.url, ":input-fast-seek")
        onControl(control)
        onDispose {
            onControl(null)
            player.events().removeMediaPlayerEventListener(listener)
            thread(isDaemon = true) {
                runCatching { player.controls().stop() }
                runCatching { player.release() }
            }
        }
    }

    Box(modifier.background(Color.Black)) {
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
