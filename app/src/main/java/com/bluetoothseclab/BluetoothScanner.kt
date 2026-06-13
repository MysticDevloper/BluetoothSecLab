package com.bluetoothseclab

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class BluetoothScanner(
    private val context: Context,
    private val onDeviceFound: (BluetoothDevice, Int, String?) -> Unit,
    private val onScanStateChange: (Boolean) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var isScanning = false
    private var shouldContinue = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    if (device != null) {
                        onDeviceFound(device, rssi, name)
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (shouldContinue) {
                        bluetoothAdapter?.startDiscovery()
                    } else {
                        cleanup()
                        isScanning = false
                        onScanStateChange(false)
                    }
                }
            }
        }
    }

    fun startScan() {
        if (isScanning) return
        if (bluetoothAdapter?.isEnabled != true) return

        shouldContinue = true

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)

        bluetoothAdapter?.startDiscovery()
        isScanning = true
        onScanStateChange(true)
    }

    fun stopScan() {
        shouldContinue = false
        bluetoothAdapter?.cancelDiscovery()
        cleanup()
        isScanning = false
        onScanStateChange(false)
    }

    private fun cleanup() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {}
    }
}
