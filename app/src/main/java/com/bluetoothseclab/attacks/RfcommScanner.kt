package com.bluetoothseclab.attacks

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.bluetoothseclab.models.AttackResult
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

data class RfcommPort(
    val channel: Int?,
    val uuid: UUID?,
    val serviceName: String,
    val requiresAuth: Boolean?,
    val isOpen: Boolean,
    val riskLevel: AttackResult.Severity,
    val rawSentHex: String? = null,
    val rawReceivedHex: String? = null,
    val rawReceivedAscii: String? = null,
    val interactionLog: List<String> = emptyList()
)

object RfcommScanner {

    data class ProfileInfo(val name: String, val risk: AttackResult.Severity)

    private val KNOWN_UUIDS = mapOf(
        "00001101-0000-1000-8000-00805f9b34fb" to ProfileInfo("SPP (Serial Port)", AttackResult.Severity.HIGH),
        "00001103-0000-1000-8000-00805f9b34fb" to ProfileInfo("DUN (Dial-Up Networking)", AttackResult.Severity.CRITICAL),
        "00001104-0000-1000-8000-00805f9b34fb" to ProfileInfo("FAX", AttackResult.Severity.HIGH),
        "00001105-0000-1000-8000-00805f9b34fb" to ProfileInfo("OBEX Object Push", AttackResult.Severity.CRITICAL),
        "00001106-0000-1000-8000-00805f9b34fb" to ProfileInfo("OBEX File Transfer", AttackResult.Severity.CRITICAL),
        "00001107-0000-1000-8000-00805f9b34fb" to ProfileInfo("OBEX Sync", AttackResult.Severity.HIGH),
        "00001108-0000-1000-8000-00805f9b34fb" to ProfileInfo("HSP (Headset)", AttackResult.Severity.MEDIUM),
        "00001109-0000-1000-8000-00805f9b34fb" to ProfileInfo("HSP-AG", AttackResult.Severity.MEDIUM),
        "0000110a-0000-1000-8000-00805f9b34fb" to ProfileInfo("A2DP Source", AttackResult.Severity.LOW),
        "0000110b-0000-1000-8000-00805f9b34fb" to ProfileInfo("A2DP Sink", AttackResult.Severity.LOW),
        "0000110c-0000-1000-8000-00805f9b34fb" to ProfileInfo("AVRCP Target", AttackResult.Severity.LOW),
        "0000110d-0000-1000-8000-00805f9b34fb" to ProfileInfo("AVRCP Controller", AttackResult.Severity.LOW),
        "0000110e-0000-1000-8000-00805f9b34fb" to ProfileInfo("AVRCP", AttackResult.Severity.MEDIUM),
        "0000110f-0000-1000-8000-00805f9b34fb" to ProfileInfo("HFP (Hands-Free)", AttackResult.Severity.MEDIUM),
        "00001110-0000-1000-8000-00805f9b34fb" to ProfileInfo("HFP-AG", AttackResult.Severity.MEDIUM),
        "00001112-0000-1000-8000-00805f9b34fb" to ProfileInfo("HID (Boot Mode)", AttackResult.Severity.HIGH),
        "00001113-0000-1000-8000-00805f9b34fb" to ProfileInfo("HID Service", AttackResult.Severity.HIGH),
        "00001114-0000-1000-8000-00805f9b34fb" to ProfileInfo("HID (Report)", AttackResult.Severity.HIGH),
        "00001115-0000-1000-8000-00805f9b34fb" to ProfileInfo("PAN (Personal Area Network)", AttackResult.Severity.CRITICAL),
        "00001116-0000-1000-8000-00805f9b34fb" to ProfileInfo("PANU", AttackResult.Severity.HIGH),
        "00001117-0000-1000-8000-00805f9b34fb" to ProfileInfo("NAP (Network Access)", AttackResult.Severity.CRITICAL),
        "00001118-0000-1000-8000-00805f9b34fb" to ProfileInfo("GN (Group Network)", AttackResult.Severity.HIGH),
        "00001119-0000-1000-8000-00805f9b34fb" to ProfileInfo("BIP (Basic Imaging)", AttackResult.Severity.MEDIUM),
        "0000111e-0000-1000-8000-00805f9b34fb" to ProfileInfo("HCRP (Hardcopy Cable)", AttackResult.Severity.MEDIUM),
        "00001124-0000-1000-8000-00805f9b34fb" to ProfileInfo("HOGP (HID over GATT)", AttackResult.Severity.HIGH),
        "0000112d-0000-1000-8000-00805f9b34fb" to ProfileInfo("SAP (SIM Access)", AttackResult.Severity.CRITICAL),
        "0000112e-0000-1000-8000-00805f9b34fb" to ProfileInfo("SAP Client", AttackResult.Severity.CRITICAL),
        "00001130-0000-1000-8000-00805f9b34fb" to ProfileInfo("PBAP (Phone Book)", AttackResult.Severity.HIGH),
        "00001132-0000-1000-8000-00805f9b34fb" to ProfileInfo("MAP (Message Access)", AttackResult.Severity.CRITICAL),
        "00001133-0000-1000-8000-00805f9b34fb" to ProfileInfo("MAP MSE", AttackResult.Severity.CRITICAL),
        "00001134-0000-1000-8000-00805f9b34fb" to ProfileInfo("MAP MCE", AttackResult.Severity.HIGH),
        "00001135-0000-1000-8000-00805f9b34fb" to ProfileInfo("GNSS (GPS)", AttackResult.Severity.MEDIUM)
    )

