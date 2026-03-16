package com.recall.app.presentation.timeline

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.recall.app.R
import java.io.File

/**
 * Adapter for displaying timeline sections with date headers.
 */
class TimelineAdapter(
    private val onItemClick: (TimelineScreenshot) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_SECTION = 0
    }
    
    private var sections: List<TimelineSection> = emptyList()
    
    class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateHeader: TextView = view.findViewById(R.id.date_header_text)
        val screenshotsRecycler: RecyclerView = view.findViewById(R.id.date_screenshots_recycler)
    }
    
    override fun getItemViewType(position: Int): Int {
        return VIEW_TYPE_SECTION
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline_date_section, parent, false)
        return SectionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val section = sections[position]
        
        if (holder is SectionViewHolder) {
            holder.dateHeader.text = section.dateLabel
            
            // Setup screenshots grid for this date
            val screenshotAdapter = TimelineScreenshotAdapter(onItemClick)
            holder.screenshotsRecycler.apply {
                layoutManager = GridLayoutManager(holder.itemView.context, 3)
                adapter = screenshotAdapter
            }
            
            screenshotAdapter.updateScreenshots(section.screenshots)
        }
    }
    
    override fun getItemCount(): Int = sections.size
    
    fun updateSections(newSections: List<TimelineSection>) {
        sections = newSections
        notifyDataSetChanged()
    }
}

/**
 * Adapter for displaying screenshots within a date section.
 */
class TimelineScreenshotAdapter(
    private val onItemClick: (TimelineScreenshot) -> Unit
) : RecyclerView.Adapter<TimelineScreenshotAdapter.ViewHolder>() {
    
    private var screenshots: List<TimelineScreenshot> = emptyList()
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.screenshot_image)
        val timeText: TextView = view.findViewById(R.id.screenshot_time)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline_screenshot, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val screenshot = screenshots[position]
        
        // Load screenshot image
        Glide.with(holder.imageView.context)
            .load(File(screenshot.filePath))
            .placeholder(R.drawable.ic_empty_screenshots)
            .error(R.drawable.ic_empty_screenshots)
            .centerCrop()
            .into(holder.imageView)
        
        holder.timeText.text = screenshot.formattedTime
        
        holder.itemView.setOnClickListener {
            onItemClick(screenshot)
        }
    }
    
    override fun getItemCount(): Int = screenshots.size
    
    fun updateScreenshots(newScreenshots: List<TimelineScreenshot>) {
        screenshots = newScreenshots
        notifyDataSetChanged()
    }
}
