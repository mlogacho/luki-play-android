// feature/downloads/DownloadsViewModel.kt
package com.luki.play.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.luki.play.data.downloads.DownloadsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DownloadItem(
    val id: String,
    val title: String,
    val state: Int,
    val percent: Int,
)

@UnstableApi
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadsRepository,
) : ViewModel() {

    val items: StateFlow<List<DownloadItem>> = repository.observe()
        .let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { list -> emit(list.map { it.toItem() }) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    fun cancel(id: String) { repository.cancel(id) }

    private fun Download.toItem(): DownloadItem = DownloadItem(
        id      = request.id,
        title   = request.data.toString(Charsets.UTF_8).ifBlank { request.id },
        state   = state,
        percent = percentDownloaded.toInt().coerceIn(0, 100),
    )

    companion object { private const val STOP_TIMEOUT_MS = 5_000L }
}
