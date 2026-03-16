package com.recall.app.presentation.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.recall.app.databinding.FragmentHomeBinding
import com.recall.app.presentation.home.ScreenshotGridAdapter.ScreenshotItem
import com.recall.app.presentation.search.SearchViewModel
import com.recall.app.presentation.timeline.TimelineAdapter
import com.recall.app.presentation.timeline.TimelineViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Home screen fragment with integrated search, timeline and categories.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val timelineViewModel: TimelineViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    
    private var timelineAdapter: TimelineAdapter? = null
    private var categoriesAdapter: CategoryChipAdapter? = null
    private var searchResultsAdapter: ScreenshotGridAdapter? = null
    private var suggestedSearchesAdapter: SuggestedSearchAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupSearchBar()
        observeViewModels()
    }

    private fun setupRecyclerViews() {
        // Setup categories
        categoriesAdapter = CategoryChipAdapter { category ->
            android.util.Log.d("HomeFragment", "Category clicked: $category")
        }
        binding.categoriesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoriesAdapter
        }

        // Setup timeline
        timelineAdapter = TimelineAdapter { screenshot ->
            navigateToDetail(screenshot.id)
        }
        binding.timelineRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.timelineAdapter
        }

        // Setup search results
        searchResultsAdapter = ScreenshotGridAdapter(emptyList()) { screenshot ->
            navigateToDetail(screenshot.id)
        }
        binding.searchResultsRecycler.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@HomeFragment.searchResultsAdapter
        }

        // Setup suggested searches - load from ViewModel
        searchViewModel.suggestedSearches.let { suggestions ->
            suggestedSearchesAdapter?.updateSearches(suggestions)
        }
    }

    private fun setupSearchBar() {
        // Show suggestions when search focused
        binding.searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.searchEditText.text.isNullOrBlank()) {
                binding.suggestionsContainer.visibility = View.VISIBLE
            }
        }

        // Search on text change with debounce
        binding.searchEditText.addTextChangedListener { text ->
            val query = text.toString()
            
            // Show/hide clear button
            binding.clearSearchButton.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
            
            // Show/hide suggestions
            if (query.isBlank()) {
                binding.suggestionsContainer.visibility = View.VISIBLE
                binding.homeContent.visibility = View.VISIBLE
                binding.searchResultsContainer.visibility = View.GONE
                binding.emptyState.visibility = View.GONE
            } else {
                binding.suggestionsContainer.visibility = View.GONE
                binding.homeContent.visibility = View.GONE
                binding.searchResultsContainer.visibility = View.VISIBLE
                // Search after debounce
                searchViewModel.search(query)
            }
        }

        // Clear search
        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.text?.clear()
            binding.homeContent.visibility = View.VISIBLE
            binding.searchResultsContainer.visibility = View.GONE
            binding.suggestionsContainer.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            searchViewModel.clearSearch()
        }
    }

    private fun observeViewModels() {
        // Observe home view model (categories)
        homeViewModel.uiState.observe(viewLifecycleOwner) { state ->
            categoriesAdapter?.updateCategories(state.categories)
        }

        // Observe timeline view model
        timelineViewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.emptyState.visibility = if (state.isEmpty && binding.searchEditText.text.isNullOrBlank()) View.VISIBLE else View.GONE
            
            if (state.timelineSections.isNotEmpty()) {
                timelineAdapter?.updateSections(state.timelineSections)
            }
        }

        // Observe search results
        searchViewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (state.searchResults.isNotEmpty()) {
                searchResultsAdapter?.updateScreenshots(
                    state.searchResults.map { result ->
                        ScreenshotItem(
                            id = result.screenshotId,
                            filePath = result.filePath,
                            summary = result.summary,
                            timestamp = result.timestamp
                        )
                    }
                )
                binding.emptyState.visibility = View.GONE
                binding.searchResultsContainer.visibility = View.VISIBLE
            } else {
                // No results - show empty state only if searching
                if (!binding.searchEditText.text.isNullOrBlank()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.searchResultsContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun navigateToDetail(screenshotId: Long) {
        val bundle = android.os.Bundle().apply {
            putLong("screenshotId", screenshotId)
        }
        val navController = Navigation.findNavController(requireView())
        navController.navigate(com.recall.app.R.id.screenshot_detail_fragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
