package com.adultaristocrat.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adultaristocrat.cinema.api.ItemFilter
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.AAButton
import com.adultaristocrat.cinema.ui.AAChip
import com.adultaristocrat.cinema.ui.LocalUi
import kotlinx.coroutines.delay

private const val MAX_YEARS = 5

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterDialog(
    initial: ItemFilter,
    genres: List<String>?,
    currentYear: Int,
    onApply: (ItemFilter) -> Unit,
) {
    var types by remember { mutableStateOf(initial.types) }
    var selGenres by remember { mutableStateOf(initial.genres) }
    var years by remember { mutableStateOf(initial.years) }
    val ui = LocalUi.current
    val firstFocus = remember { FocusRequester() }

    fun current() = ItemFilter(types, selGenres, years)

    Dialog(
        onDismissRequest = { onApply(current()) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(if (ui.isPhone) 0.96f else 0.86f)
                .fillMaxHeight(0.9f)
                .background(Color(0xF5080000), RoundedCornerShape(18.dp))
                .border(1.dp, AA.Border, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Label("Тип контента")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AAChip("Все", types.isEmpty(), { types = emptySet() }, Modifier.focusRequester(firstFocus))
                    AAChip("Фильмы", "Movie" in types, { types = toggle(types, "Movie") })
                    AAChip("Сериалы", "Series" in types, { types = toggle(types, "Series") })
                }

                Label("Жанры")
                if (genres == null) {
                    CircularProgressIndicator(color = AA.Red, modifier = Modifier.padding(8.dp))
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AAChip("Все жанры", selGenres.isEmpty(), { selGenres = emptySet() })
                        genres.forEach { g ->
                            AAChip(g, g in selGenres, { selGenres = toggle(selGenres, g) })
                        }
                    }
                }

                Row {
                    Label("Годы")
                    Text(
                        "  можно выбрать до $MAX_YEARS",
                        color = AA.TextDim, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AAChip("Все годы", years.isEmpty(), { years = emptySet() })
                    for (y in (currentYear + 1) downTo 1950) {
                        AAChip(y.toString(), y in years, {
                            years = if (y in years) years - y
                            else if (years.size < MAX_YEARS) years + y
                            else years
                        })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                if (ui.isTv) "«Назад» тоже применяет фильтр" else "",
                color = AA.TextDim, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AAButton("Сбросить", Icons.Rounded.Close, { onApply(ItemFilter()) }, primary = false)
                AAButton("Применить", Icons.Rounded.Check, { onApply(current()) })
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = AA.Text,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

private fun <T> toggle(set: Set<T>, v: T): Set<T> = if (v in set) set - v else set + v
