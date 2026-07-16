package com.bluetoothseclab

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.bluetoothseclab.databinding.ActivityMainBinding
import com.bluetoothseclab.models.BluetoothDeviceInfo
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ScanViewModel

    private var classicScanner: BluetoothScanner? = null
    private var bleScanner: BLEScanner? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val BLUETOOTH_SETTINGS_CODE = 200
        private const val LOCATION_SETTINGS_CODE = 300
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ScanViewModel::class.java]

        setSupportActionBar(binding.toolbar)

        checkPrerequisites()

        val adapter = DevicePagerAdapter(viewModel.classicDevices, viewModel.bleDevices) { device ->
            val intent = Intent(this, DeviceDetailActivity::class.java).apply {
                putExtra("device_name", device.name)
                putExtra("device_address", device.address)
                putExtra("device_type", device.type)
                putExtra("device_is_ble", device.isBle)
                putExtra("device_rssi", device.rssi)
            }
            startActivity(intent)
        }

        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Classic (${viewModel.classicDevices.size})" else "BLE (${viewModel.bleDevices.size})"
        }.attach()

        binding.btnScanClassic.setOnClickListener {
            if (!PermissionsHelper.allGranted(this)) {
                ActivityCompat.requestPermissions(this, PermissionsHelper.getRequiredPermissions(), PERMISSION_REQUEST_CODE)
                viewModel.pendingClassicScan = true
                return@setOnClickListener
            }
            if (!PermissionsHelper.isLocationEnabled(this)) {
                showLocationDialog()
                viewModel.pendingClassicScan = true
                return@setOnClickListener
            }
            if (!PermissionsHelper.isBluetoothEnabled()) {
                showBluetoothDialog()
                viewModel.pendingClassicScan = true
                return@setOnClickListener
            }
            if (viewModel.isClassicScanning) stopClassicScan() else startClassicScan()
        }

        binding.btnScanBLE.setOnClickListener {
            if (!PermissionsHelper.allGranted(this)) {
                ActivityCompat.requestPermissions(this, PermissionsHelper.getRequiredPermissions(), PERMISSION_REQUEST_CODE)
                viewModel.pendingBleScan = true
                return@setOnClickListener
            }
            if (!PermissionsHelper.isLocationEnabled(this)) {
                showLocationDialog()
                viewModel.pendingBleScan = true
                return@setOnClickListener
            }
            if (!PermissionsHelper.isBluetoothEnabled()) {
                showBluetoothDialog()
                viewModel.pendingBleScan = true
                return@setOnClickListener
            }
            if (viewModel.isBleScanning) stopBleScan() else startBleScan()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_about -> showAboutDialog()
                R.id.action_disclaimer -> showDisclaimerDialog()
            }
            true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                retryPendingScan()
            } else {
                Snackbar.make(binding.root, "Bluetooth + Location permissions required", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Grant") {
                        ActivityCompat.requestPermissions(this, PermissionsHelper.getRequiredPermissions(), PERMISSION_REQUEST_CODE)
                    }
                    .show()
            }
        }
    }

    private fun checkPrerequisites() {
        if (!PermissionsHelper.allGranted(this)) {
            ActivityCompat.requestPermissions(this, PermissionsHelper.getRequiredPermissions(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun showBluetoothDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Bluetooth")
            .setMessage("This app requires Bluetooth to be enabled.")
            .setPositiveButton("Settings") { _, _ ->
                startActivityForResult(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), BLUETOOTH_SETTINGS_CODE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Location")
            .setMessage("Android requires Location to be ON for Bluetooth scanning (API < 31). Your location is NOT tracked or stored.")
            .setPositiveButton("Settings") { _, _ ->
                startActivityForResult(PermissionsHelper.getLocationSettingsIntent(), LOCATION_SETTINGS_CODE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            BLUETOOTH_SETTINGS_CODE -> retryPendingScan()
            LOCATION_SETTINGS_CODE -> retryPendingScan()
        }
    }

    private fun retryPendingScan() {
        if (viewModel.pendingClassicScan && PermissionsHelper.isBluetoothEnabled() && PermissionsHelper.isLocationEnabled(this)) {
            viewModel.pendingClassicScan = false
            startClassicScan()
        }
        if (viewModel.pendingBleScan && PermissionsHelper.isBluetoothEnabled() && PermissionsHelper.isLocationEnabled(this)) {
            viewModel.pendingBleScan = false
            startBleScan()
        }
    }

    private fun startClassicScan() {
        viewModel.clearClassicDevices()
        binding.tvStatus.text = "Scanning Classic (auto-refresh until stopped)…"
        binding.progressBar.visibility = android.view.View.VISIBLE

        classicScanner = BluetoothScanner(
            context = this,
            onDeviceFound = { device, rssi, name ->
                val displayName = name ?: device.name ?: "Unknown"
                val deviceInfo = BluetoothDeviceInfo(
                    name = displayName,
                    address = device.address,
                    type = device.type,
                    bondState = device.bondState,
                    isBle = false,
                    rssi = rssi
                )
                if (viewModel.addClassicDeviceIfAbsent(deviceInfo)) {
                    (binding.viewPager.adapter as? DevicePagerAdapter)?.notifyPageItemInserted(0)
                    updateTabTitles()
                }
            },
            onScanStateChange = { scanning ->
                viewModel.isClassicScanning = scanning
                binding.btnScanClassic.text = if (scanning) "⏹ Stop Classic" else "▶ Scan Classic"
                binding.progressBar.visibility = if (scanning || viewModel.isBleScanning) android.view.View.VISIBLE else android.view.View.GONE
                binding.tvStatus.text = if (scanning) "Scanning Classic (${viewModel.classicDevices.size} found)…" else "Classic scan stopped — ${viewModel.classicDevices.size} devices"
            }
        )
        classicScanner?.startScan()
    }

    private fun stopClassicScan() {
        classicScanner?.stopScan()
        classicScanner = null
    }

    private fun startBleScan() {
        viewModel.clearBleDevices()
        binding.tvStatus.text = "Scanning BLE (continuous)…"
        binding.progressBar.visibility = android.view.View.VISIBLE

        bleScanner = BLEScanner(
            onDeviceFound = { result: ScanResult, name ->
                val device = result.device
                val displayName = name ?: device.name ?: "Unknown"
                val deviceInfo = BluetoothDeviceInfo(
                    name = displayName,
                    address = device.address,
                    type = device.type,
                    bondState = device.bondState,
                    isBle = true,
                    rssi = result.rssi
                )
                if (viewModel.addBleDeviceIfAbsent(deviceInfo)) {
                    (binding.viewPager.adapter as? DevicePagerAdapter)?.notifyPageItemInserted(1)
                    updateTabTitles()
                }
            },
            onScanStateChange = { scanning ->
                viewModel.isBleScanning = scanning
                binding.btnScanBLE.text = if (scanning) "⏹ Stop BLE" else "▶ Scan BLE"
                binding.progressBar.visibility = if (scanning || viewModel.isClassicScanning) android.view.View.VISIBLE else android.view.View.GONE
                binding.tvStatus.text = if (scanning) "Scanning BLE (${viewModel.bleDevices.size} found)…" else "BLE scan stopped — ${viewModel.bleDevices.size} devices"
            }
        )
        bleScanner?.startScan()
    }

    private fun stopBleScan() {
        bleScanner?.stopScan()
        bleScanner = null
    }

    private fun updateTabTitles() {
        val tabCount = binding.tabLayout.tabCount
        if (tabCount >= 1) binding.tabLayout.getTabAt(0)?.text = "Classic (${viewModel.classicDevices.size})"
        if (tabCount >= 2) binding.tabLayout.getTabAt(1)?.text = "BLE (${viewModel.bleDevices.size})"
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("BT Security Lab v1.1")
            .setMessage("A Bluetooth security assessment tool for authorized testing and research.\n\nFor educational and research purposes only.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDisclaimerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Legal Notice")
            .setMessage("This tool is for AUTHORIZED security testing only.\n\nUse only on devices you own or have explicit written permission to test. Unauthorized use may violate laws including CFAA, GDPR, and local regulations.\n\nThe developer assumes no liability for misuse.")
            .setPositiveButton("I Understand", null)
            .show()
    }

    override fun onDestroy() {
        stopClassicScan()
        stopBleScan()
        super.onDestroy()
    }
}

