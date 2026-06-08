// feature/downloads/DownloadsScreen.kt
package com.luki.play.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download

@UnstableApi
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Mis descargas",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (items.isEmpty()) {
                Text(
                    "Aún no tienes contenido descargado.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    DownloadRow(item = item, onCancel = { viewModel.cancel(item.id) })
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(item.title, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stateLabel(item.state) + "  ·  ${item.percent}%",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { item.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = onCancel,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Cancelar") }
        }
    }
}

private fun stateLabel(state: Int): String = when (state) {
    Download.STATE_QUEUED      -> "En cola"
    Download.STATE_STOPPED     -> "Pausada"
    Download.STATE_DOWNLOADING -> "Descargando"
    Download.STATE_COMPLETED   -> "Completada"
    Download.STATE_FAILED      -> "Fallida"
    Download.STATE_REMOVING    -> "Eliminando"
    Download.STATE_RESTARTING  -> "Reiniciando"
    else                       -> "Desconocida"
}
