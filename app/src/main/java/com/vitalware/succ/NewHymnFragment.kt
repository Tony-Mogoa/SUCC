package com.vitalware.succ


import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vitalware.succ.databinding.FragmentNewHymnBinding
import java.text.Normalizer
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class NewHymnFragment : Fragment() {
    private lateinit var binding: FragmentNewHymnBinding
    private var databaseTitles: DatabaseReference = Firebase.database.getReference("packsAndTitles")
    private var packsFromDatabase: DatabaseReference = Firebase.database.getReference("hymn_packs")
    private var audioRecords: DatabaseReference = Firebase.database.getReference("audios")
    private var musicScores: DatabaseReference = Firebase.database.getReference("musicScores")
    private var hymnListDatabase: DatabaseReference = Firebase.database.getReference("searchRedundancy")
    private val regex = Regex("[^A-Za-z0-9 ]")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_new_hymn, container, false
        )

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
                    val radioBtnId = binding.hymnalRadios.checkedRadioButtonId
                    when {
                        TextUtils.isEmpty(binding.hymnTitle.text) -> {
                            binding.hymnTitle.error =
                                this@NewHymnFragment.resources.getString(R.string.error_no_input)
                        }
                        radioBtnId == -1 -> {
                            Snackbar.make(
                                    binding.root,
                                    "Please select a hymnal",
                                    Snackbar.LENGTH_LONG
                                )
                                .setAction("Action", null).show()
                        }
                        else -> {

                            val hymnProfile: HymnProfile
                            val id = databaseTitles.push().key
                            if (id != null) {
                                hymnProfile = HymnProfile(
                                    binding.hymnTitle.text.toString(),
                                    binding.numberText.text.toString(),
                                    binding.pageText.text.toString(),
                                    getSelectedHymnal(radioBtnId)
                                )
                                addHymn(hymnProfile, packId, id)
                            }
                           incrementPackSize(packId)

                            NavHostFragment.findNavController(this@NewHymnFragment)
                                .navigate(

                                    NewHymnFragmentDirections.actionNewHymnFragmentToVerseAddFragment(
                                        id!!, binding.hymnTitle.text.toString()
                                    )
                                )
                        }
                    }

                })
                adapter.submitList(data)
                if (binding.packList.itemDecorationCount == 0) {
                    val divider = DividerItemDecoration(
                        binding.packList.context,
                        LinearLayoutManager.VERTICAL
                    )
                    binding.packList.addItemDecoration(divider)
                }

                binding.packList.adapter = adapter

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        packsFromDatabase.addValueEventListener(hymnPackListener)
        return binding.root
    }

    private fun addHymn(hymnProfile: HymnProfile, packId: String, id: String) {
        databaseTitles.child(packId).child(id).setValue(hymnProfile)
        val titleSplit = hymnProfile.title.split(" ")
        val searchKeywords = titleSplit.toMutableList()
        searchKeywords.add(hymnProfile.hymnNumber)
        searchKeywords.add(hymnProfile.hymnPage)
        val hymnalSplit = hymnProfile.hymnal.split(" ")
        for (hymnalLing in hymnalSplit) {

            searchKeywords.add(hymnalLing)

        }
        hymnListDatabase.child(id).setValue(hymnProfile)
        hymnListDatabase.child(id).child("packId").setValue(packId)
        for (keyword in searchKeywords) {
            var semiPurgedKeyword = Normalizer.normalize(keyword, Normalizer.Form.NFD)
            semiPurgedKeyword = semiPurgedKeyword.replace("[^\\p{ASCII}]", "")
            val purgedKeyword = regex.replace(semiPurgedKeyword, "")
            if (purgedKeyword != "Hymnal" && purgedKeyword != "-" && purgedKeyword != "") {
                hymnListDatabase.child(id).child(purgedKeyword.toLowerCase(Locale.ENGLISH)).setValue(true)
            }


        }
        createAudioRecords(id)
        createMusicScoreRecord(id)
    }

    private  fun createAudioRecords(hymnId: String){
        audioRecords.child(hymnId).setValue(VoiceBundle())
    }

    private fun createMusicScoreRecord(hymnId: String){
        musicScores.child(hymnId).setValue(MusicScore())
    }

    private fun getSelectedHymnal(radioBtnId: Int): String {

        val radioBtn = binding.hymnalRadios.findViewById<RadioButton>(radioBtnId)
        return radioBtn.text.toString()

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
