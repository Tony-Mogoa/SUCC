package com.vitalware.succ


import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vitalware.succ.databinding.FragmentEditVerseBinding
import java.text.Normalizer
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class EditVerseFragment : Fragment() {
    private lateinit var binding: FragmentEditVerseBinding
    private var database: DatabaseReference = Firebase.database.getReference("titlesAndVerses")
    private var hymnListDatabase: DatabaseReference = Firebase.database.getReference("searchRedundancy")
    private val regex = Regex("[^A-Za-z0-9 ]")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_edit_verse, container, false
        )
        val args = EditVerseFragmentArgs.fromBundle(arguments!!)
        binding.newVerseEdit.setText(args.verseInit)
        binding.ifIsChorus.isChecked = args.isChorus
        binding.saveEditBtn.setOnClickListener{
            editVerse(args.verseId, args.hymnId, args.hymnTitle, args.verseInit)
            database.child(args.hymnId).child(args.verseId).child("verseText").setValue(binding.newVerseEdit.text.toString())
            database.child(args.hymnId).child(args.verseId).child("chorus").setValue(binding.ifIsChorus.isChecked)
            activity!!.onBackPressed()
        }
        return binding.root
    }

    private fun editVerse(verseId: String, hymnId: String, hymnTitle: String, verseInitial: String) {
        database.child(hymnId).child(verseId).child("verseText").setValue(binding.newVerseEdit.text.toString())
        database.child(hymnId).child(verseId).child("chorus").setValue(binding.ifIsChorus.isChecked)
        val verseInitialSplitted = verseInitial.split(" ")
        val titleSplit = hymnTitle.split(" ")
        for (verseLing in verseInitialSplitted) {
            //hymnListDatabase.child(args.hymnId).child("title").child(verseLing).setValue(true)
            if (!titleSplit.contains(verseLing)) {
                var semiPurgedVerseLing = Normalizer.normalize(verseLing, Normalizer.Form.NFD)
                semiPurgedVerseLing = semiPurgedVerseLing.replace("[^\\p{ASCII}]", "")
                val purgedVerseLing = regex.replace(semiPurgedVerseLing, "")
                hymnListDatabase.child(hymnId).child(purgedVerseLing.toLowerCase(Locale.ENGLISH))
                    .removeValue()
            }
        }
        val newVerseSplitted = binding.newVerseEdit.text.toString().split(" ")
        for (newVerseLing in newVerseSplitted) {
            var semiPurgedVerseLing = Normalizer.normalize(newVerseLing, Normalizer.Form.NFD)
            semiPurgedVerseLing = semiPurgedVerseLing.replace("[^\\p{ASCII}]", "")
            val purgedVerseLing = regex.replace(semiPurgedVerseLing, "")
            if (purgedVerseLing != "-" && purgedVerseLing != ""){
                hymnListDatabase.child(hymnId).child(purgedVerseLing.toLowerCase(Locale.ENGLISH))
                    .setValue(true)
            }
        }
    }


}
