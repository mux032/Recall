package com.recall.app.presentation.permission

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.recall.app.R
import com.recall.app.data.service.ScreenshotDetectionService
import com.recall.app.databinding.ActivityPermissionBinding
import com.recall.app.presentation.MainActivity
import com.recall.app.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity to request storage permissions from the user.
 */
@AndroidEntryPoint
class PermissionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPermissionBinding
    
    @Inject
    lateinit var screenshotDetectionService: ScreenshotDetectionService
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        
        if (allGranted) {
            onPermissionsGranted()
        } else {
            showPermissionDenied()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.grantButton.setOnClickListener {
            requestPermissions()
        }
        
        // If permissions already granted, navigate to home
        if (PermissionUtils.areAllPermissionsGranted(this)) {
            navigateToHome()
        }
    }
    
    private fun requestPermissions() {
        val permissions = PermissionUtils.getRequiredPermissions()
        
        if (permissions.isEmpty()) {
            // No permissions needed (Android 10+)
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(permissions)
        }
    }
    
    private fun onPermissionsGranted() {
        // Start screenshot monitoring
        screenshotDetectionService.startMonitoring()
        navigateToHome()
    }
    
    private fun showPermissionDenied() {
        // Show explanation that permissions are required
        binding.descriptionText.text = "Permissions are required to access your screenshots. Without them, the app cannot function."
        binding.grantButton.text = "Try Again"
    }
    
    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Don't stop monitoring here - it should continue in background
    }
}
