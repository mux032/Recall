package com.recall.app.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.domain.model.Screenshot
import com.recall.app.domain.usecase.GetAllScreenshotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    getAllScreenshotsUseCase: GetAllScreenshotsUseCase
) : ViewModel() {

    val screenshots: StateFlow<List<Screenshot>> = getAllScreenshotsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
