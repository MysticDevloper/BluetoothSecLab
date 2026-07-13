# BT Security Lab

A Bluetooth security assessment Android application for authorized penetration testing and research. Scans Classic (BR/EDR) and BLE devices, enumerates services, tests pairing vulnerabilities, probes RFCOMM ports, and generates security reports.

> **For educational and authorized security testing only.**

---

## APK Download

The pre-built debug APK is included in this repository:

```
BTSecurityLab-v1.0-debug.apk   (6.1 MB)
```

**Install on Android:**
1. Enable **Install from unknown sources** in Settings > Security
2. Transfer the APK to your phone
3. Open the APK file and tap **Install**

**Requirements:**
- Android 5.0+ (API 21)
- Bluetooth hardware
- Location services enabled (required by Android for BT scanning)

---

## Features

### Device Discovery
- **Classic Bluetooth (BR/EDR)** scan — discovers paired and discoverable devices via inquiry
- **BLE scan** — discovers Low Energy advertising devices with RSSI signal strength
- Dual-tab interface showing device counts in real-time

### Device Inspection
- Device name, MAC address, bond state, device class, and service class
- RSSI signal strength with visual quality indicator (bar + text)
- Bluetooth profile enumeration via SDP (Serial Port, A2DP, HID, PAN, OBEX, etc.)
- Manufacturer identification from BLE advertisement data

### Security Assessment
- **Vulnerability analysis** based on exposed profiles and device characteristics:
  - OBEX file transfer exposure
  - Legacy/insecure profiles (HSP, HFP, DUN, LAN, SYNC)
  - Services exposed without bonding (CVE-2020-10135 / BIAS)
  - Default PIN detection (HC-05/HC-06 modules)
  - BrakTooth detection (ESP32/ESP devices, CVE-2021-28139)
- **Severity ratings**: LOW / MEDIUM / HIGH / CRITICAL
- CVE references where applicable

### RFCOMM Port Scanner
- SDP UUID enumeration — fetches service records from target device
- UUID-based probe — connects to known Bluetooth profile UUIDs and sends protocol-specific probes (OBEX, AT commands, SPP)
- Raw channel scan — scans RFCOMM channels 1-30 with concurrent connections (4 threads)
- Protocol identification from raw byte responses (OBEX, AT/Modem, Text Protocol)
- Risk scoring (0-10) based on open port severity

### Pairing Testing
- Tests 25 common default PINs (0000, 1234, 1111, etc.)
- Bond removal capability
- Results displayed with success/failure per PIN

### Security Report
- Formatted assessment report with timestamp
- Device info, discovered services, and security findings
- Recommendations section (firmware updates, secure pairing, LE Secure Connections)
- Share report via Android share intent (email, messaging, etc.)

---

## Architecture

```
com.bluetoothseclab/
├── MainActivity.kt              # Main screen — scan controls, device lists
├── DeviceDetailActivity.kt      # Device detail — info, pairing test, RFCOMM scan
├── SecurityReportActivity.kt    # Formatted report with share capability
├── BluetoothScanner.kt          # Classic BR/EDR discovery via BroadcastReceiver
├── BLEScanner.kt                # BLE scan via BluetoothLeScanner API
├── DevicePagerAdapter.kt        # ViewPager adapter for Classic/BLE tabs
├── DeviceInfoGatherer.kt        # Device class, manufacturer ID resolution
├── ServiceEnumerator.kt         # SDP UUID → profile name resolution
├── PairingTester.kt             # PIN brute-force + bond management
├── VulnerabilityChecker.kt      # Security issue assessment engine
├── PermissionsHelper.kt         # Runtime permission + location/BT state checks
├── attacks/
│   └── RfcommScanner.kt         # RFCOMM port scanner with protocol probing
└── models/
    ├── BluetoothDeviceInfo.kt   # Device data model
    ├── SecurityIssue.kt         # Security finding model
    └── AttackResult.kt          # Scan result + finding + severity models
```

---

## Permissions

| Permission | Purpose |
|---|---|
| `BLUETOOTH` | Classic Bluetooth access |
| `BLUETOOTH_ADMIN` | Bluetooth adapter control |
| `BLUETOOTH_SCAN` | BLE scanning (Android 12+) |
| `BLUETOOTH_CONNECT` | Device connection (Android 12+) |
| `ACCESS_FINE_LOCATION` | Required by Android for BT discovery |
| `ACCESS_COARSE_LOCATION` | Location permission fallback |
| `INTERNET` | (Future: remote reporting) |

---

## Building from Source

**Prerequisites:**
- Android Studio (Arctic Fox or later)
- Android SDK 34
- JDK 17

**Steps:**
```bash
git clone https://github.com/MysticDevloper/BluetoothSecLab.git
cd BluetoothSecLab
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

**Release build:**
```bash
./gradlew assembleRelease
```
(Requires signing configuration in `app/build.gradle.kts`)

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| AndroidX Core KTX | 1.12.0 | Kotlin extensions |
| AppCompat | 1.6.1 | Backward-compatible UI |
| Material Design | 1.11.0 | UI components, tabs, snackbars |
| ConstraintLayout | 2.1.4 | Layout system |
| Navigation | 2.7.6 | Fragment navigation |
| CardView | 1.0.0 | Card-based UI |
| RecyclerView | 1.3.2 | Device list rendering |

---

## How It Works

### Scanning Flow
1. App requests Bluetooth + Location permissions on launch
2. User taps **Scan Classic** or **Scan BLE**
3. Discovered devices appear in respective tabs with live count
4. Tap any device to open detail screen

### Assessment Flow
1. Device detail screen shows info, profiles, and vulnerability analysis
2. **Test Pairing** — tries 25 common PINs against the device
3. **Scan RFCOMM Ports** — enumerates open RFCOMM channels with protocol probes
4. **View Report** — generates formatted security report
5. **Share Report** — send report via any app

### RFCOMM Scanner Strategy
1. SDP UUID fetch → discover advertised services
2. UUID probe → connect to each known profile, send protocol-specific payload
3. Channel scan → brute-force channels 1-30 via reflection API
4. Response analysis → identify protocol from raw bytes (OBEX, AT, Text)
5. Risk scoring → aggregate severity of all open ports

---

## Known Limitations

- RFCOMM channel scan uses reflection (`createRfcommSocket`) — may not work on all Android versions
- Pairing PIN testing uses `setPin()` via reflection — blocked on Android 12+ for unbonded devices
- BLE advertisement data (manufacturer-specific) requires active BLE scan to capture
- No LE Secure Connections pairing test (requires BLE SMP layer access)
- Risk scoring is heuristic-based, not a formal vulnerability scanner

---

## Legal Disclaimer

This tool is for **authorized security testing only**. Use only on devices you own or have explicit written permission to test. Unauthorized use may violate laws including CFAA (US), GDPR (EU), and local regulations. The developer assumes no liability for misuse.

---

## License

MIT License — see [LICENSE](LICENSE) for details.


