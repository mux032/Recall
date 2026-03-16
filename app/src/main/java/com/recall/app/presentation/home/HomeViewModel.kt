package com.recall.app.presentation.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.data.repository.ScreenshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Home screen (categories only).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val screenshotRepository: ScreenshotRepository
) : ViewModel() {

    private val _uiState = MutableLiveData(HomeUiState())
    val uiState: LiveData<HomeUiState> = _uiState

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            screenshotRepository.getAllCategories()
                .catch { /* Handle error silently */ }
                .collect { categories ->
                    _uiState.postValue(_uiState.value?.copy(categories = categories))
                }
        }
    }
}

data class HomeUiState(
    val categories: List<String> = emptyList()
)
