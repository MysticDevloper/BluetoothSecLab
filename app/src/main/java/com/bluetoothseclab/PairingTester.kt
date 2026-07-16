package com.bluetoothseclab

import android.bluetooth.BluetoothDevice
import android.os.Build
import java.lang.reflect.Method

data class PairingResult(
    val pinAttempted: String,
    val success: Boolean,
    val message: String
)

object PairingTester {

    private const val PIN_DELAY_MS = 2000L

    private val commonPins = listOf(
        "0000", "1234", "1111", "0001", "9999",
        "12345", "00000", "11111", "2222", "3333",
        "4444", "5555", "6666", "7777", "8888",
        "1212", "4321", "123456", "000000", "111111",
        "888888", "123123", "654321", "1122", "1313"
    )

    fun testCommonPins(device: BluetoothDevice, callback: (PairingResult) -> Unit) {
        for (pin in commonPins) {
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                callback(PairingResult(pin, true, "Device already bonded"))
                break
            }
            val result = tryPair(device, pin)
            callback(result)
            if (result.success) break
            try {
                Thread.sleep(PIN_DELAY_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun tryPair(device: BluetoothDevice, pin: String): PairingResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PairingResult(pin, false, "PIN testing restricted on Android 12+")
        }

        return try {
            val setPinMethod: Method? = device.javaClass.getMethod("setPin", ByteArray::class.java)
            if (setPinMethod != null) {
                setPinMethod.invoke(device, pin.toByteArray())
                val createBondMethod: Method? = device.javaClass.getMethod("createBond")
                if (createBondMethod != null) {
                    val result = createBondMethod.invoke(device) as Boolean
                    PairingResult(pin, result, if (result) "Bond created with PIN $pin" else "Pairing rejected for PIN $pin")
                } else {
                    PairingResult(pin, false, "createBond not available")
                }
            } else {
                PairingResult(pin, false, "setPin not available (Android 12+ may block)")
            }
        } catch (e: Exception) {
            PairingResult(pin, false, "Error: ${e.message?.take(50)}")
        }
    }

    fun attemptPairing(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return false
        }
        return try {
            val method: Method? = device.javaClass.getMethod("createBond")
            method?.invoke(device) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun removeBond(device: BluetoothDevice): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return false
        }
        return try {
            val method: Method? = device.javaClass.getMethod("removeBond")
            method?.invoke(device) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }
}
