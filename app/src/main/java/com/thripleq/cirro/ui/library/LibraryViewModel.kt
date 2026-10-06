package com.thripleq.cirro.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.repo.Chart
import com.thripleq.cirro.core.repo.ChartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Error : LibraryUiState
    data class Charts(val charts: List<Chart>) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: ChartRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            val charts = repository.charts()
            _uiState.value = if (charts.isEmpty()) {
                LibraryUiState.Error
            } else {
                LibraryUiState.Charts(charts)
            }
        }
    }
}