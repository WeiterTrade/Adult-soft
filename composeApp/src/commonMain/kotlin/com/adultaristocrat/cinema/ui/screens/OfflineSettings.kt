package com.adultaristocrat.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.offline.OfflineHost
import com.adultaristocrat.cinema.offline.OfflineSource
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.AAButton
import com.adultaristocrat.cinema.ui.LocalUi
import com.adultaristocrat.cinema.ui.rememberFocusVisual
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * «Офлайн-библиотека» в настройках — свёрнутый блок.
 * Подключение сетевых папок (SMB): по IP или поиском в сети, выбор папок, сканирование NFO.
 */
@Composable
fun OfflineSettings(state: AppState) {
    val lib = state.offline
    if (!lib.supported) return
    var expanded by remember { mutableStateOf(state.offlineMode) }
    var addHost by remember { mutableStateOf<String?>(null) }   // открыт диалог добавления (адрес)
    var discovering by remember { mutableStateOf(false) }

    val st = lib.state
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xE0101010))
            .border(1.dp, AA.BorderDark, RoundedCornerShape(12.dp)),
    ) {
        // Заголовок (сворачивается)
        RowButton(
            icon = Icons.Rounded.FolderShared,
            title = "Офлайн-библиотека",
            subtitle = if (st.enabled) "Включена · фильмов: ${st.movies.size} · папок: ${st.sources.size}" else "Фильмы из сетевых папок (NAS, компьютер)",
            trailing = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
        ) { expanded = !expanded }

        if (expanded) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow("Включить офлайн-библиотеку", st.enabled) { lib.setEnabled(it) }

                if (st.enabled) {
                    Text(
                        "Папка с фильмами в стиле Kodi: в каждой папке — видео (.mkv), .nfo, постер (-poster.jpg), фанарт " +
                            "(-fanart.jpg) и папка .actors с фото актёров. Подпапки сканируются автоматически.",
                        color = AA.TextDim, style = MaterialTheme.typography.bodySmall,
                    )

                    if (st.sources.isEmpty()) {
                        Text("Папки ещё не добавлены", color = AA.TextDim)
                    }
                    st.sources.forEach { src ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF161616))
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = AA.Gold, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(src.title, color = AA.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            SmallIconButton(Icons.Rounded.Delete, "Убрать папку") { lib.removeSource(src) }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AAButton("По IP-адресу", Icons.Rounded.Add, { addHost = "" }, primary = false)
                        AAButton("Найти в сети", Icons.Rounded.Wifi, { discovering = true }, primary = false)
                    }

                    if (st.sources.isNotEmpty()) {
                        if (lib.scanning) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = AA.Red, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(lib.scanStatus ?: "", color = AA.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                SmallIconButton(Icons.Rounded.Stop, "Остановить") { lib.cancelScan() }
                            }
                        } else {
                            AAButton("Сканировать папки", Icons.Rounded.Refresh, {
                                lib.scan { n -> state.showToast("Офлайн-библиотека: найдено фильмов — $n") }
                            })
                            lib.scanStatus?.let { Text(it, color = AA.TextDim, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }

    if (discovering) {
        DiscoverDialog(state, onClose = { discovering = false }) { host ->
            discovering = false
            addHost = host.ip
        }
    }
    addHost?.let { h ->
        AddSourceDialog(state, initialHost = h, onClose = { addHost = null }) { src ->
            lib.addSource(src)
            addHost = null
            state.showToast("Папка добавлена. Нажмите «Сканировать папки».")
        }
    }
}

@Composable
private fun RowButton(icon: ImageVector, title: String, subtitle: String, trailing: ImageVector, onClick: () -> Unit) {
    val fv = rememberFocusVisual()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (fv.active) AA.Selector else Color.Transparent)
            .border(if (fv.active) 2.dp else 0.dp, if (fv.active) AA.Red else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AA.RedBright, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AA.Text, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = AA.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        Icon(trailing, null, tint = AA.Text)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val fv = rememberFocusVisual()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (fv.active) AA.Selector else Color(0xFF161616))
            .border(if (fv.active) 2.dp else 0.dp, if (fv.active) AA.Red else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(interactionSource = fv.interaction, indication = null) { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = AA.Text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AA.Text, checkedTrackColor = AA.Red,
                uncheckedThumbColor = AA.TextDim, uncheckedTrackColor = AA.SurfaceHigher,
            ),
        )
    }
}

@Composable
private fun SmallIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val fv = rememberFocusVisual()
    Icon(
        icon, description, tint = if (fv.active) AA.Text else AA.TextDim,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (fv.active) AA.Red else Color.Transparent)
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(8.dp).size(22.dp),
    )
}

