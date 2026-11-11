package com.adultaristocrat.cinema.ui.screens

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
        SectionTitle("Аккаунт")
        InfoRow("Пользователь", state.userName)
        InfoRow("Устройство", state.platform.deviceName)

        SectionTitle("Воспроизведение")
        ToggleRow(
            "Всегда перекодировать видео",
            "Включите, если видео не запускается или нет звука (DTS, TrueHD). Нагружает сервер.",
            state.forceTranscode,
        ) { state.updateForceTranscode(it) }
        ToggleRow(
            "Автоматически следующая серия",
            "После окончания серии сразу включать следующую",
            state.autoNextEpisode,
        ) { state.updateAutoNext(it) }

        Spacer(Modifier.height(8.dp))
        AAButton("Выйти из аккаунта", Icons.Rounded.Logout, { state.logout() }, primary = false)
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
