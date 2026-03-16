package com.recall.app.presentation.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.recall.app.R
import com.recall.app.data.local.entity.ScreenshotEntity
import com.recall.app.databinding.FragmentScreenshotDetailBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScreenshotDetailFragment : Fragment() {

    private var _binding: FragmentScreenshotDetailBinding? = null
    private val binding get() = _binding!!
    
    private val args: ScreenshotDetailFragmentArgs by navArgs()
    
    private var screenshot: ScreenshotEntity? = null
    
    // Get DAO directly from database for now
    private val screenshotDao by lazy {
        androidx.room.Room.databaseBuilder(
            requireContext().applicationContext,
            com.recall.app.data.local.database.RecallDatabase::class.java,
            "recall_database"
        ).build().screenshotDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreenshotDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val screenshotId = args.screenshotId
        loadScreenshot(screenshotId)
        setupActions()
    }

    private fun loadScreenshot(screenshotId: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            screenshot = withContext(Dispatchers.IO) {
                screenshotDao.getScreenshotById(screenshotId)
            }
            
            android.util.Log.d("ScreenshotDetail", "Loaded screenshot from DB: $screenshotId")
            android.util.Log.d("ScreenshotDetail", "ocrText from DB: ${screenshot?.ocrText?.take(200)}")
            android.util.Log.d("ScreenshotDetail", "ocrText is null: ${screenshot?.ocrText == null}")
            android.util.Log.d("ScreenshotDetail", "ocrText is blank: ${screenshot?.ocrText.isNullOrBlank()}")
            android.util.Log.d("ScreenshotDetail", "ocrText length: ${screenshot?.ocrText?.length}")
            
            screenshot?.let { displayScreenshot(it) }
        }
    }

    private fun displayScreenshot(screenshot: ScreenshotEntity) {
        android.util.Log.d("ScreenshotDetail", "Displaying screenshot: ${screenshot.id}")
        android.util.Log.d("ScreenshotDetail", "OCR Text length: ${screenshot.ocrText?.length ?: 0}")
        android.util.Log.d("ScreenshotDetail", "OCR Text: ${screenshot.ocrText?.take(100)}")
        
        // Load image (fit to screen, not cropped)
        if (screenshot.filePath.startsWith("content://")) {
            Glide.with(requireContext())
                .load(Uri.parse(screenshot.filePath))
                .fitCenter()
                .into(binding.screenshotImage)
        } else {
            Glide.with(requireContext())
                .load(File(screenshot.filePath))
                .fitCenter()
                .into(binding.screenshotImage)
        }

        // Display file name
        val fileName = screenshot.filePath.substringAfterLast("/")
        binding.textFileName.text = fileName

        // Display summary (AI caption - will be improved with Vision model)
        binding.textSummary.text = screenshot.summary ?: "AI caption will appear here after vision model processing"

        // Display OCR text
        if (screenshot.ocrText.isNullOrBlank()) {
            binding.textOcr.text = "No text extracted yet.\n\nThis screenshot hasn't been processed by OCR yet.\nProcessing status: ${screenshot.processingStatus.name}"
            binding.textOcr.setTextColor(android.graphics.Color.parseColor("#999999"))
        } else {
            binding.textOcr.text = screenshot.ocrText
            binding.textOcr.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_surface_light))
        }

        // Display tags
        displayTags(screenshot.tags)

        // Display metadata
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        binding.textTimestamp.text = "Taken: ${dateFormat.format(Date(screenshot.timestamp))}"
        binding.textCategory.text = "Category: ${screenshot.category?.replace("_", " ")?.capitalize() ?: "Uncategorized"}"
        binding.textProcessingStatus.text = "Status: ${screenshot.processingStatus.name}"
    }

    private fun displayTags(tags: String?) {
        binding.tagsChipGroup.removeAllViews()
        
        if (tags.isNullOrBlank()) {
            val chip = Chip(requireContext()).apply {
                text = "No tags"
                isEnabled = false
            }
            binding.tagsChipGroup.addView(chip)
            return
        }

        tags.split(",").forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag.trim().replace("_", " ").capitalize()
                isClickable = false
                isCheckable = false
            }
            binding.tagsChipGroup.addView(chip)
        }
    }

    private fun setupActions() {
        binding.btnShare.setOnClickListener {
            screenshot?.let { shareScreenshot(it.filePath) }
        }

        binding.btnDelete.setOnClickListener {
            screenshot?.let { deleteScreenshot(it) }
        }
    }

    private fun shareScreenshot(filePath: String) {
        try {
            val uri = if (filePath.startsWith("content://")) {
                Uri.parse(filePath)
            } else {
                FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    File(filePath)
                )
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share screenshot"))
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotDetail", "Error sharing screenshot: ${e.message}")
        }
    }

    private fun deleteScreenshot(screenshot: ScreenshotEntity) {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                screenshotDao.deleteById(screenshot.id)
            }
            // Navigate back
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
