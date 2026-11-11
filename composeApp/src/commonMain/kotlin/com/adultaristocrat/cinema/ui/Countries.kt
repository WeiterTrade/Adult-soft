package com.adultaristocrat.cinema.ui

/**
 * Страны производства: русское название + флаг.
 * Jellyfin отдаёт страны по-английски (из TMDB), иногда уже по-русски — понимаем оба варианта.
 * Флаги — картинки flagcdn.com (эмодзи-флаги не рисуются в Windows).
 */
object Countries {
    data class Country(val iso: String, val ru: String) {
        val flagUrl: String get() = "https://flagcdn.com/w40/$iso.png"
    }

    // iso, русское название, варианты написания
    private val data: List<Triple<String, String, List<String>>> = listOf(
        Triple("us", "США", listOf("United States of America", "United States", "USA", "US", "США", "Соединённые Штаты", "Соединенные Штаты Америки")),
        Triple("gb", "Великобритания", listOf("United Kingdom", "UK", "Great Britain", "England", "Великобритания", "Англия")),
        Triple("cz", "Чехия", listOf("Czech Republic", "Czechia", "Чехия")),
        Triple("hu", "Венгрия", listOf("Hungary", "Венгрия")),
        Triple("de", "Германия", listOf("Germany", "West Germany", "Германия")),
        Triple("fr", "Франция", listOf("France", "Франция")),
        Triple("it", "Италия", listOf("Italy", "Италия")),
        Triple("es", "Испания", listOf("Spain", "Испания")),
        Triple("ru", "Россия", listOf("Russia", "Russian Federation", "Россия", "СССР", "Soviet Union")),
        Triple("ua", "Украина", listOf("Ukraine", "Украина")),
        Triple("by", "Беларусь", listOf("Belarus", "Беларусь", "Белоруссия")),
        Triple("kz", "Казахстан", listOf("Kazakhstan", "Казахстан")),
        Triple("ca", "Канада", listOf("Canada", "Канада")),
        Triple("br", "Бразилия", listOf("Brazil", "Бразилия")),
        Triple("jp", "Япония", listOf("Japan", "Япония")),
        Triple("kr", "Южная Корея", listOf("South Korea", "Korea", "Republic of Korea", "Korea, Republic of", "Южная Корея", "Корея")),
        Triple("cn", "Китай", listOf("China", "People's Republic of China", "Китай")),
        Triple("hk", "Гонконг", listOf("Hong Kong", "Гонконг")),
        Triple("tw", "Тайвань", listOf("Taiwan", "Тайвань")),
        Triple("th", "Таиланд", listOf("Thailand", "Таиланд")),
        Triple("ph", "Филиппины", listOf("Philippines", "Филиппины")),
        Triple("id", "Индонезия", listOf("Indonesia", "Индонезия")),
        Triple("vn", "Вьетнам", listOf("Vietnam", "Viet Nam", "Вьетнам")),
        Triple("in", "Индия", listOf("India", "Индия")),
        Triple("nl", "Нидерланды", listOf("Netherlands", "Holland", "Нидерланды", "Голландия")),
        Triple("be", "Бельгия", listOf("Belgium", "Бельгия")),
        Triple("lu", "Люксембург", listOf("Luxembourg", "Люксембург")),
        Triple("ch", "Швейцария", listOf("Switzerland", "Швейцария")),
        Triple("at", "Австрия", listOf("Austria", "Австрия")),
        Triple("pl", "Польша", listOf("Poland", "Польша")),
        Triple("sk", "Словакия", listOf("Slovakia", "Словакия")),
        Triple("ro", "Румыния", listOf("Romania", "Румыния")),
        Triple("bg", "Болгария", listOf("Bulgaria", "Болгария")),
        Triple("rs", "Сербия", listOf("Serbia", "Сербия")),
        Triple("hr", "Хорватия", listOf("Croatia", "Хорватия")),
        Triple("si", "Словения", listOf("Slovenia", "Словения")),
        Triple("gr", "Греция", listOf("Greece", "Греция")),
        Triple("pt", "Португалия", listOf("Portugal", "Португалия")),
        Triple("ie", "Ирландия", listOf("Ireland", "Ирландия")),
        Triple("se", "Швеция", listOf("Sweden", "Швеция")),
        Triple("no", "Норвегия", listOf("Norway", "Норвегия")),
        Triple("dk", "Дания", listOf("Denmark", "Дания")),
        Triple("fi", "Финляндия", listOf("Finland", "Финляндия")),
        Triple("is", "Исландия", listOf("Iceland", "Исландия")),
        Triple("ee", "Эстония", listOf("Estonia", "Эстония")),
        Triple("lv", "Латвия", listOf("Latvia", "Латвия")),
        Triple("lt", "Литва", listOf("Lithuania", "Литва")),
        Triple("md", "Молдова", listOf("Moldova", "Молдова", "Молдавия")),
        Triple("ge", "Грузия", listOf("Georgia", "Грузия")),
        Triple("am", "Армения", listOf("Armenia", "Армения")),
        Triple("tr", "Турция", listOf("Turkey", "Türkiye", "Турция")),
        Triple("il", "Израиль", listOf("Israel", "Израиль")),
        Triple("eg", "Египет", listOf("Egypt", "Египет")),
        Triple("ma", "Марокко", listOf("Morocco", "Марокко")),
        Triple("za", "ЮАР", listOf("South Africa", "ЮАР")),
        Triple("au", "Австралия", listOf("Australia", "Австралия")),
        Triple("nz", "Новая Зеландия", listOf("New Zealand", "Новая Зеландия")),
        Triple("mx", "Мексика", listOf("Mexico", "Мексика")),
        Triple("ar", "Аргентина", listOf("Argentina", "Аргентина")),
        Triple("co", "Колумбия", listOf("Colombia", "Колумбия")),
        Triple("ve", "Венесуэла", listOf("Venezuela", "Венесуэла")),
        Triple("cl", "Чили", listOf("Chile", "Чили")),
        Triple("pe", "Перу", listOf("Peru", "Перу")),
        Triple("cu", "Куба", listOf("Cuba", "Куба")),
        Triple("do", "Доминикана", listOf("Dominican Republic", "Доминикана")),
        Triple("sg", "Сингапур", listOf("Singapore", "Сингапур")),
        Triple("my", "Малайзия", listOf("Malaysia", "Малайзия")),
        Triple("ae", "ОАЭ", listOf("United Arab Emirates", "ОАЭ")),
        Triple("ir", "Иран", listOf("Iran", "Иран")),
    )

    private val index: Map<String, Country> by lazy {
        val m = HashMap<String, Country>()
        for ((iso, ru, names) in data) {
            val c = Country(iso, ru)
            m[iso] = c
            m[ru.lowercase()] = c
            names.forEach { m[it.lowercase()] = c }
        }
        m
    }

    fun find(name: String?): Country? {
        val key = name?.trim()?.lowercase() ?: return null
        if (key.isEmpty()) return null
        return index[key]
    }
}
