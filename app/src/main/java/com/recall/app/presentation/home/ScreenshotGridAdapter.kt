package com.recall.app.presentation.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.recall.app.R
import java.io.File

/**
 * Adapter for displaying screenshots in a grid.
 */
class ScreenshotGridAdapter(
    private var screenshots: List<ScreenshotItem>,
    private val onItemClick: (ScreenshotItem) -> Unit
) : RecyclerView.Adapter<ScreenshotGridAdapter.ViewHolder>() {

    data class ScreenshotItem(
        val id: Long,
        val filePath: String,
        val summary: String?,
        val timestamp: Long
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.screenshot_image)
        val summaryText: TextView = view.findViewById(R.id.screenshot_summary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_screenshot_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val screenshot = screenshots[position]

        // Load screenshot image - handle both file paths and content URIs
        try {
            if (screenshot.filePath.startsWith("content://")) {
                // Content URI (scoped storage)
                val contentUri = Uri.parse(screenshot.filePath)
                Glide.with(holder.imageView.context)
                    .load(contentUri)
                    .placeholder(R.drawable.ic_empty_screenshots)
                    .error(R.drawable.ic_empty_screenshots)
                    .centerCrop()
                    .into(holder.imageView)
            } else {
                // File path (legacy)
                val file = File(screenshot.filePath)
                if (file.exists()) {
                    Glide.with(holder.imageView.context)
                        .load(file)
                        .placeholder(R.drawable.ic_empty_screenshots)
                        .error(R.drawable.ic_empty_screenshots)
                        .centerCrop()
                        .into(holder.imageView)
                } else {
                    holder.imageView.setImageResource(R.drawable.ic_empty_screenshots)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenshotAdapter", "Error loading image: ${e.message}")
            holder.imageView.setImageResource(R.drawable.ic_empty_screenshots)
        }

        // Show summary if available
        if (!screenshot.summary.isNullOrBlank()) {
            holder.summaryText.visibility = View.VISIBLE
            holder.summaryText.text = screenshot.summary
        } else {
            holder.summaryText.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(screenshot)
        }
    }

    override fun getItemCount(): Int = screenshots.size

    fun updateScreenshots(newScreenshots: List<ScreenshotItem>) {
        screenshots = newScreenshots
        notifyDataSetChanged()
    }
}
