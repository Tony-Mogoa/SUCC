package com.vitalware.succ


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vitalware.succ.databinding.SearchItemViewBinding

class SearchListAdapter(val clickListener: SearchListListener, val optionsListener: SearchListOptionsListener): ListAdapter<SearchHymnProfile, SearchListViewHolder>(SearchListDiffCallback()) {

    override fun onBindViewHolder(holder: SearchListViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, clickListener, optionsListener)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchListViewHolder {
        return SearchListViewHolder.from(parent)
    }


}

class SearchListDiffCallback : DiffUtil.ItemCallback<SearchHymnProfile>() {
    override fun areItemsTheSame(oldItem: SearchHymnProfile, newItem: SearchHymnProfile): Boolean {
        return oldItem.hymnId == newItem.hymnId
    }

    override fun areContentsTheSame(oldItem: SearchHymnProfile, newItem: SearchHymnProfile): Boolean {
        return oldItem == newItem
    }
}

class SearchListViewHolder private constructor(val binding: SearchItemViewBinding) : RecyclerView.ViewHolder(binding.root){
    fun bind(item: SearchHymnProfile, clickListener: SearchListListener, optionsListener: SearchListOptionsListener) {

        binding.clickListener = clickListener
        binding.optionsListener = optionsListener
        binding.hymnInList = item
        binding.executePendingBindings()
    }

    companion object {
        fun from(parent: ViewGroup): SearchListViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding =
                SearchItemViewBinding.inflate(layoutInflater, parent, false)
            return SearchListViewHolder(binding)
        }
    }
}

class SearchListListener(val clickListener: (hymnTitle: String, hymnId: String) -> Unit) {
    fun onClick(hymnItem: SearchHymnProfile) = clickListener(hymnItem.title, hymnItem.hymnId)
}

class SearchListOptionsListener(val optionsListener: (hymn: SearchHymnProfile, view: View) -> Unit) {
    fun onClick(hymnItem: SearchHymnProfile, view: View) = optionsListener(hymnItem, view)
}
