package com.recall.app.presentation.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.recall.app.presentation.MainActivity
import com.recall.app.presentation.onboarding.OnboardingActivity
import com.recall.app.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Splash screen activity shown on app launch.
 * Displays branding while initializing the app.
 */
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {
    
    private val splashTimeOut: Long = 2000 // 2 seconds
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, splashTimeOut)
    }
    
    private fun navigateToNextScreen() {
        // For now, always navigate to onboarding
        // Later we'll check preferences to skip if already completed
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }
}
