package com.vitalware.succ


import android.os.Bundle
import android.os.Handler
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.PopupMenu
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.datetime.datePicker
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
class MassFragment : Fragment() {
    private lateinit var binding: FragmentHymnListBinding
    private lateinit var skeleton: Skeleton
    private var hymnsForMass: DatabaseReference = Firebase.database.getReference("MassHymns")
    private val formatter = SimpleDateFormat.getDateInstance()
    private lateinit var hymnListListener: ValueEventListener
    private lateinit var adapter: MassListAdapter
    private var favourites: DatabaseReference = Firebase.database.getReference("favorites")
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
    private lateinit var connectedListener: ValueEventListener
    private val connectedRef = Firebase.database.getReference(".info/connected")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_hymn_list, container, false
        )
        setHasOptionsMenu(true)
        hymnsForMass.keepSynced(true)
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
        val date = Calendar.getInstance().time
        val formatter = SimpleDateFormat.getDateInstance()
        val formattedDate = formatter.format(date)
        binding.dateSelect.text = formattedDate
        binding.dateSelect.visibility = View.VISIBLE
        skeleton = binding.hymnList.applySkeleton(R.layout.hymn_item_view, 15)
        skeleton.showSkeleton()
        adapter = MassListAdapter(MassListListener { hymnTitle, hymnId ->
            //navigate with args
            NavHostFragment.findNavController(this@MassFragment)
                .navigate(
                    MassFragmentDirections.actionMassFragmentToSingingFragment(
                        hymnTitle,
                        hymnId
                    )
                )

        }, MassListOptionsListener { hymn, hymnView ->
            //handle optionMenu
            val popup = PopupMenu(context, hymnView)
            popup.menuInflater.inflate(R.menu.mass_hymn_option_menu, popup.menu)
            PreferenceManager.getDefaultSharedPreferences(context).apply {
                when(getInt(AuthCodeFragment.USER_ACCESS_LEVEL, 1)){
                    1 -> {
                        popup.menu.removeItem(R.id.removeFromMass)
                    }
                }
            }
            popup.setOnMenuItemClickListener {

                when (it.itemId) {
                    R.id.removeFromMass ->{
                        hymnsForMass.child(formattedDate).child(hymn.hymnId).removeValue()
                    }
                    R.id.favHymnFromMass ->{
                        favourites.child(getUserId()).child(hymn.hymnId).setValue(hymn)
                        Snackbar.make(
                                binding.root,
                                getString(R.string.fav_added),
                                Snackbar.LENGTH_LONG
                            )
                            .setAction("Action", null).show()
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

                val data = mutableListOf<MassHymn>()
                makeNotifyInvisible()
                for (hymnSnapshot in dataSnapshot.children) {
                    val hymnProfile = MassHymn(
                        hymnSnapshot.child("title").value as String,
                        hymnSnapshot.child("hymnNumber").value as String,
                        hymnSnapshot.child("hymnPage").value as String,
                        hymnSnapshot.child("hymnal").value as String,
                        hymnSnapshot.key!!,
                        hymnSnapshot.child("type").value as String
                    )
                    data.add(hymnProfile)
                }
                if(data.size < 1){
                    binding.hymnList.visibility = View.GONE
                    binding.oopsImage.visibility = View.VISIBLE
                    binding.dataStateText.visibility = View.VISIBLE
                    binding.oopsImage.setImageResource(R.drawable.ic_sentiment_dissatisfied_black_24dp)
                    if(isAdded){
                        binding.dataStateText.text = getString(R.string.nothing_here)
                    }
                }

                skeleton.showOriginal()
                adapter.submitList(data)


                binding.hymnList.adapter = adapter

            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }
        hymnsForMass.child(formattedDate).orderByChild("index").addListenerForSingleValueEvent(hymnListListener)

        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.mass_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.chooseDate -> {
                MaterialDialog(context!!).show {
                    datePicker { _, datetime ->
                        val date = datetime.time
                        val setFormattedDate = formatter.format(date)
                        binding.dateSelect.text = setFormattedDate
                        binding.dateSelect.visibility = View.VISIBLE
                        hymnsForMass.child(setFormattedDate).orderByChild("index").addListenerForSingleValueEvent(hymnListListener)
                    }

                }
                true
            }
            else -> false
        }
    }

    private fun makeNotifyInvisible(){
        binding.hymnList.visibility = View.VISIBLE
        binding.oopsImage.visibility = View.GONE
        binding.dataStateText.visibility = View.GONE
    }

    private fun notifyNoInternet(connected: Boolean){
        if(!connected && binding.hymnList.adapter != adapter){
            handler.postDelayed(runnableCode, 6000)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(runnableCode)
        connectedRef.removeEventListener(connectedListener)
    }

    private fun getUserId(): String {
        PreferenceManager.getDefaultSharedPreferences(context).apply {
            return getString(FirstNameFragment.USER_ID, "")!!
        }
    }
}
