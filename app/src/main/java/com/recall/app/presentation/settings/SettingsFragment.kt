package com.recall.app.presentation.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.recall.app.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Settings screen fragment for app configuration.
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        viewModel.onPermissionsResult(allGranted)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeViewModel()
        checkPermissions()
    }

    private fun setupUI() {
        binding.btnManageModels.setOnClickListener { }
        binding.btnReindex.setOnClickListener { 
            viewModel.reindexScreenshots()
            binding.btnReindex.text = "Scanning..."
        }
        binding.btnClearData.setOnClickListener { viewModel.clearAllData() }
        binding.switchIndexing.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIndexingEnabled(isChecked)
        }
    }

    private fun checkPermissions() {
        val permissions = getRequiredPermissions()
        if (permissions.isNotEmpty()) {
            val needsPermission = permissions.any {
                ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }

            if (needsPermission) {
                binding.btnReindex.text = "Grant Permissions & Scan"
                binding.btnReindex.setOnClickListener {
                    permissionLauncher.launch(permissions)
                }
            }
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf() // Android 10+ doesn't need permission
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            updateUI(state)
        }
    }

    private fun updateUI(state: SettingsUiState) {
        binding.textModelStatus.text = state.modelStatus
        binding.textModelSize.text = state.modelSize
        binding.textIndexSize.text = state.indexSize
        binding.textScreenshotCount.text = "${state.screenshotCount} screenshots indexed"
        binding.textIndexingStatus.text = if (state.isIndexingEnabled) "Active" else "Paused"
        binding.switchIndexing.isChecked = state.isIndexingEnabled
        binding.textVersion.text = state.versionName

        if (state.isProcessing) {
            binding.progressIndexing.visibility = View.VISIBLE
            binding.textIndexingProgress.text = "Scanning..."
        } else {
            binding.progressIndexing.visibility = View.GONE
            binding.textIndexingProgress.text = "${state.processedCount}/${state.totalCount} processed"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
