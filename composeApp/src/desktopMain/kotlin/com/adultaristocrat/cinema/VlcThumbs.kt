package com.adultaristocrat.cinema

import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.concurrent.thread

/** Кадр из видео для картинки сцены (офлайн-библиотека на ПК): VLC без звука и без окна */
object VlcThumbs {
    fun grab(url: String, ms: Long): ByteArray? {
        val factory = Vlc.factory ?: return null
        val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
        val latch = CountDownLatch(1)
        var result: ByteArray? = null
        var w = 480
        var h = 270
        var frames = 0
        val format = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                w = 480
                h = if (sourceWidth > 0) ((480L * sourceHeight) / sourceWidth).toInt().coerceAtLeast(2) else 270
                return RV32BufferFormat(w, h)
            }

            override fun allocatedBuffers(buffers: Array<ByteBuffer>) {}
        }
        val render = object : RenderCallback {
            override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<ByteBuffer>, bufferFormat: BufferFormat) {
                if (result != null) return
                frames++
                if (frames < 3) return // первые кадры после перемотки бывают серыми
                runCatching {
                    val ints = IntArray(w * h)
                    val b = nativeBuffers[0].duplicate()
                    b.rewind()
                    val ib = b.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                    ib.get(ints, 0, minOf(ints.size, ib.remaining()))
                    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
                    img.setRGB(0, 0, w, h, ints, 0, w)
                    val bos = ByteArrayOutputStream()
                    ImageIO.write(img, "jpg", bos)
                    result = bos.toByteArray()
                }
                latch.countDown()
            }
        }
        return try {
            player.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
            player.media().play(url, ":start-time=${ms / 1000.0}", ":no-audio", ":input-fast-seek")
            latch.await(20, TimeUnit.SECONDS)
            result
        } catch (t: Throwable) {
            null
        } finally {
            thread(isDaemon = true) {
                runCatching { player.controls().stop() }
                runCatching { player.release() }
            }
        }
    }
}
