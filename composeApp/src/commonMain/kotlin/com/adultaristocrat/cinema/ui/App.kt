package com.adultaristocrat.cinema.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.AppTab
import com.adultaristocrat.cinema.Route
import com.adultaristocrat.cinema.ui.screens.BrowseScreen
import com.adultaristocrat.cinema.ui.screens.DetailScreen
import com.adultaristocrat.cinema.ui.screens.HomeScreen
import com.adultaristocrat.cinema.ui.screens.LoginScreen
import com.adultaristocrat.cinema.ui.screens.PlayerScreen
import com.adultaristocrat.cinema.ui.screens.PersonScreen
import com.adultaristocrat.cinema.ui.screens.SearchScreen
import com.adultaristocrat.cinema.ui.screens.SettingsScreen

fun AppTab.icon(): ImageVector = when (this) {
    AppTab.HOME -> Icons.Rounded.Home
    AppTab.CONTINUE -> Icons.Rounded.PlayArrow
    AppTab.SEARCH -> Icons.Rounded.Search
    AppTab.RANDOM -> Icons.Rounded.Shuffle
    AppTab.HISTORY -> Icons.Rounded.History
    AppTab.FAVORITES -> Icons.Rounded.Bookmarks
    AppTab.COLLECTIONS -> Icons.Rounded.ViewList
    AppTab.SETTINGS -> Icons.Rounded.Settings
}

@Composable
fun App(state: AppState) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).crossfade(true).build()
    }
    AATheme(state.platform.fonts) {
        BoxWithConstraints(Modifier.fillMaxSize().background(AA.backgroundGradient)) {
            val platform = state.platform
            val isPhone = !platform.isTv && !platform.isDesktop && maxWidth < 700.dp
            val metrics = UiMetrics(
                isTv = platform.isTv,
                isPhone = isPhone,
                isDesktop = platform.isDesktop,
                cardMin = when {
                    isPhone -> 82.dp
                    platform.isTv -> 118.dp
                    else -> 150.dp
                },
                wideMin = if (isPhone) 260.dp else 300.dp,
                hPad = if (isPhone) 8.dp else 16.dp,
            )
            CompositionLocalProvider(LocalUi provides metrics, LocalLogo provides platform.logoPainter()) {
                if (!state.loggedIn && !state.offlineMode) {
                    LoginScreen(state)
                } else {
                    Shell(state)
                }
            }
        }
    }
}

@Composable
private fun Shell(state: AppState) {
    Box(Modifier.fillMaxSize()) {
        ShellContent(state)
        state.toast?.let { msg ->
            Text(
                msg,
                color = AA.Text,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xF0140000))
                    .border(1.dp, AA.Red, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ShellContent(state: AppState) {
    val ui = LocalUi.current
    val route = state.current
    if (route is Route.Player) {
        key(route) { PlayerScreen(state, route) }
        return
    }
    if (ui.isPhone) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            key(route) { RouteContent(state, route) }
            BottomNav(
                state.currentTab, state.platform, { state.openTab(it) },
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    } else {
        Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            NavRail(state.currentTab, state.platform) { state.openTab(it) }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                key(route) { RouteContent(state, route) }
            }
        }
    }
}

@Composable
private fun RouteContent(state: AppState, route: Route) {
    when (route) {
        is Route.Tab -> when (route.tab) {
            AppTab.HOME -> HomeScreen(state, route)
            AppTab.SEARCH -> SearchScreen(state, route)
            AppTab.SETTINGS -> SettingsScreen(state)
            else -> BrowseScreen(state, route, route.tab, null)
        }
        is Route.Folder -> BrowseScreen(state, route, null, route)
        is Route.Detail -> DetailScreen(state, route)
        is Route.Person -> PersonScreen(state, route)
        is Route.Player -> PlayerScreen(state, route)
    }
}

@Composable
private fun NavRail(current: AppTab, platform: com.adultaristocrat.cinema.Platform, onSelect: (AppTab) -> Unit) {
    // Панель не прокручивается: размер кнопок подбирается под высоту экрана
    BoxWithConstraints(
        Modifier
            .fillMaxHeight()
            .width(76.dp)
            .background(Color(0xF0000000))
            .border(1.dp, AA.BorderDark),
    ) {
        val count = AppTab.entries.size + 1 // + наушники
        val logo = 44.dp
        val free = maxHeight - logo - 28.dp
        val btn = (free / count - 4.dp).coerceIn(34.dp, 56.dp)
        Column(
            Modifier.fillMaxSize().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Logo(logo)
            HeadphonesButton(platform, btn)
            AppTab.entries.forEach { tab ->
                NavButton(tab, tab == current, btn) { onSelect(tab) }
            }
        }
    }
}

@Composable
private fun BottomNav(current: AppTab, platform: com.adultaristocrat.cinema.Platform, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF2141414))
            .border(1.dp, AA.Border, RoundedCornerShape(22.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeadphonesButton(platform, 36.dp)
        AppTab.entries.forEach { tab ->
            NavButton(tab, tab == current, 36.dp) { onSelect(tab) }
        }
    }
}

@Composable
private fun NavButton(tab: AppTab, selected: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val fv = rememberFocusVisual()
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(
                when {
                    selected -> AA.Red
                    fv.active -> AA.Selector
                    else -> Color.Transparent
                }
            )
            .border(if (fv.active) 2.dp else 0.dp, if (fv.active) AA.Text else Color.Transparent, shape)
            .clickable(interactionSource = fv.interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            tab.icon(), tab.title,
            tint = if (selected) AA.Text else if (fv.active) AA.RedHover else Color(0xCCFFFFFF),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Наушники: тёмная иконка без красной подсветки; зелёная, когда наушники подключены */
@Composable
private fun HeadphonesButton(platform: com.adultaristocrat.cinema.Platform, size: androidx.compose.ui.unit.Dp) {
    val fv = rememberFocusVisual()
    val shape = RoundedCornerShape(14.dp)
    val connected = platform.headphonesConnected
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(if (fv.active) Color(0xFF1C1C1C) else Color.Transparent)
            .border(if (fv.active) 2.dp else 0.dp, if (fv.active) AA.Text else Color.Transparent, shape)
            .clickable(interactionSource = fv.interaction, indication = null) { platform.onHeadphonesClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Headphones,
            if (connected) "Наушники подключены: ${platform.headphonesName ?: ""}" else "Подключить наушники",
            tint = when {
                connected -> AA.Green
                fv.active -> Color(0xFFBDBDBD)
                else -> Color(0xFF5C5C5C)
            },
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
