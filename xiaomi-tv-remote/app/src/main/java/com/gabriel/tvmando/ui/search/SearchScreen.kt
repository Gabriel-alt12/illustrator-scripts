package com.gabriel.tvmando.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.gabriel.tvmando.domain.SearchTarget
import com.gabriel.tvmando.domain.TvApp
import com.gabriel.tvmando.ui.SearchUiState
import com.gabriel.tvmando.ui.components.Tap
import com.gabriel.tvmando.ui.theme.Chalk
import com.gabriel.tvmando.ui.theme.ChalkFaint
import com.gabriel.tvmando.ui.theme.ChalkMuted
import com.gabriel.tvmando.ui.theme.Ember
import com.gabriel.tvmando.ui.theme.EmberSunk
import com.gabriel.tvmando.ui.theme.Hairline
import com.gabriel.tvmando.ui.theme.InkRaised

/**
 * Escribir en la TV desde el teclado del movil.
 *
 * Buscar en una TV con la cruceta es una tortura, asi que esta pantalla es medio
 * teclado remoto: se elige donde escribir, se escribe aqui y la app manda
 * `input text` seguido de ENTER. Por dentro es una escena efimera, para reutilizar
 * los retardos del motor de la pestana de Escenas.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    apps: List<TvApp>,
    enabled: Boolean,
    onTargetChange: (SearchTarget) -> Unit,
    onSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
    haptics: (Tap) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun launch(text: String) {
        if (text.isBlank()) return
        haptics(Tap.Confirm)
        keyboard?.hide()
        onSearch(text)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Donde escribir",
            style = MaterialTheme.typography.labelMedium,
            color = ChalkMuted,
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val targets = remember(apps) {
                buildList {
                    add(SearchTarget.GoogleTv)
                    add(SearchTarget.Focused)
                    apps.forEach { add(SearchTarget.App(it.packageName, it.displayName)) }
                }
            }
            targets.forEach { target ->
                Chip(
                    label = target.label,
                    selected = target == state.target,
                    onClick = {
                        haptics(Tap.Press)
                        onTargetChange(target)
                    },
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.replace("\n", "") },
                label = { Text("Que buscar") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = ChalkMuted)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { launch(query) }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            SendButton(enabled = enabled && query.isNotBlank(), onClick = { launch(query) })
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (state.isTyping) {
                "Enviando a la TV..."
            } else {
                "Se envia el texto y luego ENTER."
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.isTyping) Ember else ChalkFaint,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = ChalkMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Ultimas busquedas",
                style = MaterialTheme.typography.labelMedium,
                color = ChalkMuted,
                modifier = Modifier.weight(1f),
            )
            if (state.history.isNotEmpty()) {
                TextButton(
                    onClick = {
                        haptics(Tap.Press)
                        onClearHistory()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("Borrar", style = MaterialTheme.typography.labelLarge, color = ChalkMuted)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.history.isEmpty()) {
            Text(
                text = "Todavia no has buscado nada.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkFaint,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items = state.history, key = { it }) { previous ->
                    HistoryRow(
                        text = previous,
                        onClick = {
                            query = previous
                            launch(previous)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) EmberSunk else InkRaised)
            .border(
                1.dp,
                if (selected) Ember else Hairline,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Ember else ChalkMuted,
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) EmberSunk else InkRaised)
            .border(1.dp, if (enabled) Ember else Hairline, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Send,
            contentDescription = "Enviar a la TV",
            tint = if (enabled) Ember else ChalkFaint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun HistoryRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(InkRaised)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Chalk,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.Send,
            contentDescription = null,
            tint = ChalkFaint,
            modifier = Modifier.size(16.dp),
        )
    }
}
