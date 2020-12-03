package com.vitalware.succ


import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vitalware.succ.databinding.FragmentFirstNameBinding

/**
 * A simple [Fragment] subclass.
 */
class FirstNameFragment : Fragment() {
    companion object {
        const val USER_NAME = "USERNAME"
        const val USER_ID = "USER_ID"
    }

    private lateinit var binding: FragmentFirstNameBinding
    private var database: DatabaseReference = Firebase.database.getReference("users")
    private var connected = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        if (activity is AppCompatActivity) {
            (activity as AppCompatActivity).supportActionBar!!.hide()
        }

        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_first_name, container, false
        )
        val connectedRef = Firebase.database.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                connected = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: DatabaseError) {
                //something
            }
        })
        binding.firstNameNextBtn.setOnClickListener {
            if (connected) {
                signUpUser()
                NavHostFragment.findNavController(this@FirstNameFragment)
                    .navigate(

                        FirstNameFragmentDirections.actionFirstNameFragmentToAuthCodeFragment()
                    )
            } else {
                Snackbar.make(
                        binding.root,
                        "Please turn on internet connection",
                        Snackbar.LENGTH_SHORT
                    )
                    .setAction("Action", null).show()
            }

        }

        return binding.root
    }

    private fun signUpUser() {
        if (TextUtils.isEmpty(binding.firstNameEdit.text)) {
            binding.firstNameEdit.error =
                this@FirstNameFragment.resources.getString(R.string.error_no_input)
        } else {
            val user = User(binding.firstNameEdit.text.toString())
            val id = database.push().key
            if (id != null) {
                database.child(id).setValue(user)
            }
            PreferenceManager.getDefaultSharedPreferences(context).apply {

                PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
                    putString(USER_ID, id)
                    putString(USER_NAME, binding.firstNameEdit.text.toString())
                    apply()
                }

            }
        }
    }


}
