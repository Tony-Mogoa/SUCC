package com.vitalware.succ

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vitalware.succ.databinding.SingPackViewBinding

class SingPackAdapter(val clickListener: SingPackListener): ListAdapter<HymnPack, SingViewHolder>(SingPackDiffCallback()) {


    override fun onBindViewHolder(holder: SingViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, clickListener)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SingViewHolder {
        return SingViewHolder.from(parent)
    }


}

class SingPackDiffCallback : DiffUtil.ItemCallback<HymnPack>() {
    override fun areItemsTheSame(oldItem: HymnPack, newItem: HymnPack): Boolean {
        return oldItem.packId == newItem.packId
    }

    override fun areContentsTheSame(oldItem: HymnPack, newItem: HymnPack): Boolean {
        return oldItem == newItem
    }
}

class SingViewHolder private constructor(val binding: SingPackViewBinding) : RecyclerView.ViewHolder(binding.root){
    fun bind(item: HymnPack, clickListener: SingPackListener) {
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
        fun from(parent: ViewGroup): SingViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding =
                SingPackViewBinding.inflate(layoutInflater, parent, false)
            return SingViewHolder(binding)
        }
    }
}

class SingPackListener(val clickListener: (packId: String, packSize: Int, packName: String) -> Unit) {
    fun onClick(pack: HymnPack) = clickListener(pack.packId, pack.packSize, pack.packName)
}