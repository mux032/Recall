package com.recall.app.presentation.timeline

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.data.repository.ScreenshotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for Timeline screen.
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val screenshotRepository: ScreenshotRepository
) : ViewModel() {
    
    private val _uiState = MutableLiveData(TimelineUiState())
    val uiState: LiveData<TimelineUiState> = _uiState
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    
    init {
        loadTimeline()
    }
    
    private fun loadTimeline() {
        viewModelScope.launch {
            _uiState.postValue(_uiState.value?.copy(isLoading = true))

            // Get ALL screenshots (not just indexed ones)
            screenshotRepository.getRecentScreenshots(200)
                .catch { e ->
                    _uiState.postValue(
                        _uiState.value?.copy(
                            error = e.message,
                            isLoading = false
                        )
                    )
                }
                .collect { screenshots ->
                    val groupedScreenshots = groupScreenshotsByDate(screenshots)
                    _uiState.postValue(
                        _uiState.value?.copy(
                            timelineSections = groupedScreenshots,
                            isLoading = false,
                            isEmpty = screenshots.isEmpty()
                        )
                    )
                }
        }
    }
    
    private fun groupScreenshotsByDate(screenshots: List<ScreenshotEntity>): List<TimelineSection> {
        val grouped = mutableMapOf<String, List<ScreenshotEntity>>()
        
        screenshots.forEach { screenshot ->
            val dateKey = dateFormatter.format(Date(screenshot.timestamp))
            val existing = grouped[dateKey] ?: emptyList()
            grouped[dateKey] = existing + screenshot
        }
        
        return grouped.mapKeys { (date, _) ->
            getRelativeDateLabel(date)
        }.map { (label, screenshots) ->
            TimelineSection(
                dateLabel = label,
                screenshots = screenshots.map { entity ->
                    TimelineScreenshot(
                        id = entity.id,
                        filePath = entity.filePath,
                        timestamp = entity.timestamp,
                        formattedTime = timeFormatter.format(Date(entity.timestamp)),
                        summary = entity.summary,
                        category = entity.category
                    )
                }.sortedByDescending { it.timestamp }
            )
        }.sortedByDescending { section ->
            // Sort sections by most recent date
            section.screenshots.maxOfOrNull { it.timestamp } ?: 0L
        }
    }
    
    private fun getRelativeDateLabel(date: String): String {
        val calendar = Calendar.getInstance()
        val today = dateFormatter.format(calendar.time)
        
        if (date == today) {
            return "Today"
        }
        
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = dateFormatter.format(calendar.time)
        
        if (date == yesterday) {
            return "Yesterday"
        }
        
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val lastWeek = calendar.time
        
        return try {
            val dateObj = dateFormatter.parse(date)
            if (dateObj != null && dateObj.after(lastWeek)) {
                "This Week"
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, -23)
                val lastMonth = calendar.time
                if (dateObj.after(lastMonth)) {
                    "Last Month"
                } else {
                    "Older"
                }
            }
        } catch (e: Exception) {
            date
        }
    }
    
    fun refresh() {
        loadTimeline()
    }
}

data class TimelineUiState(
    val isLoading: Boolean = false,
    val isEmpty: Boolean = true,
    val error: String? = null,
    val timelineSections: List<TimelineSection> = emptyList()
)

data class TimelineSection(
    val dateLabel: String,
    val screenshots: List<TimelineScreenshot>
)

data class TimelineScreenshot(
    val id: Long,
    val filePath: String,
    val timestamp: Long,
    val formattedTime: String,
    val summary: String?,
    val category: String?
)
