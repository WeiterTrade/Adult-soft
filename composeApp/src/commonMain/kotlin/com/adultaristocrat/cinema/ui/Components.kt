package com.adultaristocrat.cinema.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adultaristocrat.cinema.api.BaseItem
import com.adultaristocrat.cinema.api.JellyfinApi

/** Состояние фокуса/наведения: на ТВ — фокус пультом, на ПК — мышь */
class FocusVisual(val interaction: MutableInteractionSource, val active: Boolean)

@Composable
fun rememberFocusVisual(): FocusVisual {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    return FocusVisual(interaction, focused || hovered)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 6.dp)) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.5.sp),
            color = Color(0xD1FFFFFF),
        )
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(180.dp).height(1.5.dp).background(AA.sectionLine))
    }
}

/** «Таблетка» — фильтры, жанры, вкладки */
@Composable
fun AAChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val fv = rememberFocusVisual()
    val scale by animateFloatAsState(if (fv.active) 1.06f else 1f)
    val bg = when {
        selected -> AA.Red
        fv.active -> AA.Selector
        else -> AA.SurfaceHigh
    }
    Row(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                if (fv.active) 2.dp else 1.dp,
                if (fv.active) AA.Text else if (selected) AA.RedBright else AA.Border,
                RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = AA.Text, modifier = Modifier.size(18.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(6.dp))
        }
        if (text.isNotEmpty()) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected || fv.active) AA.Text else Color(0xE6FFFFFF),
                maxLines = 1,
            )
        }
    }
}

/** Большая кнопка: красная «Смотреть» или светлая «Трейлер» */
@Composable
fun AAButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    val fv = rememberFocusVisual()
    val scale by animateFloatAsState(if (fv.active) 1.04f else 1f)
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        primary && fv.active -> Color(0xFFFF1A1A)
        primary -> AA.Red
        fv.active -> Color(0xFF3A3A3A)
        else -> AA.SurfaceHigher
    }
    Row(
        modifier
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(if (fv.active) 3.dp else 1.dp, if (fv.active) AA.Text else if (primary) AA.RedBright else AA.Border, shape)
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = AA.Text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = AA.Text, maxLines = 1)
    }
}

/** Круглая иконка-действие (избранное, просмотрено и т.п.) */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val fv = rememberFocusVisual()
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (fv.active) AA.Selector else Color.Transparent)
            .border(if (fv.active) 2.dp else 0.dp, if (fv.active) AA.Red else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) AA.RedBright else AA.Text, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AA.TextDim, maxLines = 1)
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, bg: Color = Color(0xD9000000), fg: Color = AA.Text, icon: ImageVector? = null, iconTint: Color = AA.Gold) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
        }
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

fun formatRating(r: Float): String {
    val v = (r * 10).toInt()
    return "${v / 10},${v % 10}"
}

/** Постер 2:3 с бейджами — как в веб-клиенте */
@Composable
fun PosterCard(
    item: BaseItem,
    api: JellyfinApi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val fv = rememberFocusVisual()
    val scale by animateFloatAsState(if (fv.active) 1.06f else 1f)
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    Column(
        modifier
            .scale(scale)
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(AA.SurfaceHigh)
                .border(if (fv.active) 3.dp else 1.5.dp, if (fv.active) AA.Red else AA.BorderDark, shape)
        ) {
            val url = api.posterUrl(item)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    item.name ?: "?",
                    color = AA.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            // Верх: серия / рейтинг
            Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val left = item.episodeLabel ?: if (item.isSeries && item.status == "Ended") "Завершён" else null
                if (left != null) Badge(left) else Spacer(Modifier.width(1.dp))
                item.communityRating?.takeIf { it > 0f }?.let {
                    Badge(formatRating(it), icon = Icons.Rounded.Star)
                }
            }
            // Низ: жанр, тип, год + прогресс
            CardFooter(item, compact)
            if (item.userData?.played == true && !item.isSeries) {
                Icon(
                    Icons.Rounded.CheckCircle, "Просмотрено", tint = AA.Green,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(6.dp).size(20.dp),
                )
            }
        }
        Text(
            item.seriesName.takeIf { item.isEpisode } ?: item.name ?: "",
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (fv.active) AA.RedHover else AA.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun BoxScope.CardFooter(item: BaseItem, compact: Boolean) {
    Column(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().background(AA.cardFooterGradient)
            .padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 5.dp)
    ) {
        // Страна с флагом (если страны нет — жанр)
        val country = item.productionLocations.firstNotNullOfOrNull { Countries.find(it) }
        val fallback = item.productionLocations.firstOrNull() ?: item.genres.firstOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (country != null) {
                AsyncImage(
                    model = country.flagUrl,
                    contentDescription = country.ru,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = if (compact) 14.dp else 18.dp, height = if (compact) 10.dp else 13.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                country?.ru ?: fallback ?: "",
                fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.Bold, color = AA.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (!compact) {
                Icon(
                    if (item.isSeries || item.isEpisode) Icons.Rounded.Tv else Icons.Rounded.Movie, null,
                    tint = if (item.isSeries || item.isEpisode) Color(0xFF4FC3F7) else AA.Green,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        item.yearLabel?.let {
            Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AA.Text, maxLines = 1)
        }
        val pct = item.userData?.playedPercentage
        if (pct != null && pct > 0 && pct < 100) {
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40B40000))) {
                Box(Modifier.fillMaxWidth((pct / 100.0).toFloat()).height(4.dp).background(AA.Red))
            }
        }
    }
}

/** Широкая карточка коллекции (фон + название) */
@Composable
fun WideCard(
    item: BaseItem,
    api: JellyfinApi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fv = rememberFocusVisual()
    val scale by animateFloatAsState(if (fv.active) 1.04f else 1f)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .scale(scale)
            .aspectRatio(16f / 7f)
            .clip(shape)
            .background(AA.SurfaceHigh)
            .border(if (fv.active) 3.dp else 1.5.dp, if (fv.active) AA.Red else AA.BorderDark, shape)
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = api.backdropUrl(item, 900),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        Text(
            (item.name ?: "").uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
            color = if (fv.active) AA.RedHover else AA.Text,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.align(Alignment.Center).padding(12.dp),
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AA.Red)
    }
}

@Composable
fun MessageBox(text: String, modifier: Modifier = Modifier, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = AA.TextDim, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            AAButton(actionText, null, onAction)
        }
    }
}

@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 5)).background(AA.Black), contentAlignment = Alignment.Center) {
        Text("AA", style = brandStyle((size.value / 2.6f).toInt()))
        AsyncImage(
            model = com.adultaristocrat.cinema.AppConfig.LOGO_URL,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

