package com.bluetoothseclab.models

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val type: Int = android.bluetooth.BluetoothDevice.DEVICE_TYPE_UNKNOWN,
    val bondState: Int = android.bluetooth.BluetoothDevice.BOND_NONE,
    val isBle: Boolean = false,
    val rssi: Int = 0,
    val deviceClass: String = "Unknown",
    val majorClass: String = "",
    val txPower: Int? = null,
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val advertisedServices: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val securityIssues: List<SecurityIssue> = emptyList(),
    val lastSeen: Long = System.currentTimeMillis(),
    val isConnectable: Boolean = false
) {
    fun typeString(): String = when (type) {
        android.bluetooth.BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic (BR/EDR)"
        android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
        android.bluetooth.BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode"
        else -> "Unknown"
    }

    fun bondString(): String = when (bondState) {
        android.bluetooth.BluetoothDevice.BOND_NONE -> "None"
        android.bluetooth.BluetoothDevice.BOND_BONDING -> "Bonding…"
        android.bluetooth.BluetoothDevice.BOND_BONDED -> "Bonded"
        else -> "Unknown"
    }

    fun rssiBars(): Int = when {
        rssi >= -50 -> 4
        rssi >= -65 -> 3
        rssi >= -80 -> 2
        rssi >= -90 -> 1
        else -> 0
    }

    fun rssiQuality(): String = when {
        rssi >= -50 -> "Excellent"
        rssi >= -65 -> "Good"
        rssi >= -80 -> "Fair"
        rssi >= -90 -> "Weak"
        else -> "Very Weak"
    }
}
