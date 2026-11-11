package com.adultaristocrat.cinema.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adultaristocrat.cinema.AppConfig
import com.adultaristocrat.cinema.AppState
import com.adultaristocrat.cinema.ui.AA
import com.adultaristocrat.cinema.ui.AAButton
import com.adultaristocrat.cinema.ui.Logo
import com.adultaristocrat.cinema.ui.brandStyle
import kotlinx.coroutines.launch

@Composable
fun aaFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AA.Red,
    unfocusedBorderColor = AA.Border,
    focusedLabelColor = AA.Text,
    unfocusedLabelColor = AA.TextDim,
    cursorColor = AA.Red,
    focusedContainerColor = AA.Selector,
    unfocusedContainerColor = Color(0xFF0D0000),
    focusedTextColor = AA.Text,
    unfocusedTextColor = AA.Text,
)

@Composable
fun LoginScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(state.lastServer) }
    var user by remember { mutableStateOf(state.lastUser) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun doLogin() {
        if (busy) return
        if (server.isBlank() || user.isBlank()) {
            error = if (AppConfig.isServerLocked) "Введите логин" else "Укажите адрес сервера и логин"
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                runCatching { state.api.publicInfo(server) }.onFailure {
                    throw IllegalStateException("Нет связи с кинотеатром. Проверьте интернет и попробуйте ещё раз.")
                }
                state.login(server, user, password)
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                error = if (msg.contains("401")) "Неверный логин или пароль" else msg
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF140000), AA.Black))
        ).imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xD1000000))
                .border(1.dp, AA.Border, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Logo(110.dp)
            Text(AppConfig.APP_NAME.uppercase(), style = brandStyle(26), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            if (!AppConfig.isServerLocked) OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Адрес сервера") },
                placeholder = { Text("http://192.168.1.10:8096") },
                singleLine = true,
                colors = aaFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Логин") },
                singleLine = true,
                colors = aaFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = aaFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { doLogin() }),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Color(0xFFFF6B6B), textAlign = TextAlign.Center) }
            if (busy) {
                CircularProgressIndicator(color = AA.Red)
            } else {
                AAButton("Войти", Icons.Rounded.Login, { doLogin() }, Modifier.fillMaxWidth())
            }
        }
    }
}
