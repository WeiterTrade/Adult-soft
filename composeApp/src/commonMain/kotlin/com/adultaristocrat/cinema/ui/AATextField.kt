package com.adultaristocrat.cinema.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.delay

/**
 * Поле ввода. На телевизоре клавиатура НЕ открывается при наведении пультом —
 * только после нажатия «ОК» на поле. По «Готово» / «Назад» поле снова становится обычным пунктом.
 * На телефоне и ПК — обычное поле.
 */
@Composable
fun AATextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    shape: Shape = OutlinedTextFieldDefaults.shape,
) {
    val ui = LocalUi.current
    if (!ui.isTv) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier, singleLine = singleLine,
            label = label, placeholder = placeholder, leadingIcon = leadingIcon,
            visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions, colors = colors, shape = shape,
        )
        return
    }

    var editing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        if (editing) {
            delay(50)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    fun finish() {
        editing = false
        keyboard?.hide()
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // Пока не нажали ОК — поле «только для чтения»: фокус есть, клавиатуры нет
        readOnly = !editing,
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged { if (!it.isFocused && editing) editing = false }
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                when {
                    !editing && ok -> {
                        if (e.type == KeyEventType.KeyUp) editing = true
                        true
                    }
                    editing && e.key == Key.Back -> {
                        if (e.type == KeyEventType.KeyUp) finish()
                        true
                    }
                    // Стрелки вверх/вниз уводят с поля, даже когда идёт ввод
                    editing && (e.key == Key.DirectionDown || e.key == Key.DirectionUp) && e.type == KeyEventType.KeyDown -> {
                        finish()
                        false
                    }
                    else -> false
                }
            },
        singleLine = singleLine,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(
            onDone = { finish(); keyboardActions.onDone?.invoke(this) },
            onGo = { finish(); keyboardActions.onGo?.invoke(this) },
            onNext = { finish(); keyboardActions.onNext?.invoke(this) },
            onSearch = { finish(); keyboardActions.onSearch?.invoke(this) },
            onSend = { finish(); keyboardActions.onSend?.invoke(this) },
        ),
        colors = colors,
        shape = shape,
    )
}
