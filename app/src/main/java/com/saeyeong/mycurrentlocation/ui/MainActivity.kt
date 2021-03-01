package com.saeyeong.mycurrentlocation.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.telephony.*
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.saeyeong.mycurrentlocation.BuildConfig
import com.saeyeong.mycurrentlocation.R
import com.saeyeong.mycurrentlocation.base.BaseActivity
import com.saeyeong.mycurrentlocation.databinding.ActivityMainBinding
import com.saeyeong.mycurrentlocation.hasPermission
import com.saeyeong.mycurrentlocation.model.BaseTransceiverStation
import com.saeyeong.mycurrentlocation.model.Wifi
import com.saeyeong.mycurrentlocation.requestPermissionWithRationale

class MainActivity : BaseActivity() {

    private val binding: ActivityMainBinding by binding(R.layout.activity_main)
    private val adapter by lazy {
        WifiRecyclerViewAdapter()
    }
    private val wifiManager by lazy {
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val telephonyManager by lazy {
        applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    var permissionApproved: Boolean = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val cancellationTokenSource by lazy {
        CancellationTokenSource()
    }

    private val fineLocationRationalSnackbar by lazy {
        Snackbar.make(
            binding.container,
                R.string.string_fine_location_permission_rationale,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.string_ok) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FINE_LOCATION_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure()
            }
        }
    }

    val intentFilter = IntentFilter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        init()
    }

    override fun onStop() {
        super.onStop()
        cancellationTokenSource.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiScanReceiver)
    }

    private fun init() {

        binding.wifiRecyclerView.adapter = adapter

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)

        getCellInfo()?.run {
            val cellInfoString = "$cellID, $telecom, $mcc, $mnc, $lac"
            binding.tvCellInfo.text = cellInfoString
        }

    }

    private fun getCellInfo(): BaseTransceiverStation? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return null
        val networkOperator = telephonyManager.networkOperator

        return telephonyManager.allCellInfo.find{it.isRegistered}?.let {
            when (it) {
                is CellInfoWcdma -> it.cellIdentity.run {
                    BaseTransceiverStation(cid,networkOperator, mcc, mnc, lac)
                }
                is CellInfoGsm -> it.cellIdentity.run {
                    BaseTransceiverStation(cid,networkOperator, mcc, mnc, lac)
                }
                is CellInfoLte -> it.cellIdentity.run {
                    BaseTransceiverStation(ci,networkOperator, mcc, mnc, 0)
                }
                else -> null
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "onRequestPermissionResult()")

        if (requestCode == REQUEST_FINE_LOCATION_PERMISSIONS_REQUEST_CODE) {
            when {
                grantResults.isEmpty() ->
                    Log.d(TAG, "User interaction was cancelled.")

                grantResults[0] == PackageManager.PERMISSION_GRANTED ->
                    Snackbar.make(
                        binding.container,
                        R.string.string_permission_approved_explanation,
                        Snackbar.LENGTH_LONG
                    )
                        .show()

                else -> {
                    Snackbar.make(
                        binding.container,
                        R.string.string_fine_permission_denied_explanation,
                        Snackbar.LENGTH_LONG
                    )
                        .setAction(R.string.string_settings) {
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts(
                                "package",
                                BuildConfig.APPLICATION_ID,
                                null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                        .show()
                }
            }
        }
    }

    fun wifiScanAndLocationRequestOnClick(view: View) {

        permissionApproved =
            applicationContext.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        Log.d(TAG, "wifiScanAndLocationRequestOnClick()")
        Log.d(TAG, "$permissionApproved")

        if (permissionApproved) {
            requestCurrentLocation()
        } else {
            requestPermissionWithRationale(
                Manifest.permission.ACCESS_FINE_LOCATION,
                REQUEST_FINE_LOCATION_PERMISSIONS_REQUEST_CODE,
                fineLocationRationalSnackbar
            )
        }
        wifiManager.startScan()
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation() {
        if (applicationContext.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {

            val currentLocationTask: Task<Location> = fusedLocationClient.getCurrentLocation(
                PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            )

            currentLocationTask.addOnCompleteListener { task: Task<Location> ->
                if (task.isSuccessful && task.result != null) {
                    logOutputToScreen(task.result)
                } else {
                    Log.d(TAG, "Location (failure): ${task.exception}")
                }
            }
        }
    }

    private fun logOutputToScreen(result: Location) {
        binding.run {
            tvNumLatitude.text = "${result.latitude.toInt()}"
            tvNumLongitude.text = "${result.longitude.toInt()}"
            tvNumAltitude.text = "${result.altitude.toInt()}"
            tvNumAccuracy.text = "${result.accuracy.toInt()}"
        }
    }

    private fun scanSuccess() {
        val results = wifiManager.scanResults.toList()
        val wifiList = results.map { data ->
            Wifi(data.SSID, data.BSSID)
        } as MutableList

        adapter.submitList(wifiList)
    }

    private fun scanFailure() {
        val results = wifiManager.scanResults
        Log.d("와이파이", "스캔 실패 $results")
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_FINE_LOCATION_PERMISSIONS_REQUEST_CODE = 34
    }
}