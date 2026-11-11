package com.adultaristocrat.cinema

import android.app.Activity
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
    override fun VideoPlayer(request: PlaybackRequest, events: PlayerEvents, modifier: Modifier) {
        AndroidVideoPlayer(request, events, modifier)
    }
}
