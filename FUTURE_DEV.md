# BT Security Lab — Future Development Roadmap

> Last Updated: 2026-07-13
> Status: v1.0 Released → v2.0 Planned (ESP32-S3 Wireless Adapter)

---

## ✅ COMPLETED (v1.0)

### Core App
- [x] Android app with Classic + BLE dual-tab scanning
- [x] BR/EDR inquiry scan (paired & discoverable devices)
- [x] BLE Low Energy scan (advertising devices, RSSI, manufacturer data)
- [x] Device detail screen with full metadata
- [x] Signal strength indicator (visual bars + dBm)
- [x] Device class identification (major class + service class)
- [x] SDP profile enumeration (32 known UUIDs)
- [x] Security vulnerability assessment engine
- [x] Permissions helper (Android 6+ and 12+ runtime permissions)

### Attack Modules (No Root)
- [x] **RFCOMM Port Scanner** — SDP fetch → UUID probe → Channel scan (1-30)
  - 4-thread concurrent scanning
  - OBEX/AT/Text protocol detection
  - Risk scoring (0-10)
- [x] **Pairing PIN Tester** — 25 common default PINs
  - 0000, 1234, 1111, etc.
  - Bond removal capability
- [x] **BLE Connection Flooder** (CVE-2026-52866 pattern)
  - GATT connection slot exhaustion
  - 12 rapid connections, 8s hold each
  - Auto-stop 30s
- [x] **RFCOMM Buffer Flood** (CVE-2026-31280 pattern)
  - Multi-channel buffer overflow
  - 10 channels × 4KB payloads × 20 iterations
  - Auto-stop 25s
- [x] **BLE GATT Fuzzer** (BSFuzzer method)
  - 15 malformed payload types per characteristic
  - Crash detection via disconnect monitoring
  - Auto-stop 30s

### Safety
- [x] Lab environment warning dialog
- [x] Auto-stop timers on all attacks
- [x] Stop buttons for immediate abort
- [x] Legal disclaimer in README

### Documentation
- [x] Comprehensive README with Mermaid diagrams
- [x] Attack flow visualizations
- [x] CVE references for each attack
- [x] GitHub Release v1.0 with APK

---

## 🔧 IN PROGRESS

### ESP32-S3 Firmware (Wireless Adapter)
- [ ] Basic ESP32-S3 setup (Arduino/PlatformIO)
- [ ] WiFi STA mode (connect to home network)
- [ ] TCP server for Android app commands
- [ ] Command parser (JSON protocol)
- [ ] BLE scanner module
- [ ] BLE deauth module (connection reset attacks)
- [ ] BLE advertising flood module
- [ ] Classic BT scanner module
- [ ] Classic BT page scan
- [ ] MAC address spoofing
- [ ] Firmware OTA update support

### Android Companion App (v2.0)
- [ ] WiFi connection manager (discover ESP32 on network)
- [ ] TCP/WebSocket client
- [ ] ESP32 device list screen
- [ ] Remote attack control panel
- [ ] Real-time result streaming
- [ ] Attack history/log
- [ ] OTA firmware update from app

---

## 📋 TODO — v2.0 (ESP32-S3 Integration)

### Phase 1: ESP32-S3 Basic Firmware
| # | Task | Priority | Est. Effort |
|---|------|----------|-------------|
| 1 | PlatformIO project setup for ESP32-S3 | HIGH | 1 hour |
| 2 | WiFi STA + TCP server (port 42069) | HIGH | 2 hours |
| 3 | JSON command protocol definition | HIGH | 1 hour |
| 4 | BLE scanner module (NimBLE) | HIGH | 3 hours |
| 5 | MAC address read/change | HIGH | 2 hours |
| 6 | Basic command routing | HIGH | 2 hours |

### Phase 2: ESP32-S3 Attack Firmware
| # | Task | Priority | Est. Effort |
|---|------|----------|-------------|
| 7 | BLE deauth (connection reset flood) | HIGH | 4 hours |
| 8 | BLE advertising channel jamming | HIGH | 4 hours |
| 9 | BLE connection flood DoS | HIGH | 3 hours |
| 10 | BLE MITM (GATT proxy) | MEDIUM | 6 hours |
| 11 | BLE packet sniffing (Sniffle port) | MEDIUM | 5 hours |
| 12 | Classic BT page scan flood | HIGH | 3 hours |
| 13 | Classic BT LMP disconnect injection | MEDIUM | 6 hours |
| 14 | RFCOMM channel flood | HIGH | 3 hours |
| 15 | Advertising spam (beacon flood) | MEDIUM | 3 hours |

