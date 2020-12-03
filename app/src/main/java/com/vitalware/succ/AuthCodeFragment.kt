package com.vitalware.succ


import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vitalware.succ.databinding.FragmentAuthCodeBinding

/**
 * A simple [Fragment] subclass.
 */
class AuthCodeFragment : Fragment() {

    companion object {
        const val USER_ACCESS_LEVEL = "ACCESS_LEVEL"
        const val COMPLETED_ON_BOARDING_PREF_NAME = "COMPLETE"
    }

    private lateinit var binding: FragmentAuthCodeBinding
    private var connected = false
    private var codeFound = false
    private var database: DatabaseReference = Firebase.database.getReference("authCodes")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (activity is AppCompatActivity) {
            (activity as AppCompatActivity).supportActionBar!!.hide()
        }

        binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_auth_code, container, false
        )
        hideKeyboard()
        val connectedRef = Firebase.database.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                connected = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: DatabaseError) {
                //something
            }
        })

        val codeListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (codeSnapshot in dataSnapshot.children) {
                    if (codeSnapshot.child("code").value as String == binding.codeText.text.toString()){
                        auth((codeSnapshot.child("userType").value as Long).toInt())
                        codeFound = true
                    }
                }
                if (codeFound){
                    binding.progressBar2.visibility = View.GONE
                    NavHostFragment.findNavController(this@AuthCodeFragment)
                        .navigate(
                            AuthCodeFragmentDirections.actionAuthCodeFragmentToSingFragment()
                        )
                    markSignUpAsComplete()
                }
                else{
                    binding.progressBar2.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.invalid_code), Toast.LENGTH_LONG).show()
                }


            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                // ...
            }
        }

        binding.submitButton.setOnClickListener{
            if (TextUtils.isEmpty(binding.codeText.text)) {
                binding.codeText.error = getString(R.string.error_no_input)
            }
            else if(connected){
                binding.noCodeButton.visibility = View.GONE
                binding.progressBar2.visibility = View.VISIBLE
                database.addValueEventListener(codeListener)
            }
        }

        binding.noCodeButton.setOnClickListener{
            NavHostFragment.findNavController(this@AuthCodeFragment)
                .navigate(
                    AuthCodeFragmentDirections.actionAuthCodeFragmentToSingFragment()
                )
            markSignUpAsComplete()
        }

        return binding.root
    }

    private fun markSignUpAsComplete() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            putBoolean(COMPLETED_ON_BOARDING_PREF_NAME, true)
            apply()
        }
    }

    private fun auth(accessLevel: Int){
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            putInt(USER_ACCESS_LEVEL, accessLevel)
            apply()
        }
    }

    private fun hideKeyboard(){
        val inputMethodManager = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

}
