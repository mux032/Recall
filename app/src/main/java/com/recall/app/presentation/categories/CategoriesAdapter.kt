package com.recall.app.presentation.categories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.recall.app.R

/**
 * Adapter for displaying categories with counts.
 */
class CategoriesAdapter(
    private val onItemClick: (CategoryItem) -> Unit
) : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {
    
    private var categories: List<CategoryItem> = emptyList()
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.category_icon)
        val name: TextView = view.findViewById(R.id.category_name)
        val count: TextView = view.findViewById(R.id.category_count)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_card, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        
        holder.name.text = category.displayName
        holder.count.text = "${category.count} screenshot${if (category.count != 1) "s" else ""}"
        
        // Set category-specific icon color
        val iconColor = getCategoryColor(category.name)
        holder.icon.setColorFilter(iconColor)
        
        holder.itemView.setOnClickListener {
            onItemClick(category)
        }
    }
    
    override fun getItemCount(): Int = categories.size
    
    fun updateCategories(newCategories: List<CategoryItem>) {
        categories = newCategories
        notifyDataSetChanged()
    }
    
    private fun getCategoryColor(categoryName: String): Int {
        return when (categoryName.lowercase()) {
            "shopping" -> R.color.category_shopping
            "travel" -> R.color.category_travel
            "code" -> R.color.category_code
            "food" -> R.color.category_food
            "finance" -> R.color.category_finance
            "social" -> R.color.category_social
            "work" -> R.color.category_work
            "recipes" -> R.color.category_recipes
            else -> R.color.primary
        }.let { colorRes ->
            // Would need context for proper color resolution
            // For now, return a default color
            0xFF2563EB.toInt() // Primary blue
        }
    }
}