@Composable
private fun ListRow(icon: ImageVector, text: String, sub: String? = null, onClick: () -> Unit) {
    val fv = rememberFocusVisual()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (fv.active) AA.Red else Color(0xFF161616))
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (fv.active) AA.Text else AA.Gold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = AA.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = if (fv.active) AA.Text else AA.TextDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DialogFrame(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalUi.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(if (ui.isPhone) 0.95f else 0.6f)
                .fillMaxHeight(0.85f)
                .background(Color(0xF5080000), RoundedCornerShape(18.dp))
                .border(1.dp, AA.Border, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = AA.Text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                SmallIconButton(Icons.Rounded.Close, "Закрыть", onClose)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Поиск компьютеров и NAS с общими папками в локальной сети */
@Composable
private fun DiscoverDialog(state: AppState, onClose: () -> Unit, onPick: (OfflineHost) -> Unit) {
    val hosts = remember { mutableStateListOf<OfflineHost>() }
    var running by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) {
        job = scope.launch {
            try {
                state.offline.engine?.discover { h -> state.scope.launch { if (hosts.none { it.ip == h.ip }) hosts.add(h) } }
            } finally {
                running = false
            }
        }
    }
    DialogFrame("Поиск в сети", onClose = { job?.cancel(); onClose() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                CircularProgressIndicator(color = AA.Red, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Ищем компьютеры с открытыми общими папками…", color = AA.TextDim)
            } else {
                Text(if (hosts.isEmpty()) "Ничего не найдено. Проверьте, что включён общий доступ к папкам." else "Поиск завершён", color = AA.TextDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            hosts.sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }.forEach { h ->
                ListRow(Icons.Rounded.Computer, h.name ?: h.ip, if (h.name != null) h.ip else null) {
                    job?.cancel()
                    onPick(h)
                }
            }
        }
    }
}

/** Подключение к компьютеру по IP: логин/пароль → общая папка → подпапка → «Выбрать» */
@Composable
private fun AddSourceDialog(state: AppState, initialHost: String, onClose: () -> Unit, onAdd: (OfflineSource) -> Unit) {
    val engine = state.offline.engine ?: return
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(initialHost) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var shares by remember { mutableStateOf<List<String>?>(null) }
    var share by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf("") }
    var dirs by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun source() = OfflineSource(host.trim(), share ?: "", path, user.trim(), password)

    fun openDir(newPath: String) {
        path = newPath
        busy = true
        error = null
        scope.launch {
            try {
                dirs = engine.listDirs(source())
            } catch (e: Exception) {
                error = "Не удалось открыть папку: ${e.message ?: e}"
                dirs = emptyList()
            } finally {
                busy = false
            }
        }
    }

    fun connect() {
        if (host.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try {
                shares = engine.listShares(host.trim(), user.trim(), password)
            } catch (e: Exception) {
                error = "Нет доступа: ${e.message ?: e}\nПроверьте IP, логин и пароль (для папок без пароля оставьте поля пустыми)."
            } finally {
                busy = false
            }
        }
    }

    DialogFrame(if (share == null) "Подключение к компьютеру" else "Выберите папку", onClose) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shares == null) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it.trim() }, singleLine = true,
                    label = { Text("IP-адрес или имя компьютера") }, placeholder = { Text("192.168.1.10") },
                    colors = aaFieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it }, singleLine = true,
                    label = { Text("Пользователь (если нужен)") }, colors = aaFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    label = { Text("Пароль (если нужен)") }, visualTransformation = PasswordVisualTransformation(),
                    colors = aaFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                if (busy) CircularProgressIndicator(color = AA.Red)
                else AAButton("Показать общие папки", Icons.Rounded.Check, { connect() })
            } else if (share == null) {
                Text("\\\\$host", color = AA.TextDim)
                if (shares.isNullOrEmpty()) Text("Общих папок не найдено", color = AA.TextDim)
                shares?.forEach { s ->
                    ListRow(Icons.Rounded.FolderShared, s) {
                        share = s
                        openDir("")
                    }
                }
            } else {
                Text(source().title, color = AA.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ListRow(Icons.Rounded.ArrowUpward, "Наверх") {
                    if (path.isBlank()) {
                        share = null
                        dirs = emptyList()
                    } else openDir(path.trim('/').substringBeforeLast('/', ""))
                }
                if (busy) CircularProgressIndicator(color = AA.Red)
                dirs.forEach { d ->
                    ListRow(Icons.Rounded.Folder, d) { openDir(if (path.isBlank()) d else "${path.trim('/')}/$d") }
                }
            }
            error?.let { Text(it, color = Color(0xFFFF6B6B)) }
        }
        if (share != null) {
            Spacer(Modifier.height(10.dp))
            AAButton("Выбрать эту папку", Icons.Rounded.Check, { onAdd(source()) }, Modifier.fillMaxWidth())
        }
    }
}
