package com.bluetoothseclab

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings

class BLEScanner(
    private val onDeviceFound: (ScanResult, String?) -> Unit,
    private val onScanStateChange: (Boolean) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bleScanner: BluetoothLeScanner? = null
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name
            onDeviceFound(result, name)
        }

        override fun onScanFailed(errorCode: Int) {
            stopScan()
        }
    }

    fun startScan() {
        if (isScanning) return
        if (bluetoothAdapter?.isEnabled != true) return

        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        if (bleScanner == null) {
            onScanStateChange(false)
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(null, settings, scanCallback)
        isScanning = true
        onScanStateChange(true)
    }

    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        bleScanner = null
        isScanning = false
        onScanStateChange(false)
    }
}
