package com.recall.app.presentation.categories

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
 * ViewModel for Categories screen.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val screenshotRepository: ScreenshotRepository
) : ViewModel() {
    
    private val _uiState = MutableLiveData(CategoriesUiState())
    val uiState: LiveData<CategoriesUiState> = _uiState
    
    // All available categories
    private val allCategories = listOf(
        CategoryItem("Shopping", "shopping", 0),
        CategoryItem("Travel", "travel", 0),
        CategoryItem("Code", "code", 0),
        CategoryItem("Food", "food", 0),
        CategoryItem("Finance", "finance", 0),
        CategoryItem("Social", "social", 0),
        CategoryItem("Work", "work", 0),
        CategoryItem("Recipes", "recipes", 0),
        CategoryItem("Messages", "messages", 0),
        CategoryItem("Web", "web", 0),
        CategoryItem("Email", "email", 0),
        CategoryItem("Other", "other", 0)
    )
    
    init {
        loadCategories()
    }
    
    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.postValue(_uiState.value?.copy(isLoading = true))
            
            // Get all categories from repository
            screenshotRepository.getAllCategories()
                .catch { e ->
                    _uiState.postValue(
                        _uiState.value?.copy(
                            error = e.message,
                            isLoading = false
                        )
                    )
                }
                .collect { dbCategories ->
                    // Count screenshots per category
                    val categoryCounts = mutableMapOf<String, Int>()
                    
                    dbCategories.forEach { category ->
                        val count = categoryCounts[category] ?: 0
                        categoryCounts[category] = count + 1
                    }
                    
                    // Update category items with counts
                    val updatedCategories = allCategories.map { category ->
                        val count = categoryCounts[category.name.lowercase()] ?: 0
                        category.copy(count = count)
                    }.filter { it.count > 0 } // Only show categories with screenshots
                    .sortedByDescending { it.count }
                    
                    _uiState.postValue(
                        _uiState.value?.copy(
                            categories = updatedCategories,
                            isLoading = false,
                            isEmpty = updatedCategories.isEmpty()
                        )
                    )
                }
        }
    }
    
    fun getScreenshotsByCategory(categoryName: String) {
        // This would navigate to category detail or filter the home screen
        // For now, just update the selected category
        _uiState.postValue(
            _uiState.value?.copy(selectedCategory = categoryName)
        )
    }
    
    fun clearSelection() {
        _uiState.postValue(_uiState.value?.copy(selectedCategory = null))
    }
    
    fun refresh() {
        loadCategories()
    }
}

data class CategoriesUiState(
    val isLoading: Boolean = false,
    val isEmpty: Boolean = true,
    val error: String? = null,
    val categories: List<CategoryItem> = emptyList(),
    val selectedCategory: String? = null
)

data class CategoryItem(
    val displayName: String,
    val name: String,
    val count: Int
)
