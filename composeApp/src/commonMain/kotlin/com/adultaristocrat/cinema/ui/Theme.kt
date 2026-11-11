package com.adultaristocrat.cinema.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.adultaristocrat.cinema.AppFonts

/** Палитра из CSS темы NETFIN (Red & Black) */
object AA {
    /** Пропорции постера (стандарт 500×709) и портрета актёра (1000×1500) */
    const val POSTER_RATIO = 500f / 709f
    const val PORTRAIT_RATIO = 2f / 3f

    val Black = Color(0xFF000000)
    val Background = Color(0xFF070707)
    val BackgroundLight = Color(0xFF0D0D0D)
    val Surface = Color(0xFF121212)
    val SurfaceHigh = Color(0xFF1A1A1A)
    val SurfaceHigher = Color(0xFF242424)
    val Selector = Color(0xFF1E0000)

    val Border = Color(0xFF3D0000)
    val BorderDark = Color(0xFF290000)
    val BorderLight = Color(0x47CC0000)

    val Red = Color(0xFFCC0000)        // --activeColor
    val RedBright = Color(0xFFFF1111)  // заголовки/подсветка
    val RedHover = Color(0xFFFF3333)
    val RedPlay = Color(0xFFB40000)    // кнопка Play
    val RedDeep = Color(0xFF780000)

    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFA0A0A0)
    val TextFaint = Color(0x8CFFFFFF)
    val Green = Color(0xFF22C55E)
    val Gold = Color(0xFFFFC107)
    val ImdbYellow = Color(0xFFE2B616)

    val backgroundGradient = Brush.verticalGradient(listOf(BackgroundLight, Black))
    val cardFooterGradient = Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000)))
    val topScrim = Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent))
    val bottomScrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))
    val sectionLine = Brush.horizontalGradient(
        listOf(Color(0xFFDC0000), Color(0xD9CC0000), Color(0x80B40000), Color(0x2E960000), Color.Transparent)
    )
}

val LocalLogo = staticCompositionLocalOf<androidx.compose.ui.graphics.painter.Painter?> { null }

val LocalFonts = staticCompositionLocalOf { AppFonts(FontFamily.Default, FontFamily.Default) }

@Composable
fun AATheme(fonts: AppFonts, content: @Composable () -> Unit) {
    val body = fonts.body
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = body),
        displayMedium = base.displayMedium.copy(fontFamily = body),
        displaySmall = base.displaySmall.copy(fontFamily = body),
        headlineLarge = base.headlineLarge.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = body, fontWeight = FontWeight.Bold),
        labelSmall = base.labelSmall.copy(fontFamily = body),
    )
    val colors = darkColorScheme(
        primary = AA.Red,
        onPrimary = AA.Text,
        primaryContainer = AA.RedPlay,
        onPrimaryContainer = AA.Text,
        secondary = AA.RedBright,
        onSecondary = AA.Text,
        background = AA.Black,
        onBackground = AA.Text,
        surface = AA.Surface,
        onSurface = AA.Text,
        surfaceVariant = AA.SurfaceHigh,
        onSurfaceVariant = AA.TextDim,
        outline = AA.Border,
        error = Color(0xFFFF5252),
    )
    CompositionLocalProvider(LocalFonts provides fonts) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

/** Стиль бренда (как в шапке сайта) */
@Composable
fun brandStyle(size: Int = 22) = TextStyle(
    fontFamily = LocalFonts.current.brand,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    letterSpacing = (size * 0.08f).sp,
    color = AA.RedBright,
)
