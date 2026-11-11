package com.adultaristocrat.cinema

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.adultaristocrat.cinema.ui.App
import org.jetbrains.skia.Image

fun main() {
    // Windows 7/8: нет DirectX 12 — рисуем через OpenGL (или программно, если задано вручную)
    val osName = System.getProperty("os.name") ?: ""
    val osVersion = System.getProperty("os.version") ?: ""
    if (System.getProperty("skiko.renderApi") == null && osName.startsWith("Windows") &&
        (osVersion.startsWith("6.1") || osVersion.startsWith("6.2") || osVersion.startsWith("6.3"))
    ) {
        System.setProperty("skiko.renderApi", "OPENGL")
    }

    val icon = runCatching {
        Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use {
            BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
        }
    }.getOrNull()

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        val platform = remember { DesktopPlatform(windowState) }
        val appState = remember { AppState(platform) }

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = AppConfig.APP_NAME,
            icon = icon,
            onPreviewKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) return@Window false
                when (e.key) {
                    Key.Escape -> {
                        if (windowState.placement == WindowPlacement.Fullscreen && appState.current !is Route.Player) {
                            windowState.placement = WindowPlacement.Floating
                        } else {
                            appState.back()
                        }
                        true
                    }
                    Key.F11 -> {
                        platform.toggleFullscreen()
                        true
                    }
                    else -> false
                }
            },
        ) {
            App(appState)
        }
    }
}
