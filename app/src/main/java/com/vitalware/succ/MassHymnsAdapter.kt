package com.vitalware.succ

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vitalware.succ.databinding.MassHymnViewBinding

class MassListAdapter(val clickListener: MassListListener, private val optionsListener: MassListOptionsListener): ListAdapter<MassHymn, MassListViewHolder>(MassListDiffCallback()) {

    override fun onBindViewHolder(holder: MassListViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, clickListener, optionsListener)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MassListViewHolder {
        return MassListViewHolder.from(parent)
    }


}

class MassListDiffCallback : DiffUtil.ItemCallback<MassHymn>() {
    override fun areItemsTheSame(oldItem: MassHymn, newItem: MassHymn): Boolean {
        return oldItem.hymnId == newItem.hymnId
    }

    override fun areContentsTheSame(oldItem: MassHymn, newItem: MassHymn): Boolean {
        return oldItem == newItem
    }
}

class MassListViewHolder private constructor(val binding: MassHymnViewBinding) : RecyclerView.ViewHolder(binding.root){
    fun bind(item: MassHymn, clickListener: MassListListener, optionsListener: MassListOptionsListener) {
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
        fun from(parent: ViewGroup): MassListViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding =
                MassHymnViewBinding.inflate(layoutInflater, parent, false)
            return MassListViewHolder(binding)
        }
    }
}

class MassListListener(val clickListener: (hymnTitle: String, hymnId: String) -> Unit) {
    fun onClick(hymnItem: MassHymn) = clickListener(hymnItem.title, hymnItem.hymnId)
}

class MassListOptionsListener(val optionsListener: (hymn: MassHymn, view: View) -> Unit) {
    fun onClick(hymnItem: MassHymn, view: View) = optionsListener(hymnItem, view)
}
