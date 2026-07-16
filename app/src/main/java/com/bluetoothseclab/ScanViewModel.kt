package com.bluetoothseclab

import androidx.lifecycle.ViewModel
import com.bluetoothseclab.models.BluetoothDeviceInfo

class ScanViewModel : ViewModel() {
    val classicDevices = mutableListOf<BluetoothDeviceInfo>()
    val bleDevices = mutableListOf<BluetoothDeviceInfo>()

    var isClassicScanning = false
    var isBleScanning = false

    var pendingClassicScan = false
    var pendingBleScan = false

    fun clearClassicDevices() {
        classicDevices.clear()
    }

    fun clearBleDevices() {
        bleDevices.clear()
    }

    fun addClassicDeviceIfAbsent(device: BluetoothDeviceInfo): Boolean {
        if (classicDevices.none { it.address == device.address }) {
            classicDevices.add(device)
            return true
        }
        return false
    }

    fun addBleDeviceIfAbsent(device: BluetoothDeviceInfo): Boolean {
        if (bleDevices.none { it.address == device.address }) {
            bleDevices.add(device)
            return true
        }
        return false
    }
}
