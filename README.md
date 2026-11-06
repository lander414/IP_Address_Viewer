# Hotspot Neighbor Inspector

A clean, modern Android mobile utility app built with Kotlin and Jetpack Compose (Material 3) that scans the active Wi-Fi Hotspot / tethering subnet to pair and display both the local IPv4 address (`192.168.43.x`) and hardware MAC address for each connected client.

## Key Problem Solved

Android hotspot settings frequently display connected devices only by their MAC address (or omit the IPv4 mapping entirely). **Hotspot Neighbor Inspector** inspects active hotspot interfaces (`ap0`, `wlan1`, `swlan0`, `rndis0`, `wlan0`), sweeps the `/24` subnet via concurrent UDP/ICMP/TCP probes, and pairs kernel neighbor/ARP entries (`ip neigh show` & `/proc/net/arp`) with reverse-DNS hostnames and IEEE OUI vendor prefixes.

## UI/UX & Features

- **Dashboard Header**:
  - Live **Hotspot Status** badge (`HOTSPOT ACTIVE` / `HOTSPOT INACTIVE`) with active interface indicator (`ap0`, `wlan0`, etc.) and quick link to Android Wireless/Tethering Settings.
  - **Gateway IP** (`192.168.43.1` with one-tap copy), **Subnet Mask** (`255.255.255.0` / CIDR), and **Total Connected Devices** count with online & average latency summary.
  - **Demo AP / Live NIC Mode Switch**: Test and preview realistic hotspot client pairings (`192.168.43.x`) even when Wi-Fi Hotspot is turned off or when running inside an Android Emulator.
- **Device Cards**:
  - **Hostname / Vendor Name** (or `"Unknown Client"`), IEEE OUI vendor lookup, and Locally Administered (Randomized MAC) detection.
  - **Prominent IPv4 Address** in monospace typography with a **one-tap Copy IP** button and visual confirmation.
  - **Paired MAC Address** with kernel ARP state badge (`REACHABLE`, `STALE`, `DELAY`) and a **one-tap Copy MAC** button.
  - **Latency / Ping Indicator** (`12 ms - Online`) color-coded by latency tier; tap any latency pill to re-ping that client on demand.
  - Tap any card to open the **Neighbor ARP & Interface Diagnostics** dialog and assign a custom alias.
- **Action Controls**:
  - **Scan Subnet** button with an **animated scanning progress bar** and live terminal stage readout.
  - **Auto-Refresh Toggle** (`Off` / `10s` / `30s`) with live countdown indicator.
  - **Search / Filter Bar** to filter by partial IP, partial MAC (with or without colons), hostname, or vendor, plus filter chips (`All`, `Online`, `< 50 ms`, `Known Vendor`) and sort cycling (`IP Address`, `Latency`, `Hostname / Vendor`).
- **Terminal / Slate Dark Aesthetic**:
  - Deep slate surfaces (`#090D16` / `#0F172A`), recessed terminal code wells (`#0B111E`), and high-contrast cyan/emerald accents.

## Building & Testing

```bash
./gradlew assembleDebug testDebugUnitTest
```

