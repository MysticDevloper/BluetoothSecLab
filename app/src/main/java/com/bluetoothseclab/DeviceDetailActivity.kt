package com.bluetoothseclab

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bluetoothseclab.attacks.BleConnectionFlooder
import com.bluetoothseclab.attacks.BleGattFuzzer
import com.bluetoothseclab.attacks.RfcommBufferFlood
import com.bluetoothseclab.attacks.RfcommScanner
import com.bluetoothseclab.databinding.ActivityDeviceDetailBinding
import com.bluetoothseclab.models.AttackResult
import com.bluetoothseclab.models.SecurityIssue
import java.util.concurrent.atomic.AtomicBoolean

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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        val name = intent.getStringExtra("device_name") ?: "Unknown"
        val address = intent.getStringExtra("device_address") ?: ""
        val type = intent.getIntExtra("device_type", BluetoothDevice.DEVICE_TYPE_UNKNOWN)
        isBle = intent.getBooleanExtra("device_is_ble", false)
        currentRssi = intent.getIntExtra("device_rssi", 0)

        if (address.isBlank() || !address.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))) {
            Toast.makeText(this, "Invalid device address", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        device = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        }

        if (device == null) {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        populateDeviceInfo(device!!, name, type, currentRssi)
        populateBLEAdData(device!!)
        setupPairingTest(device!!)
        setupRfcommScan(device!!)
        setupBleFlooder(device!!)
        setupRfcommFlood(device!!)
        setupGattFuzzer(device!!)
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

    private var rfcommScanRunning = AtomicBoolean(false)
    private var bleFloodRunning = AtomicBoolean(false)
    private var rfcommFloodRunning = AtomicBoolean(false)
    private var gattFuzzRunning = AtomicBoolean(false)

    private fun setupRfcommScan(dev: BluetoothDevice) {
        binding.btnScanRfcomm.setOnClickListener {
            if (rfcommScanRunning.get()) {
                Toast.makeText(this, "RFCOMM scan already in progress", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            rfcommScanRunning.set(true)
            binding.btnScanRfcomm.isEnabled = false
            binding.btnScanRfcomm.text = "Scanning..."
            binding.tvRfcommStatus.text = "Initializing..."
            binding.tvRfcommResults.text = ""

            RfcommScanner.scan(
                device = dev,
                context = this,
                onProgress = { msg ->
                    binding.tvRfcommStatus.text = msg
                },
                onPortFound = { port ->
                    val existing = binding.tvRfcommResults.text
                    val emoji = when (port.riskLevel) {
                        AttackResult.Severity.CRITICAL -> "\uD83D\uDEA8"
                        AttackResult.Severity.HIGH -> "\u26A0\uFE0F"
                        AttackResult.Severity.MEDIUM -> "\uD83D\uDD35"
                        AttackResult.Severity.LOW -> "\uD83D\uDFE2"
                        AttackResult.Severity.INFO -> "\u2139\uFE0F"
                    }
                    val hexLine = if (port.rawReceivedHex != null) "\n   HEX: ${port.rawReceivedHex}" else ""
                    val asciiLine = if (port.rawReceivedAscii != null && port.rawReceivedAscii.isNotBlank())
                        "\n   ASC: \"${port.rawReceivedAscii}\"" else ""
                    binding.tvRfcommResults.text = "$existing\n$emoji ${port.serviceName} [${port.riskLevel.name}]$hexLine$asciiLine"
                },
                onComplete = { result ->
                    rfcommScanRunning.set(false)
                    binding.btnScanRfcomm.isEnabled = true
                    binding.btnScanRfcomm.text = "Scan RFCOMM Ports"
                    binding.tvRfcommStatus.text = "Complete (${result.durationMs / 1000}s) — Risk: ${result.riskScore}/10"

                    val resultText = buildString {
                        appendLine("=== RFCOMM Scan Results ===")
                        appendLine("Status: ${result.status.name}")
                        appendLine("Risk Score: ${result.riskScore}/10")
                        appendLine("Duration: ${result.durationMs / 1000}s")
                        appendLine("Strategy: ${result.summary.substringBefore(" |")}")
                        appendLine()
                        if (result.findings.isEmpty()) {
                            appendLine("No open RFCOMM ports detected.")
                            appendLine("Device is not exposing any services via RFCOMM.")
                        } else {
                            appendLine("Open Ports (${result.findings.size}):")
                            for (finding in result.findings) {
                                val icon = when (finding.severity) {
                                    AttackResult.Severity.CRITICAL -> "\uD83D\uDEA8"
                                    AttackResult.Severity.HIGH -> "\u26A0\uFE0F"
                                    AttackResult.Severity.MEDIUM -> "\uD83D\uDD35"
                                    AttackResult.Severity.LOW -> "\uD83D\uDFE2"
                                    AttackResult.Severity.INFO -> "\u2139\uFE0F"
                                }
                                appendLine("$icon ${finding.title}")
                                appendLine("   ${finding.description}")
                                if (finding.remediation != null) {
                                    appendLine("   Fix: ${finding.remediation}")
                                }
                                appendLine()
                            }
                        }
                        appendLine("=== End Report ===")
                    }
                    binding.tvRfcommResults.text = resultText
                }
            )
        }
    }

    private fun showLabWarning(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Lab Environment Only")
            .setMessage(
                "This attack module is for controlled lab testing on devices you own or have " +
                "explicit authorization to test.\n\n" +
                "• All attacks auto-stop after 25-30 seconds\n" +
                "• Do NOT use on production or third-party devices\n" +
                "• Results are for educational/research purposes\n\n" +
                "Proceed?"
            )
            .setPositiveButton("Start Test") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupBleFlooder(dev: BluetoothDevice) {
        binding.btnBleFloodStop.isEnabled = false

        binding.btnBleFloodStart.setOnClickListener {
            if (bleFloodRunning.get()) return@setOnClickListener
            showLabWarning {
                bleFloodRunning.set(true)
                binding.btnBleFloodStart.isEnabled = false
                binding.btnBleFloodStop.isEnabled = true
                binding.tvBleFloodStatus.text = "Initializing..."
                binding.tvBleFloodResults.text = ""

                BleConnectionFlooder.flood(
                    device = dev,
                    context = this,
                    onProgress = { msg -> binding.tvBleFloodStatus.text = msg },
                    onResult = { result ->
                        val summary = buildString {
                            appendLine("=== Connection Slot Test ===")
                            appendLine("Attempted: ${result.attemptedConnections}")
                            appendLine("Connected: ${result.successfulConnections}")
                            appendLine("Failed: ${result.failedConnections}")
                            appendLine("Avg connect time: ${result.avgConnectTimeMs}ms")
                            appendLine("Multi-connection: ${result.targetSupportsMultiple}")
                            appendLine("Slots exhausted: ${result.slotsExhausted}")
                        }
                        binding.tvBleFloodResults.text = summary
                    },
                    onComplete = { result ->
                        bleFloodRunning.set(false)
                        binding.btnBleFloodStart.isEnabled = true
                        binding.btnBleFloodStop.isEnabled = false
                        binding.tvBleFloodStatus.text = "Complete (${result.durationMs / 1000}s) — Risk: ${result.riskScore}/10"

                        val report = buildString {
                            appendLine("=== BLE Connection Flooder Report ===")
                            appendLine("Status: ${result.status.name}")
                            appendLine("Risk Score: ${result.riskScore}/10")
                            appendLine("Duration: ${result.durationMs / 1000}s")
                            appendLine()
                            for (finding in result.findings) {
                                appendLine("[${finding.severity}] ${finding.title}")
                                appendLine("  ${finding.description}")
                                if (finding.cveReference != null) appendLine("  Ref: ${finding.cveReference}")
                                if (finding.remediation != null) appendLine("  Fix: ${finding.remediation}")
                                appendLine()
                            }
                            appendLine(result.summary)
                        }
                        binding.tvBleFloodResults.text = report
                    }
                )
            }
        }

        binding.btnBleFloodStop.setOnClickListener {
            BleConnectionFlooder.stop()
            bleFloodRunning.set(false)
            binding.btnBleFloodStart.isEnabled = true
            binding.btnBleFloodStop.isEnabled = false
            binding.tvBleFloodStatus.text = "Stopped by user"
        }
    }

    private fun setupRfcommFlood(dev: BluetoothDevice) {
        binding.btnRfcommFloodStop.isEnabled = false

        binding.btnRfcommFloodStart.setOnClickListener {
            if (rfcommFloodRunning.get()) return@setOnClickListener
            showLabWarning {
                rfcommFloodRunning.set(true)
                binding.btnRfcommFloodStart.isEnabled = false
                binding.btnRfcommFloodStop.isEnabled = true
                binding.tvRfcommFloodStatus.text = "Initializing..."
                binding.tvRfcommFloodResults.text = ""

                RfcommBufferFlood.flood(
                    device = dev,
                    context = this,
                    onProgress = { msg -> binding.tvRfcommFloodStatus.text = msg },
                    onResult = { result ->
                        val summary = buildString {
                            appendLine("=== RFCOMM Buffer Test ===")
                            appendLine("Channels opened: ${result.channelsOpened}")
                            appendLine("Total sent: ${result.totalBytesSent / 1024}KB")
                            appendLine("Write errors: ${result.writeErrors}")
                            appendLine("Avg write time: ${result.avgWriteTimeMs}ms")
                            appendLine("Buffers exhausted: ${result.buffersExhausted}")
                        }
                        binding.tvRfcommFloodResults.text = summary
                    },
                    onComplete = { result ->
                        rfcommFloodRunning.set(false)
                        binding.btnRfcommFloodStart.isEnabled = true
                        binding.btnRfcommFloodStop.isEnabled = false
                        binding.tvRfcommFloodStatus.text = "Complete (${result.durationMs / 1000}s) — Risk: ${result.riskScore}/10"

                        val report = buildString {
                            appendLine("=== RFCOMM Buffer Flood Report ===")
                            appendLine("Status: ${result.status.name}")
                            appendLine("Risk Score: ${result.riskScore}/10")
                            appendLine("Duration: ${result.durationMs / 1000}s")
                            appendLine()
                            for (finding in result.findings) {
                                appendLine("[${finding.severity}] ${finding.title}")
                                appendLine("  ${finding.description}")
                                if (finding.cveReference != null) appendLine("  Ref: ${finding.cveReference}")
                                if (finding.remediation != null) appendLine("  Fix: ${finding.remediation}")
                                appendLine()
                            }
                            appendLine(result.summary)
                        }
                        binding.tvRfcommFloodResults.text = report
                    }
                )
            }
        }

        binding.btnRfcommFloodStop.setOnClickListener {
            RfcommBufferFlood.stop()
            rfcommFloodRunning.set(false)
            binding.btnRfcommFloodStart.isEnabled = true
            binding.btnRfcommFloodStop.isEnabled = false
            binding.tvRfcommFloodStatus.text = "Stopped by user"
        }
    }

    private fun setupGattFuzzer(dev: BluetoothDevice) {
        binding.btnGattFuzzStop.isEnabled = false

        binding.btnGattFuzzStart.setOnClickListener {
            if (gattFuzzRunning.get()) return@setOnClickListener
            showLabWarning {
                gattFuzzRunning.set(true)
                binding.btnGattFuzzStart.isEnabled = false
                binding.btnGattFuzzStop.isEnabled = true
                binding.tvGattFuzzStatus.text = "Initializing..."
                binding.tvGattFuzzResults.text = ""

                BleGattFuzzer.fuzz(
                    device = dev,
                    context = this,
                    onProgress = { msg -> binding.tvGattFuzzStatus.text = msg },
                    onResult = { result ->
                        val summary = buildString {
                            appendLine("=== GATT Fuzz Results ===")
                            appendLine("Services: ${result.servicesDiscovered}")
                            appendLine("Characteristics: ${result.characteristicsFound}")
                            appendLine("Write attempts: ${result.writeAttempts}")
                            appendLine("Successes: ${result.writeSuccesses}")
                            appendLine("Errors: ${result.writeErrors}")
                            appendLine("Disconnects: ${result.disconnects}")
                            appendLine("Crash detected: ${result.crashDetected}")
                        }
                        binding.tvGattFuzzResults.text = summary
                    },
                    onComplete = { result ->
                        gattFuzzRunning.set(false)
                        binding.btnGattFuzzStart.isEnabled = true
                        binding.btnGattFuzzStop.isEnabled = false
                        binding.tvGattFuzzStatus.text = "Complete (${result.durationMs / 1000}s) — Risk: ${result.riskScore}/10"

                        val report = buildString {
                            appendLine("=== BLE GATT Fuzzer Report ===")
                            appendLine("Status: ${result.status.name}")
                            appendLine("Risk Score: ${result.riskScore}/10")
                            appendLine("Duration: ${result.durationMs / 1000}s")
                            appendLine()
                            for (finding in result.findings) {
                                appendLine("[${finding.severity}] ${finding.title}")
                                appendLine("  ${finding.description}")
                                if (finding.cveReference != null) appendLine("  Ref: ${finding.cveReference}")
                                if (finding.remediation != null) appendLine("  Fix: ${finding.remediation}")
                                appendLine()
                            }
                            appendLine(result.summary)
                        }
                        binding.tvGattFuzzResults.text = report
                    }
                )
            }
        }

        binding.btnGattFuzzStop.setOnClickListener {
            BleGattFuzzer.stop()
            gattFuzzRunning.set(false)
            binding.btnGattFuzzStart.isEnabled = true
            binding.btnGattFuzzStop.isEnabled = false
            binding.tvGattFuzzStatus.text = "Stopped by user"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
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
