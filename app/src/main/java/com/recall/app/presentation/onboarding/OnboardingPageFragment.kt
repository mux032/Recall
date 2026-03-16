package com.recall.app.presentation.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.recall.app.databinding.FragmentOnboardingPageBinding

/**
 * Fragment representing a single onboarding page.
 */
class OnboardingPageFragment : Fragment() {
    
    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_ILLUSTRATION_RES_ID = "illustration_res_id"
        
        fun newInstance(
            title: String,
            description: String,
            illustrationResId: Int
        ): OnboardingPageFragment {
            return OnboardingPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_DESCRIPTION, description)
                    putInt(ARG_ILLUSTRATION_RES_ID, illustrationResId)
                }
            }
        }
    }
    
    private var _binding: FragmentOnboardingPageBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingPageBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val title = arguments?.getString(ARG_TITLE) ?: ""
        val description = arguments?.getString(ARG_DESCRIPTION) ?: ""
        val illustrationResId = arguments?.getInt(ARG_ILLUSTRATION_RES_ID) ?: 0
        
        binding.titleText.text = title
        binding.descriptionText.text = description
        binding.illustration.setImageResource(illustrationResId)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
