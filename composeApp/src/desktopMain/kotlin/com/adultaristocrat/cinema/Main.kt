package com.adultaristocrat.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.App
import org.jetbrains.skia.Image
import java.awt.Cursor
import java.awt.Dimension
import java.awt.MouseInfo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.awt.ComposeWindow

fun main() {
    // Windows 7/8: нет DirectX 12 — рисуем через OpenGL (или программно, если задано вручную)
    val osName = System.getProperty("os.name") ?: ""
    val osVersion = System.getProperty("os.version") ?: ""
    if (System.getProperty("skiko.renderApi") == null && osName.startsWith("Windows") &&
        (osVersion.startsWith("6.1") || osVersion.startsWith("6.2") || osVersion.startsWith("6.3"))
    ) {
        System.setProperty("skiko.renderApi", "OPENGL")
    }

    val iconBitmap = runCatching {
        Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use {
            Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
        }
    }.getOrNull()

    application {
        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            width = 1280.dp,
            height = 800.dp,
        )
        val platform = remember { DesktopPlatform(windowState) }
        val appState = remember { AppState(platform) }

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = AppConfig.APP_NAME,
            icon = iconBitmap?.let { BitmapPainter(it) },
            undecorated = true, // своя шапка вместо рамки Windows
            onPreviewKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) return@Window false
                when (e.key) {
                    Key.F11 -> {
                        platform.toggleFullscreen()
                        true
                    }
                    Key.Escape -> {
                        when {
                            // В плеере Esc обрабатывает сам плеер (закрыть меню / выйти)
                            appState.current is Route.Player -> false
                            windowState.placement == WindowPlacement.Fullscreen -> {
                                windowState.placement = WindowPlacement.Floating
                                true
                            }
                            else -> {
                                appState.back()
                                true
                            }
                        }
                    }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(900, 560)
                // Всегда открываемся развёрнутыми на весь экран (без перекрытия панели задач)
                if (!platform.isMaximized) platform.toggleMaximize()
            }
            val fullscreen = windowState.placement == WindowPlacement.Fullscreen
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .then(if (fullscreen || platform.isMaximized) Modifier else Modifier.border(1.dp, AA.Border))
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (!fullscreen) TitleBar(platform, iconBitmap != null) { exitApplication() }
                    Box(Modifier.weight(1f).fillMaxWidth()) { App(appState) }
                }
                if (!fullscreen && !platform.isMaximized) ResizeHandles(windowState)
            }
        }
    }
}

/** Шапка окна в стиле приложения: логотип, название, кнопки */
@Composable
private fun FrameWindowScope.TitleBar(platform: DesktopPlatform, hasIcon: Boolean, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF120000), Color(0xFF050505), Color(0xFF050505))))
            .border(width = 0.5.dp, color = AA.BorderDark),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().windowDrag(window, platform)) {
            Row(
                Modifier.fillMaxSize().padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black)
                        .border(1.dp, AA.Border, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("AA", color = AA.RedBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    AppConfig.APP_NAME.uppercase(),
                    style = TextStyle(
                        fontFamily = platform.fonts.brand,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.6.sp,
                        color = AA.RedBright,
                    ),
                )
            }
        }
        TitleButton(Icons.Rounded.Fullscreen, "Во весь экран (F11)") { platform.toggleFullscreen() }
        TitleButton(Icons.Rounded.Remove, "Свернуть") { platform.minimize() }
        TitleButton(
            if (platform.isMaximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
            if (platform.isMaximized) "Восстановить" else "Развернуть",
        ) { platform.toggleMaximize() }
        TitleButton(Icons.Rounded.Close, "Закрыть", danger = true, onClick = onClose)
    }
}

@Composable
private fun TitleButton(icon: ImageVector, description: String, danger: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .hoverable(interaction)
            .background(
                when {
                    hovered && danger -> Color(0xFFCC0000)
                    hovered -> Color(0xFF2A0000)
                    else -> Color.Transparent
                }
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (hovered) Color.White else Color(0xCCFFFFFF), modifier = Modifier.size(18.dp))
    }
}

/** Изменение размера окна без рамки: правый край, нижний край, правый нижний угол */
@Composable
private fun ResizeHandles(state: WindowState) {
    val density = LocalDensity.current
    fun resize(dx: Float, dy: Float) {
        with(density) {
            val w = (state.size.width + dx.toDp()).coerceAtLeast(900.dp)
            val h = (state.size.height + dy.toDp()).coerceAtLeast(560.dp)
            state.size = DpSize(w, h)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.CenterEnd).width(6.dp).fillMaxHeight()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); resize(drag.x, 0f) } }
        )
        Box(
            Modifier.align(Alignment.BottomCenter).height(6.dp).fillMaxWidth()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR)))
                .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); resize(0f, drag.y) } }
        )
        Box(
            Modifier.align(Alignment.BottomEnd).size(14.dp)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
                .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); resize(drag.x, drag.y) } }
        )
    }
}

/**
 * Перетаскивание окна за шапку (по экранным координатам, без рывков).
 * Двойной щелчок — развернуть / восстановить. Если окно развёрнуто и его потянуть — оно восстанавливается.
 */
private fun Modifier.windowDrag(window: ComposeWindow, platform: DesktopPlatform): Modifier = this.pointerInput(Unit) {
    var lastClick = 0L
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val now = System.currentTimeMillis()
        if (now - lastClick < 400) {
            lastClick = 0L
            platform.toggleMaximize()
            return@awaitEachGesture
        }
        lastClick = now
        val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
        var winX = window.x
        var winY = window.y
        var startX = start.x
        var startY = start.y
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
            val p = MouseInfo.getPointerInfo()?.location ?: break
            if (platform.isMaximized) {
                if (kotlin.math.abs(p.x - startX) + kotlin.math.abs(p.y - startY) < 8) continue
                // Тянем развёрнутое окно — восстанавливаем размер, окно «прилипает» к курсору
                val ratio = (p.x - window.x).toFloat() / window.width.coerceAtLeast(1)
                platform.toggleMaximize()
                winX = p.x - (platform.restoredWidth * ratio).toInt()
                winY = p.y - 18
                startX = p.x
                startY = p.y
            }
            window.setLocation(winX + (p.x - startX), winY + (p.y - startY))
            event.changes.forEach { it.consume() }
        }
    }
}
