package com.bluetoothseclab

import android.bluetooth.BluetoothDevice
import java.lang.reflect.Method

object ServiceEnumerator {

    fun getServices(device: BluetoothDevice): List<String> {
        val services = mutableListOf<String>()
        try {
            val uuids = device.uuids
            if (uuids != null) {
                for (uuid in uuids) {
                    services.add(uuid.toString())
                }
            } else {
                val fetchMethod: Method = device.javaClass.getMethod("fetchUuidsWithSdp")
                fetchMethod.invoke(device)
            }
        } catch (_: Exception) {}
        return services
    }

    private val knownProfiles = mapOf(
        "0000110" to "SPP (Serial Port Profile)",
        "00001115" to "PAN (Personal Area Network)",
        "0000110A" to "A2DP (Audio Sink)",
        "0000110B" to "A2DP (Audio Source)",
        "0000110C" to "AVRCP (Remote Control)",
        "0000110D" to "HFP (Hands-Free)",
        "0000110E" to "HFP AG (Audio Gateway)",
        "00001108" to "HSP (Headset)",
        "00001112" to "HID (Human Interface Device)",
        "00001124" to "HID Service",
        "00001116" to "OBEX Push",
        "00001105" to "OBEX Sync",
        "00001119" to "DUN (Dial-Up Networking)",
        "00001101" to "Serial Port",
        "00001103" to "DIP (Device ID Profile)",
        "00001800" to "GAP (Generic Access)",
        "00001801" to "GATT (Generic Attribute)",
        "0000180A" to "Device Information",
        "0000180F" to "Battery Service",
        "00001812" to "Human Interface Device (BLE)",
        "00010000" to "LE Secure Connections"
    )

    fun resolveProfileName(uuid: String): String {
        for ((prefix, name) in knownProfiles) {
            if (uuid.startsWith(prefix, ignoreCase = true))
                return name
        }
        return uuid.take(36) + "…"
    }

    fun getServiceNames(device: BluetoothDevice): List<String> {
        val raw = getServices(device)
        return raw.map { resolveProfileName(it) }
    }
}
