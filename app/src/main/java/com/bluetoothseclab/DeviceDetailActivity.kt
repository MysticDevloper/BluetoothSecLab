package com.bluetoothseclab

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bluetoothseclab.databinding.ActivityDeviceDetailBinding
import com.bluetoothseclab.models.SecurityIssue

class DeviceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceDetailBinding
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val handler = Handler(Looper.getMainLooper())

    private var device: BluetoothDevice? = null
    private var isBle = false
    private var currentRssi = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val name = intent.getStringExtra("device_name") ?: "Unknown"
        val address = intent.getStringExtra("device_address") ?: ""
        val type = intent.getIntExtra("device_type", BluetoothDevice.DEVICE_TYPE_UNKNOWN)
        isBle = intent.getBooleanExtra("device_is_ble", false)
        currentRssi = intent.getIntExtra("device_rssi", 0)

        device = bluetoothAdapter?.getRemoteDevice(address)

        if (device != null) {
            populateDeviceInfo(device!!, name, type, currentRssi)
            populateBLEAdData(device!!)
            setupPairingTest(device!!)
        }
    }

    private fun populateDeviceInfo(dev: BluetoothDevice, name: String, type: Int, rssi: Int) {
        val typeStr = when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic (BR/EDR)"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode (BR/EDR + BLE)"
            else -> "Unknown"
        }

        val bondState = dev.bondState
        val bondStr = when (bondState) {
            BluetoothDevice.BOND_NONE -> "Not bonded"
            BluetoothDevice.BOND_BONDING -> "Bonding…"
            BluetoothDevice.BOND_BONDED -> "Bonded"
            else -> "Unknown"
        }

        val deviceClass = DeviceInfoGatherer.getDeviceClassString(dev)
        val majorCode = DeviceInfoGatherer.getMajorClassCode(dev)
        val serviceClass = DeviceInfoGatherer.getServiceClassString(dev)

        val serviceNames = ServiceEnumerator.getServiceNames(dev)

        binding.tvDeviceName.text = name
        binding.tvDeviceAddress.text = "MAC: ${dev.address}  |  Bond: $bondStr"
        binding.tvDeviceType.text = "Type: $typeStr"
        binding.tvDeviceClass.text = "Class: $deviceClass (code: $majorCode)  |  Srv: $serviceClass"
        binding.tvDeviceRssi.text = "RSSI: $rssi dBm"
        binding.tvSignalQuality.text = "Signal: ${signalBar(rssi)}  (${signalQuality(rssi)})"

        if (serviceNames.isNotEmpty()) {
            binding.tvDeviceServices.text = "Profiles (${serviceNames.size}): ${serviceNames.joinToString(", ")}"
        } else {
            binding.tvDeviceServices.text = "Profiles: None detected"
        }

        val issues = VulnerabilityChecker.assess(dev, serviceNames)
        if (issues.isNotEmpty()) {
            binding.tvVulnerabilities.text = issues.joinToString("\n\n") {
                val emoji = when (it.severity) {
                    SecurityIssue.Severity.LOW -> "ℹ️"
                    SecurityIssue.Severity.MEDIUM -> "⚠️"
                    SecurityIssue.Severity.HIGH -> "🔴"
                    SecurityIssue.Severity.CRITICAL -> "🚨"
                }
                "$emoji [${it.severity.name}] ${it.title}\n   ${it.description}" +
                    (if (it.cveReference != null) "\n   Ref: ${it.cveReference}" else "")
            }
        } else {
            binding.tvVulnerabilities.text = "No issues detected"
        }

        binding.btnViewReport.setOnClickListener {
            val reportIntent = Intent(this, SecurityReportActivity::class.java).apply {
                putExtra("report_device_name", name)
                putExtra("report_device_address", dev.address)
                putExtra("report_services", serviceNames.toTypedArray())
                putExtra("report_issue_count", issues.size)
            }
            startActivity(reportIntent)
        }
    }

    private fun populateBLEAdData(@Suppress("UNUSED_PARAMETER") dev: BluetoothDevice) {
        if (!isBle) {
            binding.tvBLEData.text = "BLE advertisement data not available for Classic devices"
            return
        }
        binding.tvBLEData.text = "Waiting for BLE advertisement data...\nRe-scan BLE for this info."
    }

    private fun setupPairingTest(dev: BluetoothDevice) {
        binding.btnTestPairing.setOnClickListener {
            binding.tvPairingResults.text = "Testing common PINs (25 PINs)...\n"
            Thread {
                val startTime = System.currentTimeMillis()
                var count = 0
                PairingTester.testCommonPins(dev) { result ->
                    count++
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    val msg = if (result.success)
                        "✓ PIN ${result.pinAttempted} — PAIRED! (${elapsed}s)"
                    else
                        "✗ PIN ${result.pinAttempted} — ${result.message.take(40)} (${elapsed}s)"
                    handler.post {
                        binding.tvPairingResults.append("\n$msg")
                    }
                }
                handler.post {
                    binding.tvPairingResults.append("\n\nDone. $count PINs tested.")
                }
            }.start()
        }

        binding.btnRemoveBond.setOnClickListener {
            if (dev.bondState != BluetoothDevice.BOND_BONDED) {
                Toast.makeText(this, "Device is not bonded", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val success = PairingTester.removeBond(dev)
            if (success) {
                Toast.makeText(this, "Bond removed successfully", Toast.LENGTH_SHORT).show()
                binding.tvPairingResults.text = "Bond removed. Device may disconnect."
            } else {
                Toast.makeText(this, "Failed to remove bond", Toast.LENGTH_SHORT).show()
                binding.tvPairingResults.text = "Failed to remove bond (may require root or Android 12+ restrictions)"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun signalBar(rssi: Int): String = when {
        rssi >= -50 -> "▂▄▆█ █"
        rssi >= -65 -> "▂▄▆█"
        rssi >= -80 -> "▂▄▆"
        rssi >= -90 -> "▂▄"
        else -> "▂"
    }

    private fun signalQuality(rssi: Int): String = when {
        rssi >= -50 -> "Excellent"
        rssi >= -65 -> "Good"
        rssi >= -80 -> "Fair"
        rssi >= -90 -> "Weak"
        else -> "Very Weak"
    }
}
