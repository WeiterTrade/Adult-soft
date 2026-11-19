package com.adultaristocrat.cinema.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.LocalUi
import com.adultaristocrat.cinema.ui.rememberFocusVisual
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Обложка-DVD-бокс, как в руках:
 *  • нажатие по центру — крышка открывается, внутри крутится диск, крышка закрывается,
 *    коробка переворачивается другой стороной (если есть задняя обложка);
 *  • нажатие по краю — просто перевернуть обложку;
 *  • на ТВ — OK запускает всю анимацию.
 * Диск — картинка «Диск» из Jellyfin (или -disc.png офлайн); если её нет, рисуется из обложки.
 */
@Composable
internal fun FlipPoster(
    front: String?,
    back: String?,
    title: String?,
    frontFull: String? = null,
    backFull: String? = null,
    disc: String? = null,
    modifier: Modifier,
    badges: @Composable BoxScope.() -> Unit,
) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val flip = remember(back) { Animatable(0f) }
    val lid = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    val showBack = flip.value > 90f
    val canZoom = !ui.isTv && front != null

    if (zoom) {
        CoverZoomDialog(front = frontFull ?: front, back = backFull ?: back, startBack = showBack, onClose = { zoom = false })
    }

    fun flipOver() {
        if (back == null || running) return
        job = scope.launch {
            running = true
            try {
                flip.animateTo(if (flip.value > 90f) 0f else 180f, tween(600, easing = FastOutSlowInEasing))
            } finally {
                running = false
            }
        }
    }

    fun playBox() {
        if (front == null) return
        if (running) {
            // Повторное нажатие во время анимации — сразу закрыть коробку
            job?.cancel()
            job = scope.launch { lid.animateTo(0f, tween(300)) }
            running = false
            return
        }
        job = scope.launch {
            running = true
            try {
                lid.animateTo(-105f, tween(700, easing = FastOutSlowInEasing))
                spin.animateTo(spin.value + 1080f, tween(2600, easing = FastOutSlowInEasing))
                lid.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
                if (back != null) flip.animateTo(if (flip.value > 90f) 0f else 180f, tween(650, easing = FastOutSlowInEasing))
            } finally {
                running = false
            }
        }
    }

    val fv = rememberFocusVisual()
    val shape = RoundedCornerShape(16.dp)
    // Задняя сторона повёрнута на 180° — разворачиваем содержимое, чтобы оно не было зеркальным
    val unmirror = Modifier.graphicsLayer { if (showBack) rotationY = 180f }

    Box(
        modifier
            .aspectRatio(AA.POSTER_RATIO)
            .focusable(interactionSource = fv.interaction)
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) {
                    playBox()
                    true
                } else ok
            }
            .pointerInput(back, front) {
                detectTapGestures { o ->
                    val x = o.x / size.width.coerceAtLeast(1)
                    if (x in 0.3f..0.7f || back == null) playBox() else flipOver()
                }
            }
            .graphicsLayer {
                rotationY = flip.value
                cameraDistance = 14f * density
            },
    ) {
        // Внутри коробки — держатель и диск (видно, пока крышка открыта)
        if (lid.value != 0f) {
            DvdTray(disc ?: front, generated = disc == null, angle = spin.value, modifier = Modifier.fillMaxSize().then(unmirror).clip(shape))
        }
        // Крышка — сама обложка. Открывается вокруг левого края (на обороте — вокруг правого)
        Box(Modifier.fillMaxSize().then(unmirror)) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = lid.value
                        cameraDistance = 14f * density
                    }
                    .clip(shape)
                    .background(AA.SurfaceHigh)
                    .border(if (fv.active) 3.dp else 1.5.dp, if (fv.active) AA.Red else AA.BorderDark, shape),
            ) {
                if (lid.value > -90f) {
                    AsyncImage(
                        model = if (showBack) back else front,
                        contentDescription = if (showBack) "Задняя обложка" else title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!showBack && lid.value == 0f && !running) badges()
                } else {
                    // Внутренняя сторона крышки — белый пластик
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFF2F2F2), Color(0xFFD0D0D0)))))
                }
            }
            if (!running && lid.value == 0f) {
                if (canZoom) {
                    Icon(
                        Icons.Rounded.ZoomIn, "Приблизить обложку", tint = Color.White,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(34.dp)
                            .clip(CircleShape).background(Color(0x99000000))
                            .clickable { zoom = true }
                            .padding(5.dp),
                    )
                }
                if (back != null) {
                    Icon(
                        Icons.Rounded.Flip, "Перевернуть обложку", tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp)
                            .clip(CircleShape).background(Color(0x99000000)).padding(5.dp),
                    )
                }
            }
        }
    }
}

/** Белый DVD-бокс изнутри: рамка, держатель и крутящийся диск с бликом */
@Composable
private fun DvdTray(image: String?, generated: Boolean, angle: Float, modifier: Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFFF7F7F7), Color(0xFFD6D6D6)))),
        contentAlignment = Alignment.Center,
    ) {
        // Литая рамка по краю
        Box(Modifier.fillMaxSize().padding(7.dp).border(2.dp, Color(0x1F000000), RoundedCornerShape(10.dp)))
        // Диск
        Box(
            Modifier.fillMaxWidth(0.88f).aspectRatio(1f)
                .graphicsLayer { rotationZ = angle },
        ) {
            AsyncImage(
                model = image, contentDescription = "Диск",
                contentScale = if (generated) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(if (generated) Color(0xFFBDBDBD) else Color.Transparent),
            )
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                if (generated) {
                    // Прозрачное кольцо у центра и серебристый ободок, как у настоящего DVD
                    drawCircle(Color(0xFFE9E9E9), radius = r * 0.22f, center = c)
                    drawCircle(Color(0x55FFFFFF), radius = r * 0.30f, center = c, style = Stroke(width = r * 0.04f))
                    drawCircle(Color(0xFFC9C9C9), radius = r * 0.985f, center = c, style = Stroke(width = r * 0.03f))
                }
                // Отверстие и втулка держателя
                drawCircle(Color(0xFFD9D9D9), radius = r * 0.11f, center = c)
                drawCircle(Color(0xFFBDBDBD), radius = r * 0.11f, center = c, style = Stroke(width = r * 0.02f))
                drawCircle(Color(0xFFF5F5F5), radius = r * 0.06f, center = c)
            }
        }
        // Блик — почти не вращается вместе с диском, поэтому «переливается»
        Canvas(
            Modifier.fillMaxWidth(0.88f).aspectRatio(1f).graphicsLayer { rotationZ = -30f + angle * 0.08f },
        ) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent, Color(0x55FFFFFF), Color.Transparent, Color.Transparent,
                        Color(0x33FFFFFF), Color.Transparent, Color.Transparent,
                    ),
                    center = c,
                ),
                radius = r,
                center = c,
            )
        }
    }
}
