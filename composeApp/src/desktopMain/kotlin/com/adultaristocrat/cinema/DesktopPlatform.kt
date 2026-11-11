package com.adultaristocrat.cinema

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.GraphicsEnvironment
import java.awt.Desktop
import javax.sound.sampled.AudioSystem
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import java.net.InetAddress
import java.net.URI
import java.time.Year
import java.util.UUID
import java.util.prefs.Preferences

class DesktopPlatform(private val windowState: WindowState) : Platform {

    private val prefs: Preferences = Preferences.userRoot().node("adult-aristocrat-cinema")

    override val store: KeyValueStore = object : KeyValueStore {
        override fun get(key: String): String? = prefs.get(key, null)
        override fun put(key: String, value: String?) {
            if (value == null) prefs.remove(key) else prefs.put(key, value)
            runCatching { prefs.flush() }
        }
    }

    override val deviceId: String = store.get("deviceId") ?: UUID.randomUUID().toString().also { store.put("deviceId", it) }

    override val deviceName: String = run {
        val os = System.getProperty("os.name") ?: "PC"
        val host = runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        if (host.isNullOrBlank()) os else "$os ($host)"
    }

    override val isTv: Boolean = false
    override val isDesktop: Boolean = true
    override val currentYear: Int = Year.now().value
    override val todayIso: String get() = java.time.LocalDate.now().toString()
    override val fonts: AppFonts = loadFonts()

    private var placementBeforePlayer: WindowPlacement? = null

    private fun loadFonts(): AppFonts {
        fun bytes(path: String): ByteArray? =
            Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.use { it.readBytes() }

        val regular = bytes("fonts/Play-Regular.ttf")
        val bold = bytes("fonts/Play-Bold.ttf")
        val body = if (regular != null && bold != null) {
            FontFamily(
                Font("Play-Regular", regular, FontWeight.Normal),
                Font("Play-Bold", bold, FontWeight.Bold),
            )
        } else FontFamily.Default
        val brandBytes = bytes("fonts/RussoOne-Regular.ttf")
        val brand = if (brandBytes != null) FontFamily(Font("RussoOne", brandBytes, FontWeight.Bold)) else body
        return AppFonts(body, brand)
    }

    // ---------------- Наушники ----------------

    private var hpConnectedState by mutableStateOf(false)
    private var hpNameState by mutableStateOf<String?>(null)
    override val headphonesConnected: Boolean get() = hpConnectedState
    override val headphonesName: String? get() = hpNameState

    private val osName = (System.getProperty("os.name") ?: "").lowercase()
    private val isWindows = osName.startsWith("windows")

    init {
        // Проверяем подключённые наушники каждые 5 секунд (в фоне)
        thread(isDaemon = true, name = "headphones-watch") {
            while (true) {
                val found = runCatching { detectHeadphones() }.getOrNull()
                SwingUtilities.invokeLater {
                    hpConnectedState = found != null
                    hpNameState = found
                }
                Thread.sleep(5000)
            }
        }
    }

    /** Имя подключённых наушников или null */
    private fun detectHeadphones(): String? {
        if (!isWindows) {
            // Linux: PulseAudio / PipeWire — Bluetooth-устройства называются bluez_*
            val out = runCatching {
                val p = ProcessBuilder("pactl", "list", "short", "sinks").redirectErrorStream(true).start()
                p.inputStream.bufferedReader().readText().also { p.waitFor() }
            }.getOrNull()
            out?.lineSequence()?.firstOrNull { it.contains("bluez") }?.let { return "Bluetooth" }
        }
        // Windows (и запасной вариант): ищем аудиовыход с «наушниковым» названием
        val keywords = listOf(
            "bluetooth", "hands-free", "handsfree", "headphone", "headset", "наушник", "гарнитур",
            "airpods", "buds", "wh-", "wf-", "jbl", "a2dp",
        )
        return AudioSystem.getMixerInfo().map { "${it.name} ${it.description}" }
            .firstOrNull { name ->
                val n = name.lowercase()
                keywords.any { n.contains(it) } && !n.contains("mix")
            }
            ?.substringBefore(",")
            ?.trim()
    }

    override fun onHeadphonesClick() {
        // Открываем системное окно Bluetooth: там включается Bluetooth и подключаются наушники
        runCatching {
            if (isWindows) {
                val v = System.getProperty("os.version") ?: ""
                if (v.startsWith("6.")) {
                    // Windows 7 / 8: «Устройства Bluetooth»
                    ProcessBuilder("control.exe", "bthprops.cpl").start()
                } else {
                    // Windows 10 / 11: Параметры → Bluetooth и устройства
                    ProcessBuilder("cmd", "/c", "start", "", "ms-settings:bluetooth").start()
                }
            } else {
                val tries = listOf(
                    listOf("blueman-manager"),
                    listOf("gnome-control-center", "bluetooth"),
                    listOf("systemsettings", "kcm_bluetooth"),
                    listOf("xdg-open", "settings://bluetooth"),
                )
                for (cmd in tries) {
                    if (runCatching { ProcessBuilder(cmd).start() }.isSuccess) break
                }
            }
        }
    }

    override fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            } else {
                Runtime.getRuntime().exec(arrayOf("xdg-open", url))
            }
        }
    }

    override val canToggleFullscreen: Boolean get() = true

    // Окно без системной рамки: «развернуть» делаем сами, чтобы не перекрывать панель задач
    private var restoreBounds: Pair<WindowPosition, DpSize>? = null
    var isMaximized by mutableStateOf(false)
        private set

    fun toggleMaximize() {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            windowState.placement = WindowPlacement.Floating
            return
        }
        if (isMaximized) {
            restoreBounds?.let { (pos, size) ->
                windowState.position = pos
                windowState.size = size
            }
            isMaximized = false
        } else {
            restoreBounds = windowState.position to windowState.size
            val r = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            windowState.placement = WindowPlacement.Floating
            windowState.position = WindowPosition(r.x.dp, r.y.dp)
            windowState.size = DpSize(r.width.dp, r.height.dp)
            isMaximized = true
        }
    }

    /** Ширина окна в обычном (не развёрнутом) состоянии, в пикселях экрана */
    val restoredWidth: Int get() = restoreBounds?.second?.width?.value?.toInt() ?: 1280

    fun minimize() {
        windowState.isMinimized = true
    }

    override fun toggleFullscreen() {
        windowState.placement =
            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
    }

    override val isFullscreen: Boolean get() = windowState.placement == WindowPlacement.Fullscreen

    override fun setPlayerMode(enabled: Boolean) {
        if (enabled) {
            placementBeforePlayer = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
        } else {
            placementBeforePlayer?.let { windowState.placement = it }
            placementBeforePlayer = null
        }
    }

    @Composable
    override fun VideoSurface(
        request: PlaybackRequest,
        events: PlayerEvents,
        onControl: (PlayerControl?) -> Unit,
        modifier: Modifier,
    ) {
        VlcVideoSurface(request, events, onControl, modifier)
    }
}
