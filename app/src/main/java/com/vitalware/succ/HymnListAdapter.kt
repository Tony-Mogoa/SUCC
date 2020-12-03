package com.vitalware.succ

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vitalware.succ.databinding.HymnItemViewBinding

class HymnListAdapter(val clickListener: HymnListListener, private val optionsListener: HymnOptionsListener): ListAdapter<HymnProfile, HymnListViewHolder>(HymnListDiffCallback()) {

    override fun onBindViewHolder(holder: HymnListViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, clickListener, optionsListener)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HymnListViewHolder {
        return HymnListViewHolder.from(parent)
    }


}

class HymnListDiffCallback : DiffUtil.ItemCallback<HymnProfile>() {
    override fun areItemsTheSame(oldItem: HymnProfile, newItem: HymnProfile): Boolean {
        return oldItem.hymnId == newItem.hymnId
    }

    override fun areContentsTheSame(oldItem: HymnProfile, newItem: HymnProfile): Boolean {
        return oldItem == newItem
    }
}

class HymnListViewHolder private constructor(val binding: HymnItemViewBinding) : RecyclerView.ViewHolder(binding.root){
    fun bind(item: HymnProfile, clickListener: HymnListListener, optionsListener: HymnOptionsListener) {
//        hymn_pack.text = item.packName
//        pack_size.text = item.packSize.toString()
//
//        pack_del_btn.setOnClickListener{
//
//        }
        binding.clickListener = clickListener
        binding.optionsListener = optionsListener
        binding.hymnInList = item
        binding.executePendingBindings()
    }

    companion object {
        fun from(parent: ViewGroup): HymnListViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding =
                HymnItemViewBinding.inflate(layoutInflater, parent, false)
            return HymnListViewHolder(binding)
        }
    }
}

class HymnListListener(val clickListener: (hymnTitle: String, hymnId: String) -> Unit) {
    fun onClick(hymnItem: HymnProfile) = clickListener(hymnItem.title, hymnItem.hymnId)
}

class HymnOptionsListener(val optionsListener: (hymn: HymnProfile, view: View) -> Unit) {
    fun onClick(hymnItem: HymnProfile, view: View) = optionsListener(hymnItem, view)
}
