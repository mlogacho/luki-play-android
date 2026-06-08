// feature/search/SearchScreen.kt
package com.luki.play.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luki.play.data.catalog.domain.Channel

@Composable
fun SearchScreen(
    onChannelClick: (Channel) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar canales") },
                singleLine = true,
            )
            if (query.length < 2) {
                Text(
                    "Escribe al menos 2 letras",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else if (results.isEmpty()) {
                Text(
                    "Sin resultados",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { it.id }) { ch ->
                        SearchResultRow(channel = ch, onClick = { onChannelClick(ch) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(channel: Channel, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(channel.name, color = MaterialTheme.colorScheme.onSurface)
            Text(
                channel.category,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