    private val MAX_CHANNELS = 30
    private val CONNECT_TIMEOUT_MS = 4000L
    private val SDP_WAIT_MS = 3000L

    fun scan(
        device: BluetoothDevice,
        context: Context,
        onProgress: (String) -> Unit,
        onPortFound: (RfcommPort) -> Unit,
        onComplete: (AttackResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val findings = mutableListOf<AttackResult.Finding>()
        val allPorts = mutableListOf<RfcommPort>()
        val handler = Handler(Looper.getMainLooper())
        val executor = Executors.newFixedThreadPool(4)

        Thread {
            var usedStrategy = "UUID Probe"
            try {
                handler.post { onProgress("[SDP] Querying service records...") }
                val sdpUuids = fetchUuidsViaSdp(device, context)

                val probeUuids = getProbeUuids()
                val probeSet = probeUuids.map { it.first.lowercase() }.toSet()
                val combined = (sdpUuids + probeUuids.filter { (uuid, _) ->
                    uuid.lowercase() in probeSet
                }).distinctBy { it.first.lowercase() }

                handler.post { onProgress("[UUID] Probing ${combined.size} services...") }

                for ((uuidStr, profile) in combined) {
                    val uuid = UUID.fromString(uuidStr)
                    handler.post { onProgress("[UUID] Testing ${profile.name}...") }
                    val port = testUuidWithProbe(device, uuid, profile)
                    if (port != null) {
                        allPorts.add(port)
                        handler.post { onPortFound(port) }
                    }
                }

                handler.post { onProgress("[RFCOMM] Scanning channels 1-$MAX_CHANNELS...") }
                usedStrategy = "UUID Probe + Channel Scan"

                for (channel in 1..MAX_CHANNELS) {
                    val ch = channel
                    handler.post { onProgress("[RFCOMM] Channel $ch/$MAX_CHANNELS...") }
                    val future = executor.submit(Callable<RfcommPort?> {
                        probeRawChannel(device, ch)
                    })
                    try {
                        val port = future.get(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        if (port != null) {
                            allPorts.add(port)
                            handler.post { onPortFound(port) }
                        }
                    } catch (_: TimeoutException) {
                    } catch (_: Exception) {
                    } finally {
                        future.cancel(true)
                    }
                }

                val criticalPorts = allPorts.count { it.riskLevel == AttackResult.Severity.CRITICAL }
                val highPorts = allPorts.count { it.riskLevel == AttackResult.Severity.HIGH }

                for (port in allPorts) {
                    val hasResponse = port.rawReceivedHex != null && port.rawReceivedHex != ""
                    val evidence = if (hasResponse) " — device RESPONDED with ${port.rawReceivedHex?.length?.div(3)} bytes" else ""
                    findings.add(
                        AttackResult.Finding(
                            title = "Open: ${port.serviceName}",
                            description = buildString {
                                append("RFCOMM service accessible")
                                if (port.channel != null) append(" on channel ${port.channel}")
                                if (port.uuid != null) append(" via UUID")
                                if (port.requiresAuth == false) append(" — NO AUTH")
                                append(evidence)
                                if (port.rawReceivedAscii != null && port.rawReceivedAscii.isNotBlank()) {
                                    append(" | response: \"${port.rawReceivedAscii.take(60)}\"")
                                }
                            },
                            severity = port.riskLevel,
                            remediation = when (port.riskLevel) {
                                AttackResult.Severity.CRITICAL -> "Disable or implement strong authentication + encryption"
                                AttackResult.Severity.HIGH -> "Restrict with secure pairing, disable if unused"
                                AttackResult.Severity.MEDIUM -> "Review exposure, ensure bonding required"
                                else -> "Informational"
                            }
                        )
                    )
                }

                val riskScore = calculateRiskScore(allPorts)
                val portSummary = allPorts.map { it.serviceName }.distinct().let { names ->
                    if (names.isEmpty()) "none"
                    else names.joinToString(", ")
                }

                handler.post {
                    onComplete(
                        AttackResult(
                            moduleName = "RFCOMM Scanner",
                            status = AttackResult.Status.SUCCESS,
                            findings = findings,
                            summary = "$usedStrategy | Open ports (${allPorts.size}): $portSummary" +
                                    if (criticalPorts > 0) " | $criticalPorts CRITICAL" else "" +
                                    if (highPorts > 0) " | $highPorts HIGH" else "",
                            riskScore = riskScore,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    )
                }

            } catch (e: Exception) {
                handler.post {
                    onComplete(
                        AttackResult(
                            moduleName = "RFCOMM Scanner",
                            status = AttackResult.Status.FAILED,
                            findings = listOf(
                                AttackResult.Finding(
                                    title = "Scan Error",
                                    description = "${e.message ?: "Unknown error"}",
                                    severity = AttackResult.Severity.INFO
                                )
                            ),
                            summary = "RFCOMM scan failed: ${e.message}",
                            riskScore = 0f,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    )
                }
            } finally {
                executor.shutdownNow()
            }
        }.start()
    }

    private fun fetchUuidsViaSdp(
        device: BluetoothDevice,
        context: Context
    ): List<Pair<String, ProfileInfo>> {
        val result = mutableListOf<Pair<String, ProfileInfo>>()
        val latch = CountDownLatch(1)
        var receiverRegistered = false
        val receiver = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(ctx: Context, intent: Intent) {
                if (BluetoothDevice.ACTION_UUID == intent.action) {
                    val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (dev?.address == device.address) {
                        val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                        if (uuids != null) {
                            for (uuidObj in uuids) {
                                val uuidStr = uuidObj.toString().trim().lowercase()
                                val profile = KNOWN_UUIDS[uuidStr]
                                result.add(uuidStr to (profile ?: ProfileInfo("Unknown Profile", AttackResult.Severity.MEDIUM)))
                            }
                        }
                        latch.countDown()
                    }
                }
            }
        }

        try {
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_UUID))
            receiverRegistered = true
        } catch (_: Exception) {
            return result
        }
        try {
            device.fetchUuidsWithSdp()
            latch.await(SDP_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        } finally {
            if (receiverRegistered) {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
        }
        return result
    }

    /**
     * Connect to a UUID service and attempt protocol handshake.
     * Shows evidence of real connection via raw bytes exchanged.
     */
    private fun testUuidWithProbe(
        device: BluetoothDevice,
        uuid: UUID,
        profile: ProfileInfo
    ): RfcommPort? {
        val log = mutableListOf<String>()
        var sentBytes: ByteArray? = null
        var receivedBytes: ByteArray? = null

        fun probe(socket: BluetoothSocket, name: String): Boolean {
            return try {
                log.add("[$name] Connected to ${profile.name}")
                val os: OutputStream = socket.outputStream
                val istream: InputStream = socket.inputStream

                // Send probe appropriate for service type
                val probeData = createServiceProbe(profile.name)
                if (probeData != null) {
                    os.write(probeData)
                    os.flush()
                    sentBytes = probeData
                    log.add("[$name] Sent probe: ${bytesToHex(probeData)}")
                }

                // Read response
                val buf = ByteArray(256)
                try {
                    val bytesRead = istream.read(buf)
                    if (bytesRead > 0) {
                        receivedBytes = buf.copyOf(bytesRead)
                        log.add("[$name] Received ${bytesRead} bytes: ${bytesToHex(receivedBytes!!)}")
                        val ascii = bytesToAscii(receivedBytes!!)
                        if (ascii.isNotBlank()) log.add("[$name] ASCII: \"$ascii\"")
                    } else {
                        log.add("[$name] Connected but no initial response (service may wait for input)")
                    }
                } catch (_: IOException) {
                    log.add("[$name] Connected — no data response (timeout)")
                }
                true
            } catch (e: Exception) {
                log.add("[$name] Failed: ${e.message?.take(60)}")
                false
            }
        }

        var ok = false
        try {
            val socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            ok = probe(socket, "Secure")
            try { socket.close() } catch (_: Exception) {}
        } catch (_: IOException) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                try {
                    val method = device.javaClass.getDeclaredMethod(
                        "createInsecureRfcommSocketToServiceRecord", UUID::class.java
                    )
                    method.isAccessible = true
                    val socket = method.invoke(device, uuid) as BluetoothSocket
                    socket.connect()
                    ok = probe(socket, "Insecure")
                    try { socket.close() } catch (_: Exception) {}
                } catch (_: Exception) {}
            }
        }

        if (!ok) return null

        val hasEvidence = receivedBytes != null && receivedBytes!!.isNotEmpty()
        val serviceName = if (hasEvidence) {
            val identified = identifyFromResponse(profile.name, receivedBytes)
            if (identified != null) identified else profile.name
        } else {
            "${profile.name} (no response)"
        }

        return RfcommPort(
            channel = null,
            uuid = uuid,
            serviceName = serviceName,
            requiresAuth = null,
            isOpen = true,
            riskLevel = profile.risk,
            rawSentHex = if (sentBytes != null) bytesToHex(sentBytes!!) else null,
            rawReceivedHex = if (receivedBytes != null) bytesToHex(receivedBytes!!) else null,
            rawReceivedAscii = if (receivedBytes != null) bytesToAscii(receivedBytes!!) else null,
            interactionLog = log
        )
    }

    /**
     * Try raw RFCOMM channel, if connected — send probes and read response.
     */
    private fun probeRawChannel(device: BluetoothDevice, channel: Int): RfcommPort? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return null
        }

