package com.adultaristocrat.cinema

/**
 * Качество видео. «Оригинал» — файл как есть; остальное — сервер перекодирует
 * в меньшее разрешение и битрейт (для слабого или мобильного интернета).
 */
enum class Quality(val title: String, val maxHeight: Int, val bitrate: Int, val hint: String) {
    ORIGINAL("Оригинал", 0, 0, "как в файле"),
    Q720("720p", 720, 3_000_000, "≈ 3 Мбит/с"),
    Q480("480p", 480, 1_500_000, "≈ 1,5 Мбит/с"),
    Q360("360p", 360, 800_000, "≈ 0,8 Мбит/с"),
    Q240("240p", 240, 400_000, "≈ 0,4 Мбит/с");

    companion object {
        fun of(name: String?): Quality = entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}