### Phase 3: Android Companion App
| # | Task | Priority | Est. Effort |
|---|------|----------|-------------|
| 16 | ESP32 auto-discovery (mDNS/UDP broadcast) | HIGH | 3 hours |
| 17 | WiFi connection screen | HIGH | 2 hours |
| 18 | TCP client with reconnect logic | HIGH | 3 hours |
| 19 | Remote attack control UI | HIGH | 4 hours |
| 20 | Real-time result display | HIGH | 3 hours |
| 21 | Attack selection + parameter config | MEDIUM | 3 hours |
| 22 | Connection status indicator | HIGH | 1 hour |
| 23 | Attack history log | LOW | 3 hours |

### Phase 4: Advanced Features
| # | Task | Priority | Est. Effort |
|---|------|----------|-------------|
| 24 | BLE Sniffle integration (packet capture) | MEDIUM | 5 hours |
| 25 | BLE protocol decode (show packets) | LOW | 6 hours |
| 26 | BLE MITM relay (proxy attack) | MEDIUM | 8 hours |
| 27 | Classic BT sniffing (Ubertooth-like) | LOW | 10 hours |
| 28 | Firmware OTA update from app | MEDIUM | 3 hours |
| 29 | ESP32 multi-device mesh | LOW | 8 hours |
| 30 | Web dashboard (ESP32 serves HTML) | LOW | 4 hours |

---

## 📋 TODO — v3.0 (Root + Shizuku Layer)

### Shizuku Integration
| # | Task | Priority | Est. Effort |
|---|------|----------|-------------|
| 31 | Shizuku SDK integration | HIGH | 3 hours |
| 32 | Shell command executor via Shizuku | HIGH | 4 hours |
| 33 | MAC spoofing via bt_config edit | HIGH | 3 hours |
| 34 | Bond removal via file manipulation | HIGH | 2 hours |
| 35 | BT process kill/restart | HIGH | 1 hour |
| 36 | BT log dump via dumpsys | MEDIUM | 2 hours |
| 37 | BD_ADDR read via settings | MEDIUM | 1 hour |
| 38 | Scan mode force change | LOW | 2 hours |

### Root Attacks (If Rooted)
| # | Task | Priority | Est. Effort |
|---|------|----------|-------------|
| 39 | Raw HCI socket access | HIGH | 4 hours |
| 40 | HCI command injection | HIGH | 6 hours |
| 41 | LMP disconnect frame injection | MEDIUM | 8 hours |
| 42 | BLE deauth via HCI | HIGH | 6 hours |
| 43 | Classic BT deauth via HCI | HIGH | 4 hours |
| 44 | BLE pairing MITM (SMP) | MEDIUM | 8 hours |
| 45 | Firmware dump extraction | LOW | 4 hours |

---

## 📋 TODO — v4.0 (Reporting + ML)

### Reporting
| # | Task | Priority | Est. Effort |
|---|------|----------|-------------|
| 46 | PDF report generation | HIGH | 4 hours |
| 47 | HTML report with charts | MEDIUM | 3 hours |
| 48 | Attack timeline visualization | MEDIUM | 4 hours |
| 49 | CVSS score calculation | MEDIUM | 3 hours |
| 50 | Compliance mapping (OWASP, NIST) | LOW | 6 hours |

### Machine Learning
| # | Task | Priority | Est. Effort |
|---|------|----------|-------------|
| 51 | Device fingerprinting via ML | LOW | 10 hours |
| 52 | Anomaly detection in BT traffic | LOW | 12 hours |
| 53 | Attack pattern classification | LOW | 8 hours |
| 54 | Predictive vulnerability scoring | LOW | 6 hours |

---

## 🏗️ Architecture (v2.0 Target)

