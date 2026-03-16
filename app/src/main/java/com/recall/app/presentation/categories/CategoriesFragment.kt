package com.recall.app.presentation.categories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.recall.app.databinding.FragmentCategoriesBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Categories screen fragment for browsing screenshots by category.
 */
@AndroidEntryPoint
class CategoriesFragment : Fragment() {
    
    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CategoriesViewModel by viewModels()
    
    private var adapter: CategoriesAdapter? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        adapter = CategoriesAdapter { category ->
            viewModel.getScreenshotsByCategory(category.name)
            // Navigate to category detail or filter (to be implemented)
        }
        
        binding.categoriesRecycler.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.categoriesRecycler.adapter = adapter
    }
    
    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            updateUI(state)
        }
    }
    
    private fun updateUI(state: CategoriesUiState) {
        // Show/hide loading
        binding.loadingShimmer.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        
        // Show/hide empty state
        binding.emptyState.visibility = if (state.isEmpty && !state.isLoading) View.VISIBLE else View.GONE
        
        // Show/hide categories
        binding.categoriesRecycler.visibility = if (state.categories.isNotEmpty() && !state.isLoading) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        // Update adapter
        if (state.categories.isNotEmpty()) {
            adapter?.updateCategories(state.categories)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
