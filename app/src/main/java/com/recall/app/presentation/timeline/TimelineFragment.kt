package com.recall.app.presentation.timeline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.recall.app.databinding.FragmentTimelineBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Timeline screen fragment for browsing screenshots chronologically.
 */
@AndroidEntryPoint
class TimelineFragment : Fragment() {
    
    private var _binding: FragmentTimelineBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: TimelineViewModel by viewModels()
    
    private var adapter: TimelineAdapter? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimelineBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        adapter = TimelineAdapter { screenshot ->
            // Navigate to screenshot detail (to be implemented)
        }
        
        binding.timelineRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.timelineRecycler.adapter = adapter
    }
    
    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            updateUI(state)
        }
    }
    
    private fun updateUI(state: TimelineUiState) {
        // Show/hide loading
        binding.loadingShimmer.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        
        // Show/hide empty state
        binding.emptyState.visibility = if (state.isEmpty && !state.isLoading) View.VISIBLE else View.GONE
        
        // Show/hide timeline
        binding.timelineRecycler.visibility = if (state.timelineSections.isNotEmpty() && !state.isLoading) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        // Update adapter
        if (state.timelineSections.isNotEmpty()) {
            adapter?.updateSections(state.timelineSections)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
