@file:Suppress("DEPRECATION")

package com.vitalware.succ

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.preference.PreferenceManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.vitalware.succ.SingFragment.Companion.THEME_NAME
import com.vitalware.succ.databinding.ActivityMainBinding


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_WIFI_PERMISSION = 200
    }
    private var macAddresses: DatabaseReference = Firebase.database.getReference("macAddresses")
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private val desiredMacAddresses =  mutableListOf<String>()
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.getDefaultSharedPreferences(applicationContext).apply {
            val themeResource = when (getString(THEME_NAME, "Violet")) {
                "Violet" -> R.style.AppTheme_Violet
                "Red" -> R.style.AppTheme_Red
                "Blue" -> R.style.AppTheme_Blue
                "Rose" -> R.style.AppTheme_Rose
                "Green" -> R.style.AppTheme_Green
                "Black" -> R.style.AppTheme_Black
                else -> R.style.AppTheme_White
            }
            setTheme(themeResource)

        }
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        getPermissionToAccessWifiInfo()
        checkWifiState()
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)

        val postListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Get Post object and use the values to update the UI
               for(macAddr in dataSnapshot.children){
                   desiredMacAddresses.add(macAddr.value as String)
               }
                registerReceiver(receiver, intentFilter)
                // ...
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message

                // ...
            }
        }
        macAddresses.addValueEventListener(postListener)
        drawerLayout = binding.drawerLayout
        //addMacs()
        val navController = this.findNavController(R.id.myNavHostFragment)
        NavigationUI.setupActionBarWithNavController(this, navController, drawerLayout)
        NavigationUI.setupWithNavController(binding.navView, navController)
        PreferenceManager.getDefaultSharedPreferences(applicationContext).apply {
            when (getInt(AuthCodeFragment.USER_ACCESS_LEVEL, 1)) {
                1 -> {
                    binding.navView.menu.removeItem(R.id.themeSetFragment)
                    binding.navView.menu.removeItem(R.id.hymnPackFragment)
                    binding.navView.menu.removeItem(R.id.newHymnFragment)
                }
                2 -> {
                    binding.navView.menu.removeItem(R.id.themeSetFragment)
                    binding.navView.menu.removeItem(R.id.hymnPackFragment)
                    binding.navView.menu.removeItem(R.id.newHymnFragment)
                }
            }
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = this.findNavController(R.id.myNavHostFragment)
        return NavigationUI.navigateUp(navController, drawerLayout)
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION == action) {
                val state =
                    intent.getParcelableExtra<SupplicantState>(WifiManager.EXTRA_NEW_STATE)
                if (SupplicantState.isValidState(state)
                    && state == SupplicantState.COMPLETED
                ) {
                    checkConnectedToDesiredWifi()
                }
            }
        }

        /** Detect you are connected to a specific network.  */
        private fun checkConnectedToDesiredWifi() {
            var connected = false
            //val desiredMacAddresses = listOf("ec:89:14:09:f9:d5")
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifi = wifiManager.connectionInfo
            if (wifi != null) {
                // get current router Mac address
                val bssid = wifi.bssid
                //Log.i("testd", bssid)
                for (macAddr in desiredMacAddresses){
                    connected = macAddr == (bssid)
                }
                if (!connected) {
                    Builder(this@MainActivity)
                        .setTitle("App will now exit")
                        .setMessage("You can't use this app if you are not connected to the Strathmore WiFi network.")
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            finish()
                        }
                        .setIcon(R.drawable.ic_error_outline_black_24dp)
                        .show()
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun getPermissionToAccessWifiInfo() {

        if (ContextCompat.checkSelfPermission(
                applicationContext!!,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            val permissionArray = arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            requestPermissions(permissionArray, REQUEST_WIFI_PERMISSION)

        } else {
            Log.i("testd", "granted")
        }
    }

    // Callback with the request from calling requestPermissions(...)
    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        // Make sure it's our original READ_CONTACTS request
        if (requestCode == REQUEST_WIFI_PERMISSION) {
            if (grantResults.size == 1 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {

                //Toast.makeText(this, "Record Audio permission granted", Toast.LENGTH_SHORT).show();

            } else {
                Toast.makeText(
                    applicationContext,
                    "You must give permissions to use this app. App is exiting.",
                    Toast.LENGTH_SHORT
                ).show()
                ActivityCompat.finishAffinity(this)
            }
        }

    }

    private fun checkWifiState() {
        val connManager: ConnectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo =
            connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        if (!networkInfo!!.isConnected) {
            Builder(this@MainActivity)
                .setTitle("App will now exit")
                .setMessage("You can't use this app if you are not connected to the Strathmore WiFi network.")
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    finish()
                }
                .setIcon(R.drawable.ic_error_outline_black_24dp)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't forget to unregister the ACTION_FOUND receiver.
        try {
            unregisterReceiver(receiver)
        }
        catch (e:Exception){

        }
    }

}