        val log = mutableListOf<String>()
        var sentBytes: ByteArray? = null
        var receivedBytes: ByteArray? = null
        var socket: BluetoothSocket? = null

        return try {
            val createMethod = try {
                device.javaClass.getDeclaredMethod("createRfcommSocket", Int::class.java)
            } catch (_: NoSuchMethodException) {
                device.javaClass.getDeclaredMethod("createInsecureRfcommSocket", Int::class.java)
            }
            createMethod.isAccessible = true
            socket = createMethod.invoke(device, channel) as BluetoothSocket
            socket.connect()
            log.add("[CH $channel] Socket connected")

            val os: OutputStream = socket.outputStream
            val istream: InputStream = socket.inputStream

            // Try OBEX Connect probe first (common protocol)
            val obexProbe = byteArrayOf(0x80.toByte(), 0x00, 0x10.toByte(), 0x00, 0x10.toByte(), 0x00, 0x00, 0x00)
            try {
                os.write(obexProbe)
                os.flush()
                sentBytes = obexProbe
                log.add("[CH $channel] Sent: ${bytesToHex(obexProbe)} (OBEX Connect)")
            } catch (_: Exception) {}

            val buf = ByteArray(256)
            try {
                val bytesRead = istream.read(buf)
                if (bytesRead > 0) {
                    receivedBytes = buf.copyOf(bytesRead)
                    log.add("[CH $channel] Recv: ${bytesToHex(receivedBytes!!)}")
                    val ascii = bytesToAscii(receivedBytes!!)
                    if (ascii.isNotBlank()) log.add("[CH $channel] ASCII: \"$ascii\"")
                }
            } catch (_: IOException) {
                // No response — try AT probe
                try {
                    val atProbe = "AT\r\n".toByteArray()
                    os.write(atProbe)
                    os.flush()
                    sentBytes = atProbe
                    log.add("[CH $channel] Sent: ${bytesToHex(atProbe)} (AT)")
                    try {
                        val n = istream.read(buf)
                        if (n > 0) {
                            receivedBytes = buf.copyOf(n)
                            log.add("[CH $channel] Recv: ${bytesToHex(receivedBytes!!)}")
                        }
                    } catch (_: IOException) {
                        log.add("[CH $channel] No response to AT probe")
                    }
                } catch (_: Exception) {
                    log.add("[CH $channel] Could not send probe")
                }
            }

            val serviceName = identifyFromBytes(receivedBytes, channel)
            val profile = KNOWN_UUIDS.values.find { p ->
                serviceName.contains(p.name.split(" ").first(), ignoreCase = true)
            }

            log.add("[CH $channel] Identified as: $serviceName")
            RfcommPort(
                channel = channel,
                uuid = null,
                serviceName = serviceName,
                requiresAuth = null,
                isOpen = true,
                riskLevel = profile?.risk ?: AttackResult.Severity.MEDIUM,
                rawSentHex = if (sentBytes != null) bytesToHex(sentBytes) else null,
                rawReceivedHex = if (receivedBytes != null) bytesToHex(receivedBytes) else null,
                rawReceivedAscii = if (receivedBytes != null) bytesToAscii(receivedBytes) else null,
                interactionLog = log
            )
        } catch (_: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Craft a protocol-specific probe based on the service name
     */
    private fun createServiceProbe(serviceName: String): ByteArray? {
        return when {
            serviceName.contains("OBEX", ignoreCase = true) ->
                byteArrayOf(0x80.toByte(), 0x00, 0x10.toByte(), 0x00, 0x10.toByte(), 0x00, 0x00, 0x00)
            serviceName.contains("AT", ignoreCase = true) || serviceName.contains("DUN", ignoreCase = true) ||
                serviceName.contains("HFP", ignoreCase = true) || serviceName.contains("HSP", ignoreCase = true) ->
                "AT\r\n".toByteArray()
            serviceName.contains("SPP", ignoreCase = true) || serviceName.contains("Serial", ignoreCase = true) ->
                "ATI\r\n".toByteArray()
            serviceName.contains("MAP", ignoreCase = true) || serviceName.contains("PBAP", ignoreCase = true) ||
                serviceName.contains("SYNC", ignoreCase = true) ->
                byteArrayOf(0x80.toByte(), 0x00, 0x10.toByte(), 0x00, 0x10.toByte(), 0x00, 0x00, 0x00)
            else -> null
        }
    }

    /**
     * Identify service from received bytes
     */
    private fun identifyFromBytes(data: ByteArray?, channel: Int): String {
        if (data == null || data.isEmpty()) return "Unknown (Channel $channel)"
        val clean = data.dropWhile { it == 0.toByte() }.toByteArray()
        if (clean.isEmpty()) return "Unknown (Channel $channel)"
        return when {
            clean[0] == 0x80.toByte() || clean[0] == 0x81.toByte() ||
                (clean[0] == 0xA0.toByte() || clean[0] == 0xA1.toByte()) -> "OBEX (Channel $channel)"
            clean[0] == 0x10.toByte() && clean.size >= 4 -> "RFCOMM Multiplexer (Channel $channel)"
            String(clean, Charsets.US_ASCII).trimStart().take(2).lowercase() == "at" -> "AT Command (Channel $channel)"
            String(clean, Charsets.US_ASCII).trimStart().take(1) == "+" -> "Modem (Channel $channel)"
            String(clean, Charsets.US_ASCII).take(4).all { it.isLetterOrDigit() || it == ' ' || it == '\r' || it == '\n' } ->
                "Text Protocol (Channel $channel): \"${String(clean, Charsets.US_ASCII).take(30)}\""
            else -> "Unknown Protocol (Channel $channel)"
        }
    }

    /**
     * Identify service by combining UUID profile name with response evidence
     */
    private fun identifyFromResponse(profileName: String, data: ByteArray?): String? {
        if (data == null || data.isEmpty()) return null
        val clean = data.dropWhile { it == 0.toByte() }.toByteArray()
        if (clean.isEmpty()) return null
        return when {
            (clean[0] == 0xA0.toByte() || clean[0] == 0xA1.toByte()) -> "OBEX ✅ (${profileName})"
            clean[0] == 0x80.toByte() || clean[0] == 0x81.toByte() -> "OBEX ✅ (${profileName})"
            String(clean, Charsets.US_ASCII).trim().startsWith("AT") ||
                String(clean, Charsets.US_ASCII).trim().startsWith("OK") -> "AT/Modem ✅ (${profileName})"
            else -> null
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }

    private fun bytesToAscii(bytes: ByteArray): String {
        val printable = bytes.map { if (it.toInt() in 0x20..0x7E) it.toChar() else '.' }.toCharArray()
        return String(printable).trim()
    }

    private fun getProbeUuids(): List<Pair<String, ProfileInfo>> =
        KNOWN_UUIDS.entries.map { (uuid, profile) -> uuid to profile }

    private fun calculateRiskScore(ports: List<RfcommPort>): Float {
        if (ports.isEmpty()) return 0f
        val score = ports.sumOf { port ->
            when (port.riskLevel) {
                AttackResult.Severity.CRITICAL -> 3.5f
                AttackResult.Severity.HIGH -> 2.0f
                AttackResult.Severity.MEDIUM -> 1.0f
                AttackResult.Severity.LOW -> 0.5f
                AttackResult.Severity.INFO -> 0.0f
            }.toDouble()
        }.toFloat()
        return minOf(score, 10f)
    }
}
