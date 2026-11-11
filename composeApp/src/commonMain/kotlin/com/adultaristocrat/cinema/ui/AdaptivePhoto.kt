package com.adultaristocrat.cinema.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * Фото актёра, которое подстраивает рамку под свои пропорции:
 * стандарт 1000×1500 (2:3), но любое другое фото тоже видно целиком и без серых полос.
 * Пока фото грузится или его нет — рамка 2:3 с инициалами.
 */
@Composable
fun AdaptivePhoto(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = AA.BorderDark,
    initialsSize: TextUnit = TextUnit.Unspecified,
    minRatio: Float = 0.5f,
    maxRatio: Float = 1.5f,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val painter = rememberAsyncImagePainter(url)
    val state by painter.state.collectAsState()
    val loaded = state is AsyncImagePainter.State.Success
    val intrinsic: Size = if (loaded) painter.intrinsicSize else Size.Unspecified
    val ratio = if (loaded && intrinsic != Size.Unspecified && intrinsic.height > 0f && intrinsic.width > 0f) {
        (intrinsic.width / intrinsic.height).coerceIn(minRatio, maxRatio)
    } else {
        AA.PORTRAIT_RATIO
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(if (loaded) Color.Transparent else Color(0xFF3A3A3A))
            .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
            Text(initials, color = Color(0xFFE0E0E0), fontSize = initialsSize, fontWeight = FontWeight.Bold)
        }
        if (url != null) {
            Image(
                painter = painter,
                contentDescription = name,
                // Рамка уже в пропорциях фото — Crop заполняет её целиком (обрезает только при крайних пропорциях)
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
