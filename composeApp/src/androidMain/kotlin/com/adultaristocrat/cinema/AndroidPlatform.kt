package com.adultaristocrat.cinema

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Calendar
import java.util.UUID

class AndroidPlatform(private val activity: Activity) : Platform {

    private val prefs = activity.getSharedPreferences("aa_cinema", Context.MODE_PRIVATE)

    override val store: KeyValueStore = object : KeyValueStore {
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String?) {
            prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        }
    }

    override val deviceId: String = store.get("deviceId") ?: UUID.randomUUID().toString().also { store.put("deviceId", it) }

    override val isTv: Boolean = run {
        val ui = activity.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    override val isDesktop: Boolean = false

    override val deviceName: String =
        (if (isTv) "Android TV " else "Android ") + (Build.MODEL ?: "")

    override val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)

    override val todayIso: String
        get() {
            val c = Calendar.getInstance()
            fun two(v: Int) = if (v < 10) "0$v" else "$v"
            return "${c.get(Calendar.YEAR)}-${two(c.get(Calendar.MONTH) + 1)}-${two(c.get(Calendar.DAY_OF_MONTH))}"
        }

    override val fonts: AppFonts = loadFonts()

    @OptIn(ExperimentalTextApi::class)
    private fun loadFonts(): AppFonts {
        val assets = activity.assets
        fun has(path: String) = try {
            assets.open(path).close(); true
        } catch (e: Exception) {
            false
        }
        val body = if (has("fonts/Play-Regular.ttf") && has("fonts/Play-Bold.ttf")) {
            FontFamily(
                Font("fonts/Play-Regular.ttf", assets, FontWeight.Normal),
                Font("fonts/Play-Bold.ttf", assets, FontWeight.Bold),
            )
        } else FontFamily.Default
        val brand = if (has("fonts/RussoOne-Regular.ttf")) {
            FontFamily(Font("fonts/RussoOne-Regular.ttf", assets, FontWeight.Bold))
        } else body
        return AppFonts(body, brand)
    }

    // ---------------- Офлайн-библиотека ----------------

    override val offlineEngine: com.adultaristocrat.cinema.offline.OfflineEngine by lazy {
        com.adultaristocrat.cinema.offline.JvmOfflineEngine()
    }

    override fun readFile(name: String): String? =
        runCatching { java.io.File(activity.filesDir, name).takeIf { it.exists() }?.readText() }.getOrNull()

    override fun writeFile(name: String, text: String) {
        runCatching {
            val f = java.io.File(activity.filesDir, name)
            val tmp = java.io.File(activity.filesDir, "$name.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    // ---------------- Наушники ----------------

    private val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hpConnectedState by mutableStateOf(false)
    private var hpNameState by mutableStateOf<String?>(null)
    private var hpIsBluetooth = false

    override val headphonesConnected: Boolean get() = hpConnectedState
    override val headphonesName: String? get() = hpNameState

    private fun refreshHeadphones() {
        if (Build.VERSION.SDK_INT >= 23) {
            val types = setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                26, // TYPE_BLE_HEADSET (Android 12+)
                27, // TYPE_BLE_SPEAKER
            )
            val dev = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in types }
            hpConnectedState = dev != null
            hpIsBluetooth = dev != null && dev.type in setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 26, 27,
            )
            hpNameState = dev?.productName?.toString()?.takeIf { it.isNotBlank() }
        } else {
            @Suppress("DEPRECATION")
            hpConnectedState = audio.isBluetoothA2dpOn || audio.isWiredHeadsetOn
        }
    }

    init {
        refreshHeadphones()
        if (Build.VERSION.SDK_INT >= 23) {
            audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshHeadphones()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshHeadphones()
            }, mainHandler)
        }
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    private fun openBluetoothSettings() {
        try {
            activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                toast("Откройте настройки Bluetooth вручную")
            }
        }
    }

    override fun onHeadphonesClick() {
        refreshHeadphones()
        if (hpConnectedState) {
            if (!hpIsBluetooth) {
                toast("Подключены проводные наушники — отключите штекер")
                return
            }
            // Повторное нажатие — отключить наушники (выключаем Bluetooth с подтверждением системы)
            try {
                activity.startActivity(Intent("android.bluetooth.adapter.action.REQUEST_DISABLE"))
            } catch (e: Exception) {
                toast("Отключите наушники в настройках Bluetooth")
                openBluetoothSettings()
            }
            return
        }
        val adapter = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            openBluetoothSettings()
            return
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 42)
            toast("Разрешите доступ к Bluetooth и нажмите на наушники ещё раз")
            return
        }
        val enabled = try { adapter.isEnabled } catch (e: SecurityException) { true }
        if (!enabled) {
            // Включаем Bluetooth: сопряжённые наушники обычно подключаются сами
            try {
                @Suppress("MissingPermission")
                activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                toast("Включаем Bluetooth и ищем ваши наушники…")
                mainHandler.postDelayed({
                    refreshHeadphones()
                    if (!hpConnectedState) openBluetoothSettings()
                }, 9000)
            } catch (e: Exception) {
                openBluetoothSettings()
            }
            return
        }
        // Bluetooth включён, но наушники не подключены — открываем список устройств
        val hasPaired = try {
            @Suppress("MissingPermission")
            adapter.bondedDevices?.any { d ->
                val major = d.bluetoothClass?.majorDeviceClass
                major == BluetoothClass.Device.Major.AUDIO_VIDEO
            } == true
        } catch (e: SecurityException) {
            false
        }
        toast(if (hasPaired) "Включите наушники или выберите их в списке" else "Добавьте наушники: «Подключить новое устройство»")
        openBluetoothSettings()
    }

    override fun openUrl(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(activity, "Нет приложения, чтобы открыть ссылку", Toast.LENGTH_LONG).show()
        }
    }

    override fun setPlayerMode(enabled: Boolean) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!isTv) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!isTv) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Composable
    override fun logoPainter(): androidx.compose.ui.graphics.painter.Painter =
        androidx.compose.ui.res.painterResource(R.drawable.app_logo)

    @Composable
    override fun VideoSurface(
        request: PlaybackRequest,
        events: PlayerEvents,
        onControl: (PlayerControl?) -> Unit,
        modifier: Modifier,
    ) {
        AndroidVideoSurface(request, events, onControl, modifier)
    }
}
