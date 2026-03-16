package com.recall.app.presentation.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.recall.app.R

/**
 * Adapter for suggested search chips.
 */
class SuggestedSearchAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<SuggestedSearchAdapter.ViewHolder>() {

    private var searches: List<String> = emptyList()

    class ViewHolder(val view: MaterialCardView) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.search_chip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggested_search, parent, false) as MaterialCardView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val search = searches[position]
        holder.text.text = search
        holder.view.setOnClickListener { onItemClick(search) }
    }

    override fun getItemCount(): Int = searches.size

    fun updateSearches(newSearches: List<String>) {
        searches = newSearches
        notifyDataSetChanged()
    }
}
