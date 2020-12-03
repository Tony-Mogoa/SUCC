package com.vitalware.succ


import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.vitalware.succ.databinding.FragmentHymnListBinding
import java.text.SimpleDateFormat
import java.util.*


/**
 * A simple [Fragment] subclass.
 */
class HymnListFragment : Fragment() {
    private lateinit var binding: FragmentHymnListBinding
    private lateinit var skeleton: Skeleton
    private var databaseTitles: DatabaseReference = Firebase.database.getReference("packsAndTitles")
    private var databaseHymnVerses: DatabaseReference =
        Firebase.database.getReference("titlesAndVerses")
    private var searchData: DatabaseReference =
        Firebase.database.getReference("searchRedundancy")
    private var favourites: DatabaseReference = Firebase.database.getReference("favorites")
    private var hymnsForMass: DatabaseReference = Firebase.database.getReference("MassHymns")
    private var packsFromDatabase: DatabaseReference = Firebase.database.getReference("hymn_packs")
    private val storageRef = Firebase.storage.getReference("audios")
    private val storageScores = Firebase.storage.getReference("musicScores")
    private var audioRecords: DatabaseReference = Firebase.database.getReference("audios")
    private var musicScores: DatabaseReference = Firebase.database.getReference("musicScores")
    private lateinit var adapter: HymnListAdapter
    private val connectedRef = Firebase.database.getReference(".info/connected")
    private lateinit var connectionListener: ValueEventListener
    private lateinit var hymnListListener: ValueEventListener
    private lateinit var args: HymnListFragmentArgs
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_hymn_list, container, false
        )
        databaseTitles.keepSynced(true)
        skeleton = binding.hymnList.applySkeleton(R.layout.hymn_item_view, 15)
        skeleton.showSkeleton()
        binding.hymnList.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        args = HymnListFragmentArgs.fromBundle(arguments!!)

        if (activity is AppCompatActivity) {
            (activity as AppCompatActivity).supportActionBar!!.title = args.packName
        }

        connectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                notifyNoInternet(connected)
            }

            override fun onCancelled(error: DatabaseError) {
                //something
            }
        }
        connectedRef.addValueEventListener(connectionListener)
        adapter = HymnListAdapter(HymnListListener { hymnTitle, hymnId ->
            //navigate with args
            NavHostFragment.findNavController(this@HymnListFragment)
                .navigate(
                    HymnListFragmentDirections.actionHymnListFragmentToSingingFragment(
                        hymnTitle,
                        hymnId
                    )
                )

        }, HymnOptionsListener { hymn, hymnView ->
            //handle optionMenu
            val popup = PopupMenu(context, hymnView)
            popup.menuInflater.inflate(R.menu.hymn_option_menu, popup.menu)
            PreferenceManager.getDefaultSharedPreferences(context).apply {
                when(getInt(AuthCodeFragment.USER_ACCESS_LEVEL, 1)){
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
                        NavHostFragment.findNavController(this@HymnListFragment)
                            .navigate(
                                HymnListFragmentDirections.actionHymnListFragmentToEditHymnFragment(
                                    hymn.title,
                                    hymn.hymnId,
                                    hymn.hymnPage,
                                    hymn.hymnNumber,
                                    hymn.hymnal,
                                    args.packId
                                )
                            )
                    }

                    R.id.deleteHymn -> {
                        deleteHymn(args.packId, hymn.hymnId)
                    }

                    R.id.alterVerses -> {
                        NavHostFragment.findNavController(this@HymnListFragment)
                            .navigate(
                                HymnListFragmentDirections.actionHymnListFragmentToVerseAddFragment(
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
                            if (dateSet){
                                MaterialDialog(context!!).show {
                                    title(text = formattedDate)
                                    listItemsSingleChoice(R.array.mass_radios) { _, index, text ->
                                        val massHymn = MassHymn(hymn.title, hymn.hymnNumber, hymn.hymnPage, hymn.hymnal, hymn.hymnId, text.toString(), index)
                                        hymnsForMass.child(formattedDate).child(hymn.hymnId).setValue(massHymn)
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
        if (binding.hymnList.itemDecorationCount == 0) {
            val divider = DividerItemDecoration(
                binding.hymnList.context,
                LinearLayoutManager.VERTICAL
            )
            binding.hymnList.addItemDecoration(divider)
        }

        hymnListListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                val data = mutableListOf<HymnProfile>()
                makeNotificationInvisible()
                for (hymnSnapshot in dataSnapshot.children) {
                    val hymnProfile = HymnProfile(
                        hymnSnapshot.child("title").value as String,
                        hymnSnapshot.child("hymnNumber").value as String,
                        hymnSnapshot.child("hymnPage").value as String,
                        hymnSnapshot.child("hymnal").value as String,
                        hymnSnapshot.key!!
                    )
                    data.add(hymnProfile)
                }


                skeleton.showOriginal()
                adapter.submitList(data)
                binding.hymnList.adapter = adapter
                binding.hymnList.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        databaseTitles.child(args.packId).orderByChild("title").addValueEventListener(hymnListListener)
        return binding.root
    }

    private fun deleteHymn(packId: String, hymnId: String) {
        databaseTitles.child(packId).child(hymnId).removeValue()
        databaseHymnVerses.child(hymnId).removeValue()
        searchData.child(hymnId).removeValue()
        decrementPackSize(packId)
        deleteAccompanyingFiles(hymnId)
        deleteFromFavNMassHymns(hymnId)

    }

    private fun decrementPackSize(packId: String){
        packsFromDatabase.child(packId).child("packSize").addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Get Post object and use the values to update the UI
                val packSize = (dataSnapshot.value as Long).toInt()
                packsFromDatabase.child(packId).child("packSize").setValue(packSize - 1 )
                if(isAdded) {
                    Snackbar.make(
                            binding.root,
                            this@HymnListFragment.resources.getString(R.string.hymn_deleted),
                            Snackbar.LENGTH_LONG
                        )
                        .setAction("Action", null).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        })
    }

    private fun deleteAccompanyingFiles(hymnId: String){
        val audioDataListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (voiceSnapshot in dataSnapshot.children) {
                    if(voiceSnapshot.value as Boolean) {
                        storageRef.child(hymnId + voiceSnapshot.key).delete()
                        audioRecords.child(hymnId).removeValue()
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
                if(dataSnapshot.child("hasMusicScore").value as Boolean){
                    storageScores.child(hymnId).delete()
                    musicScores.child(hymnId).removeValue()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        musicScores.child(hymnId).addListenerForSingleValueEvent(scoreListener)


    }

    private fun notifyNoInternet(connected: Boolean) {
        if (!connected && binding.hymnList.adapter != adapter) {
            val handler = Handler()
            val runnableCode = Runnable {
                if (binding.hymnList.adapter != adapter){
                    binding.hymnList.visibility = View.GONE
                    binding.oopsImage.visibility = View.VISIBLE
                    binding.dataStateText.visibility = View.VISIBLE
                }
            }
            handler.postDelayed(runnableCode, 6000)
        }
    }

    private fun makeNotificationInvisible() {
        binding.hymnList.visibility = View.VISIBLE
        binding.oopsImage.visibility = View.GONE
        binding.dataStateText.visibility = View.GONE
    }

    private fun getUserId(): String {
        PreferenceManager.getDefaultSharedPreferences(context).apply {
            return getString(FirstNameFragment.USER_ID, "")!!
        }
    }

    private fun deleteFromFavNMassHymns(hymnId: String){
        val favHymnListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (userSnapshot in dataSnapshot.children) {
                    for (hymnSnapshot in userSnapshot.children){
                        if (hymnSnapshot.key!! == hymnId){
                            favourites.child(userSnapshot.key!!).child(hymnSnapshot.key!!).removeValue()
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
                    for (hymnSnapshot in dateSnapshot.children){
                        if (hymnSnapshot.key!! == hymnId){
                            hymnsForMass.child(dateSnapshot.key!!).child(hymnSnapshot.key!!).removeValue()
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
        connectedRef.removeEventListener(connectionListener)
        databaseTitles.child(args.packId).removeEventListener(hymnListListener)
    }
}
