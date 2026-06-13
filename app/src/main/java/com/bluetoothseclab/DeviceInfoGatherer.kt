package com.bluetoothseclab

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.util.SparseArray

object DeviceInfoGatherer {

    fun getDeviceClassString(device: BluetoothDevice): String {
        val btClass = device.bluetoothClass ?: return "Unknown"
        return when (btClass.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video"
            BluetoothClass.Device.Major.COMPUTER -> "Computer"
            BluetoothClass.Device.Major.PHONE -> "Phone"
            BluetoothClass.Device.Major.NETWORKING -> "Networking"
            BluetoothClass.Device.Major.IMAGING -> "Imaging"
            BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
            BluetoothClass.Device.Major.TOY -> "Toy"
            BluetoothClass.Device.Major.HEALTH -> "Health"
            BluetoothClass.Device.Major.WEARABLE -> "Wearable"
            BluetoothClass.Device.Major.MISC -> "Miscellaneous"
            else -> "Unknown"
        }
    }

    fun getMajorClassCode(device: BluetoothDevice): String {
        val btClass = device.bluetoothClass ?: return "N/A"
        return String.format("0x%02X", btClass.majorDeviceClass)
    }

    fun getServiceClassString(device: BluetoothDevice): String {
        val btClass = device.bluetoothClass ?: return "None"
        val services = mutableListOf<String>()
        if (btClass.hasService(BluetoothClass.Service.NETWORKING)) services.add("Networking")
        if (btClass.hasService(BluetoothClass.Service.AUDIO)) services.add("Audio")
        if (btClass.hasService(BluetoothClass.Service.TELEPHONY)) services.add("Telephony")
        if (btClass.hasService(BluetoothClass.Service.INFORMATION)) services.add("Information")
        if (btClass.hasService(BluetoothClass.Service.POSITIONING)) services.add("Positioning")
        if (btClass.hasService(BluetoothClass.Service.OBJECT_TRANSFER)) services.add("Object Transfer")
        if (btClass.hasService(BluetoothClass.Service.CAPTURE)) services.add("Capturing")
        if (btClass.hasService(BluetoothClass.Service.RENDER)) services.add("Rendering")
        if (btClass.hasService(BluetoothClass.Service.LIMITED_DISCOVERABILITY)) services.add("Limited Discoverable")
        return if (services.isEmpty()) "None" else services.joinToString(", ")
    }

    fun parseBLEScanRecord(result: ScanResult): Pair<Map<Int, ByteArray>, List<String>> {
        val record = result.scanRecord ?: return (emptyMap<Int, ByteArray>() to emptyList())
        val raw: SparseArray<ByteArray>? = record.manufacturerSpecificData
        val mfrData = mutableMapOf<Int, ByteArray>()
        if (raw != null) {
            for (i in 0 until raw.size()) {
                val key = raw.keyAt(i)
                mfrData[key] = raw[key]
            }
        }
        val services = record.serviceUuids?.map { it.toString().take(36) + "…" } ?: emptyList()
        return (mfrData to services)
    }

    fun manufacturerIdToName(id: Int): String = when (id) {
        0x004C -> "Apple"
        0x0075 -> "Samsung"
        0x0059 -> "Nordic Semi"
        0x0006 -> "Microsoft"
        0x00E0 -> "Google"
        0x010E -> "Xiaomi"
        0x0060 -> "Logitech"
        0x009E -> "Plantronics"
        0x000A -> "CSR"
        0x0002 -> "Intel"
        0x00D6 -> "Sony"
        0x001D -> "Broadcom"
        0x00F0 -> "Motorola"
        0x0119 -> "Bose"
        0x0126 -> "Huawei"
        0x0363 -> "OnePlus"
        0x013D -> "Nothing"
        0x02E5 -> "JBL"
        else -> "Manufacturer #$id"
    }

    fun getDeviceCategoryIcon(device: BluetoothDevice): Int = when {
        device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO -> android.R.drawable.ic_media_play
        device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER -> android.R.drawable.ic_menu_edit
        device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE -> android.R.drawable.sym_action_call
        device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL -> android.R.drawable.ic_menu_compass
        device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE -> android.R.drawable.ic_menu_gallery
        else -> android.R.drawable.stat_sys_data_bluetooth
    }
}
