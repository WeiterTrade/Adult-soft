package com.adultaristocrat.cinema

/**
 * Главные настройки бренда. Меняйте здесь.
 */
object AppConfig {
    const val APP_NAME = "Adult Aristocrat"
    const val VERSION = "1.0.0"

    /**
     * Адрес вашего кинотеатра (Jellyfin). Приложение привязано к нему намертво:
     * пользователь видит только логин и пароль, никаких «серверов».
     * Пример: "https://kino.example.ru" или "http://123.45.67.89:8096".
     * Если оставить пустым — на экране входа появится поле адреса (режим для отладки).
     */
    const val SERVER_URL = "http://185.73.126.250:8096"

    val isServerLocked: Boolean get() = SERVER_URL.isNotBlank()

    /** Логотип (грузится из сети, если доступен) */
    const val LOGO_URL = "https://cdn.phototourl.com/member/2026-10-10-b3dbbcbd-aca6-4c8d-8305-7210f212a4ac.png"

    /** Сколько элементов грузить за одну страницу сетки */
    const val PAGE_SIZE = 60
}
