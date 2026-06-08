// feature/search/SearchViewModel.kt
package com.luki.play.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luki.play.data.catalog.ChannelsRepository
import com.luki.play.data.catalog.domain.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Channel> = emptyList(),
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ChannelsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<Channel>> = _query
        .debounce(DEBOUNCE_MS)
        .flatMapLatest { q ->
            if (q.length < 2) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.searchChannels(q)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    fun onQueryChange(q: String) { _query.value = q }

    companion object {
        private const val DEBOUNCE_MS = 250L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
