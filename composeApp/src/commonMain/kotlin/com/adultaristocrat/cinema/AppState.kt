package com.adultaristocrat.cinema

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adultaristocrat.cinema.api.JellyfinApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    HOME("Главная"),
    CONTINUE("Продолжить"),
    SEARCH("Поиск"),
    RANDOM("Случайное"),
    HISTORY("История"),
    FAVORITES("Избранное"),
    COLLECTIONS("Коллекции"),
    SETTINGS("Настройки"),
}

/** Экраны приложения. Обычные классы (не data) — сравниваются по ссылке, это ключи для моделей. */
sealed class Route {
    class Tab(val tab: AppTab) : Route()
    class Detail(val itemId: String) : Route()
    class Folder(val parentId: String, val title: String) : Route()
    class Player(val itemId: String, val fromStart: Boolean = false) : Route()
}

class AppState(val platform: Platform) {
    val api = JellyfinApi(platform.deviceName, platform.deviceId)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val store = platform.store

    var loggedIn by mutableStateOf(false)
        private set
    var userName by mutableStateOf(store.get(K_USER_NAME) ?: "")
        private set
    val lastServer: String get() = if (AppConfig.isServerLocked) AppConfig.SERVER_URL else store.get(K_SERVER) ?: ""
    val lastUser: String get() = store.get(K_USER_NAME) ?: ""

    /** Всегда перекодировать видео (если прямое воспроизведение не работает на устройстве) */
    var forceTranscode by mutableStateOf(store.get(K_FORCE_TRANSCODE) == "1")
        private set

    /** Автоматически включать следующую серию */
    var autoNextEpisode by mutableStateOf(store.get(K_AUTO_NEXT) != "0")
        private set

    val stack = mutableStateListOf<Route>(Route.Tab(AppTab.HOME))
    private val models = HashMap<Any, Any>()
    private val tabRoutes = HashMap<AppTab, Route.Tab>()

    init {
        val server = if (AppConfig.isServerLocked) AppConfig.SERVER_URL else store.get(K_SERVER)
        val token = store.get(K_TOKEN)
        val uid = store.get(K_USER_ID)
        if (!server.isNullOrBlank() && !token.isNullOrBlank() && !uid.isNullOrBlank()) {
            api.configure(server, token, uid)
            loggedIn = true
        }
        stack.clear()
        stack.add(tabRoute(AppTab.HOME))
    }

    suspend fun login(server: String, user: String, password: String) {
        val res = api.login(server, user, password)
        val token = res.accessToken ?: error("Не удалось войти, попробуйте ещё раз")
        val u = res.user ?: error("Не удалось войти, попробуйте ещё раз")
        store.put(K_SERVER, api.baseUrl)
        store.put(K_TOKEN, token)
        store.put(K_USER_ID, u.id)
        store.put(K_USER_NAME, u.name ?: user)
        userName = u.name ?: user
        models.clear()
        tabRoutes.clear()
        stack.clear()
        stack.add(tabRoute(AppTab.HOME))
        loggedIn = true
    }

    fun logout() {
        scope.launch { api.logout() }
        store.put(K_TOKEN, null)
        store.put(K_USER_ID, null)
        models.clear()
        tabRoutes.clear()
        stack.clear()
        stack.add(tabRoute(AppTab.HOME))
        loggedIn = false
    }

    fun updateForceTranscode(v: Boolean) {
        forceTranscode = v
        store.put(K_FORCE_TRANSCODE, if (v) "1" else "0")
    }

    fun updateAutoNext(v: Boolean) {
        autoNextEpisode = v
        store.put(K_AUTO_NEXT, if (v) "1" else "0")
    }

    // ---------------- Навигация ----------------

    private fun tabRoute(tab: AppTab): Route.Tab = tabRoutes.getOrPut(tab) { Route.Tab(tab) }

    val current: Route get() = stack.last()

    val currentTab: AppTab
        get() = (stack.lastOrNull { it is Route.Tab } as? Route.Tab)?.tab ?: AppTab.HOME

    fun openTab(tab: AppTab) {
        // Повторное нажатие на «Случайное» — новая подборка
        if (tab == AppTab.RANDOM && currentTab == AppTab.RANDOM && stack.size == 1) {
            models.remove(tabRoute(tab))
            tabRoutes.remove(tab)
        }
        dropModelsAbove(0)
        stack.clear()
        stack.add(tabRoute(tab))
    }

    fun navigate(route: Route) {
        stack.add(route)
    }

    /** Заменить верхний экран (например, следующая серия в плеере) */
    fun replaceTop(route: Route) {
        val old = stack.removeAt(stack.lastIndex)
        models.remove(old)
        stack.add(route)
    }

    /** @return false, если дальше назад некуда (можно закрывать приложение) */
    fun back(): Boolean {
        if (!loggedIn) return false
        if (stack.size > 1) {
            val old = stack.removeAt(stack.lastIndex)
            models.remove(old)
            return true
        }
        if (currentTab != AppTab.HOME) {
            openTab(AppTab.HOME)
            return true
        }
        return false
    }

    private fun dropModelsAbove(index: Int) {
        for (i in stack.indices) if (i > index) models.remove(stack[i])
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> model(key: Any, create: () -> T): T = models.getOrPut(key, create) as T

    fun invalidate(key: Any) {
        models.remove(key)
    }

    companion object {
        private const val K_SERVER = "server"
        private const val K_TOKEN = "token"
        private const val K_USER_ID = "userId"
        private const val K_USER_NAME = "userName"
        private const val K_FORCE_TRANSCODE = "forceTranscode"
        private const val K_AUTO_NEXT = "autoNext"
    }
}
