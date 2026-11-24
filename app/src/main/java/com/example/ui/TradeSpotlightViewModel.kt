package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.LiveQuote
import com.example.data.model.PickFormatters
import com.example.data.model.PicksResponse
import com.example.data.model.StockPick
import com.example.data.repository.DataSource
import com.example.data.repository.LiveQuoteDataSource
import com.example.data.repository.LiveQuoteRepository
import com.example.data.repository.PicksDataSource
import com.example.data.repository.PicksFetchResult
import com.example.data.repository.PicksRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CallFilter {
    ALL,
    BUY,
    SELL,
}

data class TradeSpotlightUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingQuotes: Boolean = false,
    val picksResponse: PicksResponse? = null,
    val filteredPicks: List<StockPick> = emptyList(),
    val liveQuotes: Map<String, LiveQuote> = emptyMap(),
    val allCount: Int = 0,
    val buyCount: Int = 0,
    val sellCount: Int = 0,
    val selectedFilter: CallFilter = CallFilter.ALL,
    val searchQuery: String = "",
    val dataSource: DataSource? = null,
    val appCheckedAt: String = "",
    val errorMessage: String? = null,
)

class TradeSpotlightViewModel(
    private val repository: PicksDataSource,
    private val liveQuoteRepository: LiveQuoteDataSource? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradeSpotlightUiState())
    val uiState: StateFlow<TradeSpotlightUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        loadPicks(isUserRefresh = false)
    }

    fun loadPicks(isUserRefresh: Boolean = false) {
        if (refreshJob?.isActive == true && !isUserRefresh) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isInitialLoading = state.picksResponse == null && !isUserRefresh,
                    isRefreshing = isUserRefresh || state.picksResponse != null,
                    errorMessage = null,
                )
            }

            if (liveQuoteRepository != null && _uiState.value.liveQuotes.isEmpty()) {
                val cached = liveQuoteRepository.loadCachedQuotes()
                if (cached.isNotEmpty()) {
                    _uiState.update { it.copy(liveQuotes = cached) }
                }
            }

            when (val result = repository.fetchPicks()) {
                is PicksFetchResult.Success -> {
                    val all = result.data.picks
                    val buyCount = all.count { it.call.equals("BUY", ignoreCase = true) }
                    val sellCount = all.count { it.call.equals("SELL", ignoreCase = true) }
                    val checkedTime = PickFormatters.formatCurrentTime()

                    _uiState.update { state ->
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingQuotes = liveQuoteRepository != null,
                            picksResponse = result.data,
                            allCount = all.size,
                            buyCount = buyCount,
                            sellCount = sellCount,
                            filteredPicks = applyFilter(all, state.selectedFilter, state.searchQuery),
                            dataSource = result.source,
                            appCheckedAt = checkedTime,
                            errorMessage = null,
                        )
                    }

                    if (liveQuoteRepository != null && all.isNotEmpty()) {
                        val updatedQuotes = liveQuoteRepository.fetchLiveQuotes(all.map { it.stock })
                        _uiState.update { state ->
                            state.copy(
                                liveQuotes = updatedQuotes,
                                isLoadingQuotes = false,
                                appCheckedAt = PickFormatters.formatCurrentTime(),
                            )
                        }
                    }
                }

                is PicksFetchResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingQuotes = false,
                            appCheckedAt = PickFormatters.formatCurrentTime(),
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun onFilterSelected(filter: CallFilter) {
        _uiState.update { state ->
            val all = state.picksResponse?.picks.orEmpty()
            state.copy(
                selectedFilter = filter,
                filteredPicks = applyFilter(all, filter, state.searchQuery),
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val all = state.picksResponse?.picks.orEmpty()
            state.copy(
                searchQuery = query,
                filteredPicks = applyFilter(all, state.selectedFilter, query),
            )
        }
    }

    private fun applyFilter(
        picks: List<StockPick>,
        filter: CallFilter,
        query: String,
    ): List<StockPick> {
        val trimmed = query.trim().lowercase()
        return picks.filter { pick ->
            val matchesCall = when (filter) {
                CallFilter.ALL -> true
                CallFilter.BUY -> pick.call.equals("BUY", ignoreCase = true)
                CallFilter.SELL -> pick.call.equals("SELL", ignoreCase = true)
            }
            val matchesQuery = trimmed.isEmpty() ||
                "${pick.stock} ${pick.broker.orEmpty()}".lowercase().contains(trimmed)
            matchesCall && matchesQuery
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TradeSpotlightViewModel::class.java)) {
                return TradeSpotlightViewModel(
                    repository = PicksRepository(application),
                    liveQuoteRepository = LiveQuoteRepository(application),
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
