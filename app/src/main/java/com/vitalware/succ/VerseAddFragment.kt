package com.vitalware.succ


import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vitalware.succ.databinding.FragmentVerseAddBinding
import java.text.Normalizer
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class VerseAddFragment : Fragment() {
    private lateinit var binding: FragmentVerseAddBinding
    private var database: DatabaseReference = Firebase.database.getReference("titlesAndVerses")
    private var hymnListDatabase: DatabaseReference = Firebase.database.getReference("searchRedundancy")
    private val regex = Regex("[^A-Za-z0-9 ]")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_verse_add, container, false
        )
        val args = VerseAddFragmentArgs.fromBundle(arguments!!)

        binding.addVerseBtn.setOnClickListener {
            if (TextUtils.isEmpty(binding.verseText.text)) {
                binding.verseText.error = this@VerseAddFragment.resources.getString(R.string.error_no_input)
            } else {
                val verse = Verse(binding.verseText.text.toString(), binding.isChorusCkb.isChecked)
                val pushId = database.push().key
                if (pushId != null) {
                    database.child(args.hymnId).child(pushId).setValue(verse)
                    val verseSplitted = binding.verseText.text.toString().split(" ")
                    for (verseLing in verseSplitted) {
                        var semiPurgedVerseLing = Normalizer.normalize(verseLing, Normalizer.Form.NFD)
                        semiPurgedVerseLing = semiPurgedVerseLing.replace("[^\\p{ASCII}]", "")
                        val purgedVerseLing = regex.replace(semiPurgedVerseLing, "")
                        if (purgedVerseLing != "" && purgedVerseLing != "-"){
                            hymnListDatabase.child(args.hymnId).child(purgedVerseLing.toLowerCase(Locale.ENGLISH))
                                .setValue(true)
                        }
                    }
                }
                binding.verseText.text.clear()
                binding.isChorusCkb.isChecked = false
            }

        }
        val verseListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val data = mutableListOf<Verse>()
                for (packSnapshot in dataSnapshot.children) {
                    val verse = Verse(
                        packSnapshot.child("verseText").value as String,
                        packSnapshot.child("chorus").value as Boolean,
                        packSnapshot.key!!
                    )
                    data.add(verse)
                }
                val adapter = HymnAdapter(VerseListener { verse, view ->
                    val popup = PopupMenu(context, view)
                    popup.menuInflater.inflate(R.menu.options_menu_verse, popup.menu)
                    popup.setOnMenuItemClickListener {

                        when (it.itemId) {
                            R.id.edit_verse -> {
                                NavHostFragment.findNavController(this@VerseAddFragment)
                                    .navigate(
                                        VerseAddFragmentDirections.actionVerseAddFragmentToEditVerseFragment(
                                            verse.verseText,
                                            verse.verseId,
                                            verse.isChorus, args.hymnId,
                                            args.hymnTitle
                                        )
                                    )
                            }

                            R.id.delete_verse -> {
                                deleteVerse(verse.verseId, args.hymnId)
                            }
                        }
                        true

                    }

                    popup.show()

                })
                adapter.submitList(data)

                binding.verseList.adapter = adapter

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        database.child(args.hymnId).addValueEventListener(verseListener)

        binding.finish.setOnClickListener {

            activity!!.onBackPressed()
        }
        return binding.root
    }

    private fun deleteVerse(verseId: String, hymnId: String) {
        database.child(hymnId).child(verseId).removeValue()
        Snackbar.make(
                binding.root,
                this@VerseAddFragment.resources.getString(R.string.verse_del),
                Snackbar.LENGTH_LONG
            )
            .setAction("Action", null).show()
    }


}
