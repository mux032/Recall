package com.recall.app.presentation.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.recall.app.R
import com.recall.app.databinding.ActivityOnboardingBinding
import com.recall.app.presentation.permission.PermissionActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Onboarding activity introducing the app's features.
 */
@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityOnboardingBinding
    
    private val onboardingPages = listOf(
        OnboardingPage(
            title = "Find Screenshots Instantly",
            description = "Search your screenshots using natural language.",
            illustrationResId = R.drawable.ic_onboarding_search
        ),
        OnboardingPage(
            title = "Your Data Stays Private",
            description = "All AI runs locally on your device. Screenshots never leave your phone.",
            illustrationResId = R.drawable.ic_onboarding_privacy
        ),
        OnboardingPage(
            title = "Automatic Screenshot Indexing",
            description = "We'll automatically organize screenshots using AI.",
            illustrationResId = R.drawable.ic_onboarding_auto
        )
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewPager()
        setupButtons()
    }
    
    private fun setupViewPager() {
        val adapter = OnboardingAdapter(this)
        binding.viewPager.adapter = adapter
        
        binding.viewPager.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updateButtons(position)
                }
            }
        )
    }
    
    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < onboardingPages.size - 1) {
                binding.viewPager.currentItem = currentItem + 1
            }
        }
        
        binding.btnSkip.setOnClickListener {
            navigateToHome()
        }
        
        binding.btnGetStarted.setOnClickListener {
            navigateToHome()
        }
    }
    
    private fun updateButtons(position: Int) {
        val isLastPage = position == onboardingPages.size - 1
        
        binding.btnNext.visibility = if (isLastPage) View.GONE else View.VISIBLE
        binding.btnGetStarted.visibility = if (isLastPage) View.VISIBLE else View.GONE
        binding.btnSkip.visibility = if (isLastPage) View.GONE else View.VISIBLE
    }
    
    private fun navigateToHome() {
        // Save onboarding completion to preferences
        // TODO: Implement preferences save
        val intent = Intent(this, PermissionActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    data class OnboardingPage(
        val title: String,
        val description: String,
        val illustrationResId: Int
    )
    
    inner class OnboardingAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = onboardingPages.size
        
        override fun createFragment(position: Int): Fragment {
            val page = onboardingPages[position]
            return OnboardingPageFragment.newInstance(
                page.title,
                page.description,
                page.illustrationResId
            )
        }
    }
}
