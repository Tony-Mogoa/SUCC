package com.vitalware.succ


import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vitalware.succ.SingFragment.Companion.THEME_NAME
import com.vitalware.succ.databinding.FragmentThemeSetBinding

/**
 * A simple [Fragment] subclass.
 */
class ThemeSetFragment : Fragment(), AdapterView.OnItemSelectedListener {
    private lateinit var binding: FragmentThemeSetBinding
    private var lastSpinnerPosition = 0
    private var database: DatabaseReference = Firebase.database.getReference("theme")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_theme_set, container, false
        )

// Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.color_list,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            binding.spinner.adapter = adapter
        }
        binding.spinner.onItemSelectedListener = this
        return binding.root

    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
        // An item was selected. You can retrieve the selected item using
        if(lastSpinnerPosition != pos){
            val itemAtPosition = parent.getItemAtPosition(pos)
            database.setValue(itemAtPosition.toString())
            PreferenceManager.getDefaultSharedPreferences(context).apply {
                if (itemAtPosition.toString() != getString(THEME_NAME, "Violet")) {
                    PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                        putString(THEME_NAME, itemAtPosition.toString())
                        apply()
                    }
                    if(NavHostFragment.findNavController(this@ThemeSetFragment).currentDestination?.id == R.id.themeSetFragment){
                        Snackbar.make(
                                binding.root,
                                this@ThemeSetFragment.resources.getString(R.string.theme_change_text) + " " +itemAtPosition.toString(),
                                5000
                            )
                            .setAction("UPDATE") {
                                if (!isDetached){
                                    val intent = Intent(context, MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(intent)
                                    activity!!.finish()
                                }
                            }.show()
                    }
                }

            }

        }
        lastSpinnerPosition = pos


    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }


}
