package com.recall.app.presentation.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.recall.app.databinding.FragmentSearchBinding
import com.recall.app.presentation.home.ScreenshotGridAdapter
import dagger.hilt.android.AndroidEntryPoint

/**
 * Search screen fragment for searching screenshots.
 */
@AndroidEntryPoint
class SearchFragment : Fragment() {
    
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SearchViewModel by viewModels()
    
    private var resultsAdapter: ScreenshotGridAdapter? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupSearchBar()
        setupRecyclerViews()
        observeViewModel()
    }
    
    private fun setupSearchBar() {
        binding.searchEditText.addTextChangedListener { text ->
            viewModel.search(text.toString())
            
            // Show/hide clear button
            binding.clearSearchButton.visibility = if (text.isNullOrBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
        
        binding.clearSearchButton.setOnClickListener {
            binding.searchEditText.text?.clear()
            viewModel.clearSearch()
        }
    }
    
    private fun setupRecyclerViews() {
        // Recent searches
        binding.recentSearchesRecycler.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        
        // Suggested searches
        binding.suggestedSearchesRecycler.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        
        // Search results
        resultsAdapter = ScreenshotGridAdapter(emptyList()) { screenshot ->
            // Navigate to detail (to be implemented)
        }
        
        binding.searchResultsRecycler.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = resultsAdapter
        }
    }
    
    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            updateUI(state)
        }
        
        viewModel.recentSearches.observe(viewLifecycleOwner) { searches ->
            // Update recent searches UI
        }
    }
    
    private fun updateUI(state: SearchUiState) {
        // Show/hide loading
        binding.loadingShimmer.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        
        // Show/hide empty state
        binding.emptyState.visibility = if (state.isEmpty && !state.isLoading) View.VISIBLE else View.GONE
        
        // Show/hide results
        binding.resultsTitle.visibility = if (state.searchResults.isNotEmpty()) View.VISIBLE else View.GONE
        binding.searchResultsRecycler.visibility = if (state.searchResults.isNotEmpty()) View.VISIBLE else View.GONE
        
        // Update results
        if (state.searchResults.isNotEmpty()) {
            // Would need to convert SearchResult to ScreenshotItem
            // For now, just show the count
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
