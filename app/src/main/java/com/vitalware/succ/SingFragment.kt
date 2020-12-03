@file:Suppress("DEPRECATION")

package com.vitalware.succ

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.datetime.datePicker
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.faltenreich.skeletonlayout.Skeleton
import com.faltenreich.skeletonlayout.applySkeleton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.vitalware.succ.databinding.FragmentSingBinding
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class SingFragment : Fragment() {
    companion object {
        const val THEME_NAME = "theme_name"
    }

    private val regex = Regex("[^A-Za-z0-9 ]")
    private lateinit var binding: FragmentSingBinding
    private lateinit var skeleton: Skeleton
    private var database: DatabaseReference = Firebase.database.getReference("theme")
    private var favourites: DatabaseReference = Firebase.database.getReference("favorites")
    private var hymnsForMass: DatabaseReference = Firebase.database.getReference("MassHymns")
    private var databaseTitles: DatabaseReference = Firebase.database.getReference("packsAndTitles")
    private var databaseHymnVerses: DatabaseReference =
        Firebase.database.getReference("titlesAndVerses")
    private var searchData: DatabaseReference =
        Firebase.database.getReference("searchRedundancy")
    private var packsFromDatabase: DatabaseReference = Firebase.database.getReference("hymn_packs")
    private val storageRef = Firebase.storage.getReference("audios")
    private val storageScores = Firebase.storage.getReference("musicScores")
    private var audioRecords: DatabaseReference = Firebase.database.getReference("audios")
    private var musicScores: DatabaseReference = Firebase.database.getReference("musicScores")
    private lateinit var hymnPackListener: ValueEventListener
    private lateinit var themeListener:ValueEventListener
    private lateinit var hymnListListener:ValueEventListener
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_sing, container, false
        )
        searchData.keepSynced(true)

        setHasOptionsMenu(true)
        skeleton = binding.hymnPackInSing.applySkeleton(R.layout.skeleton_loader_view, 15)
        skeleton.showSkeleton()
        binding.hymnPackInSing.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

        val singAdapter = SingPackAdapter(SingPackListener { packId, packSize, packName ->
            NavHostFragment.findNavController(this@SingFragment)
                .navigate(
                    SingFragmentDirections.actionSingFragmentToHymnListFragment(
                        packId, packName, packSize
                    )
                )
        })

        if (binding.hymnPackInSing.itemDecorationCount == 0) {
            val divider = DividerItemDecoration(
                binding.hymnPackInSing.context,
                LinearLayoutManager.VERTICAL
            )
            binding.hymnPackInSing.addItemDecoration(divider)
        }

        hymnPackListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                makeNotifyInvisible()
                val data = mutableListOf<HymnPack>()
                for (packSnapshot in dataSnapshot.children) {
                    val hymnPack = HymnPack(
                        packSnapshot.child("packName").value as String,
                        (packSnapshot.child("packSize").value as Long).toInt(),
                        packSnapshot.key!!
                    )
                    data.add(hymnPack)
                }

                skeleton.showOriginal()

                singAdapter.submitList(data)
                binding.hymnPackInSing.adapter = singAdapter
                binding.hymnPackInSing.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
                Log.e("Firebase", databaseError.message)
            }
        }

        packsFromDatabase.addValueEventListener(hymnPackListener)


        val searchSupervisor = mutableMapOf<SearchHymnProfile, Int>()
        val finalList = mutableListOf<SearchHymnProfile>()

        val adapter = SearchListAdapter(SearchListListener { hymnTitle, hymnId ->
            //navigate with args
            hideKeyboard()
            NavHostFragment.findNavController(this@SingFragment)
                .navigate(
                    SingFragmentDirections.actionSingFragmentToSingingFragment(
                        hymnTitle,
                        hymnId
                    )
                )

        }, SearchListOptionsListener { hymn, hymnView ->
            val popup = PopupMenu(context, hymnView)
            popup.menuInflater.inflate(R.menu.hymn_option_menu, popup.menu)

            PreferenceManager.getDefaultSharedPreferences(context).apply {
                when (getInt(AuthCodeFragment.USER_ACCESS_LEVEL, 1)) {
                    1 -> {
                        popup.menu.removeItem(R.id.editHymn)
                        popup.menu.removeItem(R.id.deleteHymn)
                        popup.menu.removeItem(R.id.alterVerses)
                        popup.menu.removeItem(R.id.forMassList)
                    }
                    2 -> {
                        popup.menu.removeItem(R.id.editHymn)
                        popup.menu.removeItem(R.id.deleteHymn)
                        popup.menu.removeItem(R.id.alterVerses)
                    }
                }
            }

            popup.setOnMenuItemClickListener {

                when (it.itemId) {
                    R.id.editHymn -> {
                        NavHostFragment.findNavController(this@SingFragment)
                            .navigate(
                                SingFragmentDirections.actionSingFragmentToEditHymnFragment(
                                    hymn.title,
                                    hymn.hymnId,
                                    hymn.hymnPage,
                                    hymn.hymnNumber,
                                    hymn.hymnal,
                                    hymn.packId
                                )
                            )
                    }

                    R.id.deleteHymn -> {
                        deleteHymn(hymn.packId, hymn.hymnId)

                    }

                    R.id.alterVerses -> {
                        NavHostFragment.findNavController(this@SingFragment)
                            .navigate(
                                SingFragmentDirections.actionSingFragmentToVerseAddFragment(
                                    hymn.hymnId, hymn.title
                                )
                            )
                    }
                    R.id.addToFav -> {
                        favourites.child(getUserId()).child(hymn.hymnId).setValue(hymn)
                        Snackbar.make(
                                binding.root,
                                getString(R.string.fav_added),
                                Snackbar.LENGTH_LONG
                            )
                            .setAction("Action", null).show()
                    }
                    R.id.forMassList -> {
                        var date = Calendar.getInstance().time
                        val formatter = SimpleDateFormat.getDateInstance()
                        var formattedDate = formatter.format(date)
                        var dateSet = false

                        MaterialDialog(context!!).show {
                            datePicker { _, datetime ->
                                date = datetime.time
                                formattedDate = formatter.format(date)
                                dateSet = true
                            }

                        }.onDismiss {
                            if (dateSet) {
                                MaterialDialog(context!!).show {
                                    title(text = formattedDate)
                                    listItemsSingleChoice(R.array.mass_radios) { _, index, text ->
                                        val massHymn = MassHymn(
                                            hymn.title,
                                            hymn.hymnNumber,
                                            hymn.hymnPage,
                                            hymn.hymnal,
                                            hymn.hymnId,
                                            text.toString(),
                                            index
                                        )
                                        hymnsForMass.child(formattedDate).child(hymn.hymnId)
                                            .setValue(massHymn)
//                                        hymnsForMass.child(formattedDate).child(hymn.hymnId).child("index").setValue(index)
//                                        hymnsForMass.child(formattedDate).child(hymn.hymnId).child("type").setValue(text)

                                    }
                                    //title()
                                    positiveButton(R.string.add)
                                }.onDismiss {
                                    Snackbar.make(
                                            binding.root,
                                            getString(R.string.mass_hymn_set),
                                            Snackbar.LENGTH_LONG
                                        )
                                        .setAction("Action", null).show()
                                }
                            }

                        }

                    }

                }
                true

            }

            popup.show()
        })

        adapter.submitList(finalList)

        hymnListListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                skeleton.showOriginal()

                makeNotifyInvisible()
                for (hymnSnapshot in dataSnapshot.children) {

                    try {
                        val hymnProfile = SearchHymnProfile(
                            hymnSnapshot.child("title").value as String,
                            hymnSnapshot.child("hymnNumber").value as String,
                            hymnSnapshot.child("hymnPage").value as String,
                            hymnSnapshot.child("hymnal").value as String,
                            hymnSnapshot.key!!,
                            hymnSnapshot.child("packId").value as String
                        )
                        if (!searchSupervisor.containsKey(hymnProfile)) {
                            searchSupervisor[hymnProfile] = 0

                        } else {

                            val newCount = searchSupervisor.getValue(hymnProfile).plus(1)
                            searchSupervisor[hymnProfile] = newCount
                        }
                    }catch (e: Exception){
                        //Log.e("rotten", hymnSnapshot.key!! + " " + e.localizedMessage)
                    }
                }
                val searchList =
                    searchSupervisor.toList().sortedByDescending { hymn -> hymn.second }
                finalList.clear()
                for (resultItem in searchList) {
                    finalList.add(resultItem.first)
                }
                if (finalList.size < 1) {
                    binding.hymnPackInSing.visibility = View.GONE
                    binding.oopsImage.visibility = View.VISIBLE
                    binding.dataStateText.visibility = View.VISIBLE
                }
                adapter.submitList(finalList)

                binding.hymnPackInSing.adapter = adapter
                binding.hymnPackInSing.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }

        //searchData.addValueEventListener(hymnListListener)


        binding.searchText.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, countx: Int) {
                skeleton.showSkeleton()
                binding.hymnPackInSing.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
                val searchString = s.split(" ")
                packsFromDatabase.removeEventListener(hymnPackListener)
                searchSupervisor.clear()
                finalList.clear()
                adapter.submitList(finalList)

                for (sWord in searchString) {
                    var semiPurgedKeyWord = Normalizer.normalize(sWord, Normalizer.Form.NFD)
                    semiPurgedKeyWord = semiPurgedKeyWord.replace("[^\\p{ASCII}]", "")
                    val purgedKeyWord = regex.replace(semiPurgedKeyWord, "")
                    if (purgedKeyWord != "") {
                        searchData.orderByChild(purgedKeyWord.toLowerCase(Locale.ENGLISH))
                            .equalTo(true)
                            .addListenerForSingleValueEvent(hymnListListener)
                    }

                }

                if (s.isEmpty()) {
                    if(isAdded){
                        packsFromDatabase.addValueEventListener(hymnPackListener)
                    }
                }


            }

        })
        themeListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Get Post object and use the values to update the UI
                val theme = dataSnapshot.value as String
                if (isAdded) {
                    PreferenceManager.getDefaultSharedPreferences(context).apply {
                        if (theme != getString(THEME_NAME, "Violet")) {
                            PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                                putString(THEME_NAME, theme)
                                apply()
                            }
                            if (NavHostFragment.findNavController(this@SingFragment).currentDestination?.id == R.id.singFragment) {
                                Snackbar.make(
                                        binding.root,
                                        this@SingFragment.resources.getString(R.string.theme_update),
                                        5000
                                    )
                                    .setAction("UPDATE") {
                                        activity!!.recreate()
                                    }.show()
                            }
                        }

                    }
                }


            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }

        database.addValueEventListener(themeListener)

        PreferenceManager.getDefaultSharedPreferences(context).apply {
            if (!getBoolean(AuthCodeFragment.COMPLETED_ON_BOARDING_PREF_NAME, false)) {
                if (NavHostFragment.findNavController(this@SingFragment).currentDestination?.id == R.id.singFragment) {
                    database.removeEventListener(themeListener)
                    NavHostFragment.findNavController(this@SingFragment)
                        .navigate(SingFragmentDirections.actionSingFragmentToFirstNameFragment())
                }

            } else {
                if (activity is AppCompatActivity) {
                    (activity as AppCompatActivity).supportActionBar!!.show()
                }
            }

        }

        return binding.root
    }

    private fun deleteHymn(packId: String, hymnId: String) {
        databaseTitles.child(packId).child(hymnId).removeValue()
        databaseHymnVerses.child(hymnId).removeValue()
        searchData.child(hymnId).removeValue()
        decrementPackSize(packId)
        deleteAccompanyingFiles(hymnId)
        deleteFromFavHymns(hymnId)
    }

    private fun decrementPackSize(packId: String) {
        packsFromDatabase.child(packId).child("packSize")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    // Get Post object and use the values to update the UI
                    val packSize = (dataSnapshot.value as Long).toInt()
                    packsFromDatabase.child(packId).child("packSize").setValue(packSize - 1)
                    Snackbar.make(
                            binding.root,
                            this@SingFragment.resources.getString(R.string.hymn_deleted),
                            Snackbar.LENGTH_LONG
                        )
                        .setAction("Action", null).show()
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Getting Post failed, log a message
                    // ...
                }
            })
    }

    private fun deleteAccompanyingFiles(hymnId: String) {
        val audioDataListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (voiceSnapshot in dataSnapshot.children) {
                    if (voiceSnapshot.value as Boolean) {
                        storageRef.child(hymnId + voiceSnapshot.key).delete()
                    }
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        audioRecords.child(hymnId).addListenerForSingleValueEvent(audioDataListener)
        val scoreListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.child("hasMusicScore").value as Boolean) {
                    storageScores.child(hymnId).delete()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        musicScores.child(hymnId).addListenerForSingleValueEvent(scoreListener)
        audioRecords.child(hymnId).removeValue()
        musicScores.child(hymnId).removeValue()
    }

    private fun makeNotifyInvisible() {
        binding.hymnPackInSing.visibility = View.VISIBLE
        binding.oopsImage.visibility = View.GONE
        binding.dataStateText.visibility = View.GONE
    }

    private fun getUserId(): String {
        PreferenceManager.getDefaultSharedPreferences(context).apply {
            return getString(FirstNameFragment.USER_ID, "")!!
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.feedback_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.feedback -> {
                composeEmail(
                    addresses = arrayOf(getString(R.string.app_dev_email)),
                    subject = getString(R.string.feedback_title)
                )
                true
            }
            else -> false
        }
    }

    private fun composeEmail(addresses: Array<String>, subject: String) {
        Log.e("test", "test")
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, addresses)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        //startActivity(intent)
        if (intent.resolveActivity(activity!!.packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun deleteFromFavHymns(hymnId: String) {
        val favHymnListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (userSnapshot in dataSnapshot.children) {
                    for (hymnSnapshot in userSnapshot.children) {
                        if (hymnSnapshot.key!! == hymnId) {
                            favourites.child(userSnapshot.key!!).child(hymnSnapshot.key!!)
                                .removeValue()
                        }
                    }
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        favourites.addListenerForSingleValueEvent(favHymnListener)

        val fromMassHymnsListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (dateSnapshot in dataSnapshot.children) {
                    for (hymnSnapshot in dateSnapshot.children) {
                        if (hymnSnapshot.key!! == hymnId) {
                            hymnsForMass.child(dateSnapshot.key!!).child(hymnSnapshot.key!!)
                                .removeValue()
                        }
                    }
                }

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        hymnsForMass.addListenerForSingleValueEvent(fromMassHymnsListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        packsFromDatabase.removeEventListener(hymnPackListener)
        database.removeEventListener(themeListener)
    }

    private fun hideKeyboard(){
        val inputMethodManager = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}


