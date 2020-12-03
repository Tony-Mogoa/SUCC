package com.vitalware.succ


import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
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
import com.vitalware.succ.databinding.FragmentEditHymnBinding
import java.text.Normalizer
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class EditHymnFragment : Fragment() {
    private lateinit var binding: FragmentEditHymnBinding
    private var packsFromDatabase: DatabaseReference = Firebase.database.getReference("hymn_packs")
    private var databaseTitles: DatabaseReference = Firebase.database.getReference("packsAndTitles")
    private var hymnListDatabase: DatabaseReference = Firebase.database.getReference("searchRedundancy")
    private val regex = Regex("[^A-Za-z0-9 ]")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_edit_hymn, container, false
        )
        databaseTitles.keepSynced(true)
        val args = EditHymnFragmentArgs.fromBundle(arguments!!)
        val oldHymnProfile = HymnProfile(args.hymnTitle, args.hymnNumber, args.hymnPage, args.hymnal)
        binding.editTitle.setText(args.hymnTitle)
        binding.editNumber.setText(args.hymnNumber)
        binding.editPage.setText(args.hymnPage)
        when (args.hymnal) {
            binding.cdhRadio.text.toString() -> binding.cdhRadio.isChecked = true
            binding.blueHymnalRadio.text.toString() -> binding.blueHymnalRadio.isChecked = true
            else -> binding.notInBooksRadio.isChecked = true
        }
        binding.ckbHymnPack.setOnCheckedChangeListener { _, b ->
            if (b){
                binding.editPackList.visibility = View.VISIBLE
                binding.buttonSaveHymnEdit.visibility = View.GONE
            }
            else{
                binding.editPackList.visibility = View.GONE
                binding.buttonSaveHymnEdit.visibility = View.VISIBLE
            }
        }
        val hymnPackListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val data = mutableListOf<HymnPack>()
                for (packSnapshot in dataSnapshot.children) {
                    val hymnPack = HymnPack(
                        packSnapshot.child("packName").value as String,
                        (packSnapshot.child("packSize").value as Long).toInt(),
                        packSnapshot.key!!
                    )
                    //Toast.makeText(context, packSnapshot.key!!, Toast.LENGTH_SHORT).show()
                    data.add(hymnPack)
                }
                val adapter = SingPackAdapter(SingPackListener { packId, _, _: String ->
                    //navigate with args
                    //pass data to verse add
                    editHymn(oldHymnProfile, validateInput(), packId, args.hymnId)
                    decrementPackSize(args.packId)
                    incrementPackSize(packId)
                    databaseTitles.child(args.packId).child(args.hymnId).removeValue()
                    activity!!.onBackPressed()

                })
                adapter.submitList(data)
                if (binding.editPackList.itemDecorationCount == 0) {
                    val divider = DividerItemDecoration(
                        binding.editPackList.context,
                        LinearLayoutManager.VERTICAL
                    )
                    binding.editPackList.addItemDecoration(divider)
                }

                binding.editPackList.adapter = adapter

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        packsFromDatabase.addValueEventListener(hymnPackListener)
        binding.buttonSaveHymnEdit.setOnClickListener {
            if (TextUtils.isEmpty(binding.editTitle.text)) {
                binding.editTitle.error =
                    this@EditHymnFragment.resources.getString(R.string.error_no_input)
            }  else {

                editHymn(oldHymnProfile, validateInput(), args.packId, args.hymnId)
                activity!!.onBackPressed()
            }
        }
        return binding.root
    }

    private fun getSelectedHymnal(radioBtnId: Int): String {

        val radioBtn = binding.hymnalEditRadios.findViewById<RadioButton>(radioBtnId)
        return radioBtn.text.toString()

    }

    private fun editHymn(oldHymnProfile: HymnProfile, newHymnProfile: HymnProfile, packId: String, hymnId: String){
        val oldTitleSplit = oldHymnProfile.title.split(" ")
        val oldSearchKeywords = oldTitleSplit.toMutableList()
        oldSearchKeywords.add(oldHymnProfile.hymnNumber)
        oldSearchKeywords.add(oldHymnProfile.hymnPage)
        val oldHymnalSplit = oldHymnProfile.hymnal.split(" ")
        for (oldHymnalLing in oldHymnalSplit) {

            oldSearchKeywords.add(oldHymnalLing)

        }
        for (oldKeyword in oldSearchKeywords){
            var semiPurgedOldKey = Normalizer.normalize(oldKeyword, Normalizer.Form.NFD)
            semiPurgedOldKey = semiPurgedOldKey.replace("[^\\p{ASCII}]", "")
            val purgedOldKeyword = regex.replace(semiPurgedOldKey, "")
            if (purgedOldKeyword != "Hymnal" && purgedOldKeyword != "-" && purgedOldKeyword != "") {
                hymnListDatabase.child(hymnId).child(purgedOldKeyword.toLowerCase(Locale.ENGLISH))
                    .removeValue()
            }
        }
        databaseTitles.child(packId).child(hymnId).setValue(newHymnProfile)
        val titleSplit = newHymnProfile.title.split(" ")
        val searchKeywords = titleSplit.toMutableList()
        searchKeywords.add(newHymnProfile.hymnNumber)
        searchKeywords.add(newHymnProfile.hymnPage)
        val hymnalSplit = newHymnProfile.hymnal.split(" ")
        for (hymnalLing in hymnalSplit) {

            searchKeywords.add(hymnalLing)

        }
        hymnListDatabase.child(hymnId).setValue(newHymnProfile)
        hymnListDatabase.child(hymnId).child("packId").setValue(packId)
        for (keyword in searchKeywords) {
            var semiPurgedNewKey = Normalizer.normalize(keyword, Normalizer.Form.NFD)
            semiPurgedNewKey = semiPurgedNewKey.replace("[^\\p{ASCII}]", "")
            val purgedKeyword = regex.replace(semiPurgedNewKey, "")
            if (purgedKeyword != "Hymnal" && purgedKeyword != "-" && purgedKeyword != "") {
                hymnListDatabase.child(hymnId).child(purgedKeyword.toLowerCase(Locale.ENGLISH)).setValue(true)
            }
        }
    }

    private fun validateInput(): HymnProfile{
        val hymnProfile: HymnProfile
        val radioBtnId = binding.hymnalEditRadios.checkedRadioButtonId
        hymnProfile = HymnProfile(
            binding.editTitle.text.toString(),
            binding.editNumber.text.toString(),
            binding.editPage.text.toString(),
            getSelectedHymnal(radioBtnId)
        )
        return hymnProfile
    }

    private fun decrementPackSize(packId: String) {
        packsFromDatabase.child(packId).child("packSize")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    // Get Post object and use the values to update the UI
                    val packSize = (dataSnapshot.value as Long).toInt()
                    packsFromDatabase.child(packId).child("packSize").setValue(packSize - 1)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Getting Post failed, log a message
                    // ...
                }
            })
    }

    private fun incrementPackSize(packId: String) {
        packsFromDatabase.child(packId).child("packSize")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    // Get Post object and use the values to update the UI
                    val packSize = (dataSnapshot.value as Long).toInt()
                    packsFromDatabase.child(packId).child("packSize").setValue(packSize + 1)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Getting Post failed, log a message
                    // ...
                }
            })
    }

}
