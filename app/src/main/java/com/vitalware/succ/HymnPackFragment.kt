package com.vitalware.succ


import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vitalware.succ.databinding.FragmentHymnPackBinding

/**
 * A simple [Fragment] subclass.
 */
class HymnPackFragment : Fragment() {
    private lateinit var binding: FragmentHymnPackBinding
    private var database: DatabaseReference = Firebase.database.getReference("hymn_packs")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_hymn_pack, container, false
        )

        val adapter = HymnPacksAdapter(HymnPackListener { packId ->
            deletePack(packId)
        })
        if(binding.hymnPacks.itemDecorationCount == 0){
            val divider = DividerItemDecoration(binding.hymnPacks.context, LinearLayoutManager.VERTICAL)
            binding.hymnPacks.addItemDecoration(divider)
        }
        val hymnPackListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val data = mutableListOf<HymnPack>()
                for(packSnapshot in dataSnapshot.children){
                    val hymnPack = HymnPack(packSnapshot.child("packName").value as String, (packSnapshot.child("packSize").value as Long).toInt(),
                    packSnapshot.key!!)
                    //Toast.makeText(context, packSnapshot.key!!, Toast.LENGTH_SHORT).show()
                    data.add(hymnPack)
                }

                adapter.submitList(data)
                binding.hymnPacks.adapter = adapter

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        database.addValueEventListener(hymnPackListener)
        binding.addHymnPackBtn.setOnClickListener{
            val hymnPack = HymnPack(binding.hymnPackText.text.toString(), 0)
            addHymnPack(hymnPack)
        }
        return binding.root
    }

    private fun addHymnPack(hymnPack: HymnPack){
        if (TextUtils.isEmpty(binding.hymnPackText.text)){
            binding.hymnPackText.error = this@HymnPackFragment.resources.getString(R.string.error_no_input)
        }
        else{
            val id = database.push().key
            if (id != null) {
                database.child(id).setValue(hymnPack)
                Snackbar.make(
                        binding.root,
                        this@HymnPackFragment.resources.getString(R.string.pack_add),
                        Snackbar.LENGTH_LONG
                    )
                    .setAction("Action", null).show()
                binding.hymnPackText.text.clear()
            }
        }

    }

    private  fun deletePack(packId: String){
        database.child(packId).removeValue()
        Snackbar.make(
                binding.root,
                this@HymnPackFragment.resources.getString(R.string.pack_del),
                Snackbar.LENGTH_LONG
            )
            .setAction("Action", null).show()
    }

}
