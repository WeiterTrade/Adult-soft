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
    class Person(val personId: String, val name: String) : Route()
    class Player(val itemId: String, val fromStart: Boolean = false, val startMs: Long? = null) : Route()
}

class AppState(val platform: Platform) {
    val api = JellyfinApi(platform.deviceName, platform.deviceId)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val store = platform.store

    var loggedIn by mutableStateOf(false)
        private set

    /** Фильмы из сетевых папок (без сервера) */
    val offline = com.adultaristocrat.cinema.offline.OfflineLibrary(platform, scope)

    /** Офлайн-режим: вход без сервера, только офлайн-библиотека */
    var offlineMode by mutableStateOf(false)
        private set

    fun enterOffline() {
        models.clear()
        tabRoutes.clear()
        stack.clear()
        stack.add(tabRoute(AppTab.HOME))
        offlineMode = true
    }

    fun exitOffline() {
        offline.flush()
        models.clear()
        tabRoutes.clear()
        stack.clear()
        stack.add(tabRoute(AppTab.HOME))
        offlineMode = false
    }
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
        startConnectivityWatch()
    }

    // ---------------- Связь с сервером: авто-переключение онлайн / офлайн ----------------

    /** null — ещё не проверяли */
    var serverReachable by mutableStateOf<Boolean?>(null)
        private set
    private var autoOffline = false

    private fun startConnectivityWatch() {
        scope.launch {
            while (true) {
                val server = if (AppConfig.isServerLocked) AppConfig.SERVER_URL else (store.get(K_SERVER) ?: "")
                val ok = if (server.isBlank()) true else runCatching {
                    kotlinx.coroutines.withTimeout(6000) { api.publicInfo(server) }
                    true
                }.getOrDefault(false)
                onReachability(ok)
                kotlinx.coroutines.delay(if (ok) 30_000 else 10_000)
            }
        }
    }

    /** Нажали «проверить связь» или открыли приложение — проверка сразу */
    fun checkConnectionNow() {
        scope.launch {
            val server = if (AppConfig.isServerLocked) AppConfig.SERVER_URL else (store.get(K_SERVER) ?: "")
            if (server.isBlank()) return@launch
            val ok = runCatching { kotlinx.coroutines.withTimeout(6000) { api.publicInfo(server) }; true }.getOrDefault(false)
            onReachability(ok)
        }
    }

    private fun onReachability(ok: Boolean) {
        serverReachable = ok
        // Во время просмотра не переключаемся — только после выхода из плеера
        if (current is Route.Player) return
        val canOffline = offline.enabled && offline.state.movies.isNotEmpty()
        if (offline.forceOffline && canOffline) {
            if (!offlineMode) enterOffline()
            return
        }
        if (!ok && canOffline && !offlineMode) {
            autoOffline = true
            enterOffline()
            showToast("Нет связи с сервером — включена офлайн-библиотека")
        } else if (ok && offlineMode && !offline.forceOffline) {
            autoOffline = false
            exitOffline()
            showToast(if (loggedIn) "Связь восстановлена — онлайн-библиотека" else "Связь восстановлена — войдите в аккаунт")
        }
    }

    suspend fun login(server: String, user: String, password: String) {
        saveSession(api.login(server, user, password), user)
    }

    /** Вход после подтверждения кода быстрого подключения на другом устройстве */
    suspend fun loginWithQuickConnect(secret: String) {
        saveSession(api.quickConnectAuthenticate(secret), "")
    }

    private fun saveSession(res: com.adultaristocrat.cinema.api.AuthResult, user: String) {
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

    /** Android: играть файл напрямую (быстрее старт, но перемотка в MKV без индекса может не работать) */
    var directPlay by mutableStateOf(store.get(K_DIRECT_PLAY) == "1")
        private set

    fun updateDirectPlay(v: Boolean) {
        directPlay = v
        store.put(K_DIRECT_PLAY, if (v) "1" else "0")
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

    /** Короткое сообщение внизу экрана */
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: kotlinx.coroutines.Job? = null

    fun showToast(text: String) {
        toast = text
        toastJob?.cancel()
        toastJob = scope.launch {
            kotlinx.coroutines.delay(3500)
            toast = null
        }
    }

    private var continueBusy = false

    /** Кнопка ▶: сразу запускает последний недосмотренный фильм */
    fun continueLast() {
        if (continueBusy) return
        continueBusy = true
        scope.launch {
            try {
                val last = if (offlineMode) offline.lastResumable()
                else runCatching { api.resume(limit = 1).items.firstOrNull() }.getOrNull() ?: offline.lastResumable()
                if (last != null) navigate(Route.Player(last.id))
                else showToast("Нет недосмотренных фильмов")
            } finally {
                continueBusy = false
            }
        }
    }

    fun openTab(tab: AppTab) {
        if (tab == AppTab.CONTINUE) {
            continueLast()
            return
        }
        if (offlineMode && tab !in setOf(AppTab.HOME, AppTab.SEARCH, AppTab.HISTORY, AppTab.SETTINGS)) {
            showToast("В офлайн-режиме доступны: Главная, Поиск, История, Настройки")
            return
        }
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
        if (!loggedIn && !offlineMode) return false
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
        private const val K_DIRECT_PLAY = "directPlay"
    }
}
