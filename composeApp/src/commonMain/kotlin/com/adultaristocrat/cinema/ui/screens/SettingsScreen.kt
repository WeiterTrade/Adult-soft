package com.adultaristocrat.cinema.ui.screens

import com.adultaristocrat.cinema.ui.AATextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.adultaristocrat.cinema.AppConfig
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.AAButton
import com.adultaristocrat.cinema.ui.LocalUi
import com.adultaristocrat.cinema.ui.Logo
import com.adultaristocrat.cinema.ui.SectionTitle
import com.adultaristocrat.cinema.ui.brandStyle
import com.adultaristocrat.cinema.ui.rememberFocusVisual

@Composable
fun SettingsScreen(state: AppState) {
    val ui = LocalUi.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ui.hPad).padding(bottom = 100.dp)
            .widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(AppConfig.APP_NAME.uppercase(), style = brandStyle(20))
                Text("Версия ${AppConfig.VERSION}", color = AA.TextDim)
            }
        }
        if (!state.offlineMode) {
            SectionTitle("Аккаунт")
            InfoRow("Пользователь", state.userName)
            InfoRow("Устройство", state.platform.deviceName)

            SectionTitle("Быстрое подключение")
            QuickConnectAuthorize(state)
        }

        if (state.offline.supported) {
            SectionTitle("Библиотека")
            OfflineSettings(state)
        }

        SectionTitle("Воспроизведение")
        QualityRow(state)
        ToggleRow(
            "Всегда перекодировать видео",
            "Включите, если видео не запускается или нет звука (DTS, TrueHD). Нагружает сервер.",
            state.forceTranscode,
        ) { state.updateForceTranscode(it) }
        if (!state.platform.isDesktop) {
            ToggleRow(
                "Прямое воспроизведение файла",
                "Видео стартует чуть быстрее, но в некоторых MKV перемотка и сцены могут начинать фильм сначала.",
                state.directPlay,
            ) { state.updateDirectPlay(it) }
        }
        if (com.adultaristocrat.cinema.AppConfig.SHOW_SERIES) ToggleRow(
            "Автоматически следующая серия",
            "После окончания серии сразу включать следующую",
            state.autoNextEpisode,
        ) { state.updateAutoNext(it) }

        Spacer(Modifier.height(8.dp))
        if (state.offlineMode) {
            AAButton("Выйти из офлайн-режима", Icons.Rounded.Logout, { state.exitOffline() }, primary = false)
        } else {
            AAButton("Выйти из аккаунта", Icons.Rounded.Logout, { state.logout() }, primary = false)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xE0101010))
            .border(1.dp, AA.BorderDark, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = AA.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(130.dp))
        Text(value, color = AA.TextDim, modifier = Modifier.weight(1f))
    }
}

/** Качество видео по умолчанию — для слабого или мобильного интернета */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QualityRow(state: AppState) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color(0xE0101010))
            .border(1.dp, AA.BorderDark, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("Качество видео", color = AA.Text, style = MaterialTheme.typography.titleSmall)
        Text(
            "Для слабого интернета выберите 480p или ниже — сервер уменьшит видео. Можно менять и в плеере.",
            color = AA.TextDim, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.adultaristocrat.cinema.Quality.entries.forEach { q ->
                com.adultaristocrat.cinema.ui.AAChip(q.title, state.quality == q, { state.updateQuality(q) })
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val fv = rememberFocusVisual()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (fv.active) AA.Selector else Color(0xE0101010))
            .border(if (fv.active) 2.dp else 1.dp, if (fv.active) AA.Red else AA.BorderDark, RoundedCornerShape(10.dp))
            .clickable(interactionSource = fv.interaction, indication = null) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AA.Text, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = AA.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AA.Text, checkedTrackColor = AA.Red,
                uncheckedThumbColor = AA.TextDim, uncheckedTrackColor = AA.SurfaceHigher,
            ),
        )
    }
}

/** Ввод кода с другого устройства (ТВ, телефон, ПК), чтобы оно вошло в этот аккаунт без пароля */
@Composable
private fun QuickConnectAuthorize(state: AppState) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xE0101010))
            .border(1.dp, AA.BorderDark, RoundedCornerShape(10.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "На другом устройстве нажмите «Быстрое подключение» на экране входа и введите показанный там код сюда.",
            color = AA.TextDim, style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AATextField(
                value = code,
                onValueChange = { v -> code = v.filter { it.isDigit() }.take(8) },
                singleLine = true,
                label = { Text("Код") },
                colors = aaFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            AAButton(if (busy) "…" else "Подтвердить", Icons.Rounded.Check, {
                if (!busy && code.length >= 4) {
                    busy = true
                    scope.launch {
                        try {
                            state.api.quickConnectAuthorize(code)
                            state.showToast("Готово! Устройство входит в аккаунт")
                            code = ""
                        } catch (e: Exception) {
                            state.showToast("Код не подошёл или устарел")
                        } finally {
                            busy = false
                        }
                    }
                }
            })
        }
    }
}
