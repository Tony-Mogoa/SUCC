package com.vitalware.succ


import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
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
import com.vitalware.succ.databinding.FragmentHymnListBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * A simple [Fragment] subclass.
 */
class FavouritesFragment : Fragment() {
    private lateinit var binding: FragmentHymnListBinding
    private lateinit var skeleton: Skeleton
    private var hymnsForMass: DatabaseReference = Firebase.database.getReference("MassHymns")
    private var favourites: DatabaseReference = Firebase.database.getReference("favorites")
    private lateinit var adapter: HymnListAdapter
    private val handler = Handler()
    private val runnableCode = Runnable {
        if (binding.hymnList.adapter != adapter) {
            binding.hymnList.visibility = View.GONE
            binding.oopsImage.visibility = View.VISIBLE
            binding.oopsImage.setImageResource(R.drawable.ic_signal_cellular_connected_no_internet_4_bar_black_24dp)
            binding.dataStateText.visibility = View.VISIBLE
            binding.dataStateText.text = getString(R.string.turn_data_on)
        }
    }
    private val connectedRef = Firebase.database.getReference(".info/connected")
    private lateinit var connectedListener: ValueEventListener
    private lateinit var hymnListListener: ValueEventListener
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_hymn_list, container, false
        )
        favourites.keepSynced(true)
        skeleton = binding.hymnList.applySkeleton(R.layout.hymn_item_view, 15)
        skeleton.showSkeleton()
        binding.hymnList.adapter?.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                notifyNoInternet(connected)
            }

            override fun onCancelled(error: DatabaseError) {
                //something
            }
        }
        connectedRef.addValueEventListener(connectedListener)
        adapter = HymnListAdapter(HymnListListener { hymnTitle, hymnId ->
            //navigate with args
            NavHostFragment.findNavController(this@FavouritesFragment)
                .navigate(
                    FavouritesFragmentDirections.actionFavouritesFragmentToSingingFragment(
                        hymnTitle,
                        hymnId
                    )
                )

        }, HymnOptionsListener { hymn, hymnView ->
            //handle optionMenu
            val popup = PopupMenu(context, hymnView)
            popup.menuInflater.inflate(R.menu.fav_hymn_option_menu, popup.menu)
            PreferenceManager.getDefaultSharedPreferences(context).apply {
                when(getInt(AuthCodeFragment.USER_ACCESS_LEVEL, 1)){
                    1 -> {
                        popup.menu.removeItem(R.id.forMass)
                    }
                }
            }
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.deleteFav ->{
                        favourites.child(getUserId()).child(hymn.hymnId).removeValue()
                    }

                    R.id.forMass -> {
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
                if(data.size < 1){
                    binding.hymnList.visibility = View.GONE
                    binding.oopsImage.visibility = View.VISIBLE
                    binding.oopsImage.setImageResource(R.drawable.ic_sentiment_dissatisfied_black_24dp)
                    binding.dataStateText.visibility = View.VISIBLE
                    if (isAdded){
                        binding.dataStateText.text = getString(R.string.nothing_here)
                    }
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
        favourites.child(getUserId()).addValueEventListener(hymnListListener)
        return binding.root
    }


    private fun getUserId(): String{
        PreferenceManager.getDefaultSharedPreferences(context).apply {
            return getString(FirstNameFragment.USER_ID, "")!!
        }
    }

    private fun notifyNoInternet(connected: Boolean){
        if(!connected && binding.hymnList.adapter != adapter){
            handler.postDelayed(runnableCode, 6000)
        }
    }

    private fun makeNotificationInvisible(){
        binding.hymnList.visibility = View.VISIBLE
        binding.oopsImage.visibility = View.GONE
        binding.dataStateText.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(runnableCode)
        connectedRef.removeEventListener(connectedListener)
        favourites.child(getUserId()).removeEventListener(hymnListListener)
    }
}
