package com.vitalware.succ

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vitalware.succ.databinding.VerseViewBinding


class HymnAdapter(val clickListener: VerseListener): ListAdapter<Verse, HymnViewHolder>(HymnDiffCallback()) {
    override fun onBindViewHolder(holder: HymnViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, clickListener)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HymnViewHolder {
        return HymnViewHolder.from(parent)
    }


}

class HymnDiffCallback : DiffUtil.ItemCallback<Verse>() {
    override fun areItemsTheSame(oldItem: Verse, newItem: Verse): Boolean {
        return oldItem.verseId == newItem.verseId
    }

    override fun areContentsTheSame(oldItem: Verse, newItem: Verse): Boolean {
        return oldItem == newItem
    }

}

class HymnViewHolder private constructor(val binding: VerseViewBinding) : RecyclerView.ViewHolder(binding.root){
    fun bind(item: Verse, clickListener: VerseListener) {
        binding.clickListener = clickListener
        binding.verse = item
        binding.executePendingBindings()
    }

    companion object {
        fun from(parent: ViewGroup): HymnViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding =
                VerseViewBinding.inflate(layoutInflater, parent, false)
            return HymnViewHolder(binding)
        }
    }
}

class VerseListener(val clickListener: (verse: Verse, view: View) -> Unit) {
    fun onClick(verse: Verse, view: View) = clickListener(verse, view)

}