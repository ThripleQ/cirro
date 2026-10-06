package com.thripleq.cirro.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thripleq.cirro.core.model.Chart
import com.thripleq.cirro.core.repo.ChartRepository
import com.thripleq.cirro.core.util.RefreshGate
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

    /** 见 [onEnterVisible]，与探索页同源：反复进页面不该反复打网络。 */
    private val enterGate = RefreshGate()

    init {
        // 首次取数自己发起，先把门关上 —— 页面组合时那次 onEnterVisible() 会被冷却吃掉。
        enterGate.mark()
        load()
    }

    /**
     * 页面重新可见时调用。冷却期内零请求；冷却之外**静默**重取（不退回骨架屏）。
     * 榜单的封面/名称会随官方调整而变，只靠 init 那一次就永远是进入时那份。
     */
    fun onEnterVisible() {
        if (!enterGate.allow()) return
        enterGate.mark()
        reload(quiet = true)
    }

    /** 手动刷新：显示加载态、无条件重取。 */
    fun load() = reload(quiet = false)

    private fun reload(quiet: Boolean) {
        val silent = quiet && _uiState.value is LibraryUiState.Charts
        viewModelScope.launch {
            if (!silent) _uiState.value = LibraryUiState.Loading
            val charts = repository.charts()
            _uiState.value = when {
                charts.isNotEmpty() -> LibraryUiState.Charts(charts)
                // 静默刷新拉空（多半是网络抖动）时保留页面上那份，不翻成错误页。
                silent -> _uiState.value
                else -> LibraryUiState.Error
            }
        }
    }
}