```
┌─────────────────────────────────────────────────────────────┐
│                    BT SECURITY LAB v2.0                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐    WiFi/TCP    ┌──────────────────┐      │
│  │ Android App  │◄─────────────▶│ ESP32-S3         │      │
│  │              │   JSON cmds    │ (Wireless BT     │      │
│  │ • Scan UI    │                │  Adapter)        │      │
│  │ • Attack UI  │                │                  │      │
│  │ • Results    │                │ • BLE Radio      │      │
│  │ • Reports    │                │ • Classic Radio  │      │
│  │ • Settings   │                │ • WiFi STA       │      │
│  └──────┬───────┘                └────────┬─────────┘      │
│         │                                 │                │
│         │ Shizuku                         │ BT Radio       │
│         │ (optional)                      │                │
│         ▼                                 ▼                │
│  ┌──────────────┐                ┌──────────────────┐      │
│  │ Local Attacks│                │ Remote Attacks   │      │
│  │ • MAC spoof  │                │ • BLE deauth     │      │
│  │ • Bond mgmt  │                │ • BLE jam        │      │
│  │ • BT reset   │                │ • Classic deauth │      │
│  │ • GATT fuzz  │                │ • LMP inject     │      │
│  └──────────────┘                │ • RFCOMM flood   │      │
│                                  │ • BLE sniff      │      │
│                                  │ • MITM relay     │      │
│                                  └──────────────────┘      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 Attack Matrix (Final Target)

| Attack | Android Only | + Shizuku | + ESP32 | + Root |
|--------|:---:|:---:|:---:|:---:|
| BLE Scan | ✅ | ✅ | ✅ | ✅ |
| Classic Scan | ✅ | ✅ | ✅ | ✅ |
| SDP Enum | ✅ | ✅ | — | ✅ |
| RFCOMM Probe | ✅ | ✅ | ✅ | ✅ |
| Pairing Test | ✅ | ✅ | — | ✅ |
| BLE Conn Flood | ✅ | ✅ | ✅ | ✅ |
| RFCOMM Flood | ✅ | ✅ | ✅ | ✅ |
| GATT Fuzzer | ✅ | ✅ | ✅ | ✅ |
| MAC Spoofing | — | ✅ | ✅ | ✅ |
| Bond Manipulation | — | ✅ | — | ✅ |
| BT Kill/Reset | — | ✅ | — | ✅ |
| **BLE Deauth** | — | — | ✅ | ✅ |
| **BLE Jamming** | — | — | ✅ | ✅ |
| **Classic Deauth** | — | — | ✅ | ✅ |
| **LMP Disconnect** | — | — | ✅ | ✅ |
| **BLE Sniffing** | — | — | ✅ | ✅ |
| **BLE MITM** | — | — | ✅ | ✅ |
| **Advertising Spam** | — | — | ✅ | ✅ |
| Raw HCI Inject | — | — | — | ✅ |
| Firmware Dump | — | — | — | ✅ |

---

## 🛒 Hardware Shopping List

| Item | Approx Cost | Where |
|------|------------|-------|
| ESP32-S3-DevKitC-1 | $5-8 | Amazon/AliExpress |
| USB-C Cable (for flashing) | $2 | Amazon |
| 5V USB Power Adapter | $3 | Any |
| Jumper Wires (optional) | $1 | Amazon |
| **Total** | **~$11-14** | |

---

## 📅 Timeline

```
v1.0 (DONE)          ← Current
  └── Released 2026-07-13
  └── 5 attack modules
  └── APK on GitHub

v1.1 (PLANNED)       ← Quick Win
  └── Fix README header
  └── Add Shizuku integration
  └── 8 new Shizuku attacks
  └── Est: 1-2 weeks

v2.0 (PLANNED)       ← ESP32 Integration
  └── ESP32-S3 firmware
  └── WiFi wireless adapter
  └── Android companion app
  └── 15 new attacks
  └── Est: 1-2 months

v3.0 (PLANNED)       ← Root + Advanced
  └── Root attack layer
  └── Raw HCI injection
  └── LMP disconnect
  └── BLE MITM
  └── Est: 1 month

v4.0 (PLANNED)       ← ML + Reporting
  └── PDF reports
  └── ML device fingerprinting
  └── Anomaly detection
  └── Est: 2-3 months
```

---

## 🔗 References

| Resource | URL |
|----------|-----|
| Sniffle (BLE Sniffer) | github.com/nccgroup/Sniffle |
| ESP32 BLE Arduino | github.com/espressif/arduino-esp32 |
| NimBLE | github.com/espressif/esp-nimble |
| Ubertooth | github.com/greatscottgadgets/ubertooth |
| BSFuzzer Paper | USENIX 2025 |
| CVE-2026-52866 | BLE Connection Slot Monopolization |
| CVE-2026-31280 | RFCOMM Buffer Overflow DoS |
| CVE-2025-36911 | WhisperPair Fast Pair Attack |
| Shizuku | github.com/RikkaApps/Shizuku |

---

**Questions? Open an issue on GitHub.**
