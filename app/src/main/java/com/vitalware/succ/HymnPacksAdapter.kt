package com.vitalware.succ

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vitalware.succ.databinding.HymnPackViewBinding

class HymnPacksAdapter(val clickListener: HymnPackListener): ListAdapter<HymnPack, ViewHolder>(HymnPacksDiffCallback()) {

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, clickListener)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder.from(parent)
    }


}

class HymnPacksDiffCallback : DiffUtil.ItemCallback<HymnPack>() {
    override fun areItemsTheSame(oldItem: HymnPack, newItem: HymnPack): Boolean {
        return oldItem.packId == newItem.packId
    }

    override fun areContentsTheSame(oldItem: HymnPack, newItem: HymnPack): Boolean {
        return oldItem == newItem
    }

}

class ViewHolder private constructor(val binding: HymnPackViewBinding) : RecyclerView.ViewHolder(binding.root){
    fun bind(item: HymnPack, clickListener: HymnPackListener) {
//        hymn_pack.text = item.packName
//        pack_size.text = item.packSize.toString()
//
//        pack_del_btn.setOnClickListener{
//
//        }
        binding.clickListener = clickListener
        binding.hymnPack = item
        binding.executePendingBindings()
    }

    companion object {
        fun from(parent: ViewGroup): ViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding =
                HymnPackViewBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(binding)
        }
    }
}

class HymnPackListener(val clickListener: (packId: String) -> Unit) {
    fun onClick(pack: HymnPack) = clickListener(pack.packId)
}