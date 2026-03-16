package com.recall.app.presentation.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.recall.app.R

/**
 * Adapter for category chips.
 */
class CategoryChipAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryChipAdapter.ViewHolder>() {

    private var categories: List<String> = emptyList()

    class ViewHolder(val view: MaterialCardView) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.category_chip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_chip, parent, false) as MaterialCardView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.text.text = category.replace("_", " ").capitalize()
        holder.view.setOnClickListener { onItemClick(category) }
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(newCategories: List<String>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}
