package com.example.hotspotneighborinspector.network

import android.content.Context
import android.net.wifi.WifiManager
import com.example.hotspotneighborinspector.model.ConnectedDevice
import com.example.hotspotneighborinspector.model.HotspotNetworkInfo
import com.example.hotspotneighborinspector.model.NetworkInterfaceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class HotspotScannerRepository(private val context: Context) {

    companion object {
        private val HOTSPOT_IFACE_PREFIXES = listOf(
            "swlan", "ap", "wlan1", "wlan2", "softap", "p2p", "rndis", "ncm", "bt-pan", "tether"
        )
        private val HOTSPOT_SUBNET_PREFIXES = listOf(
            "192.168.43.", "192.168.42.", "192.168.49.", "192.168.137.", "172.20.10."
        )
    }

    fun detectNetworkInfo(preferredInterface: String? = null): HotspotNetworkInfo {
        val interfaces = findActiveInterfaces()
        if (interfaces.isEmpty()) {
            return HotspotNetworkInfo(
                isActive = false,
                interfaceName = "None",
                localIp = "Not connected",
                subnetMask = "—",
                subnetCidr = "—",
                availableInterfaces = emptyList()
            )
        }

        val selected = interfaces.find { it.interfaceName == preferredInterface }
            ?: interfaces.firstOrNull { it.isHotspotInterface }
            ?: interfaces.first()

        return HotspotNetworkInfo(
            isActive = selected.isHotspotInterface,
            interfaceName = selected.interfaceName,
            localIp = selected.localIp,
            subnetMask = selected.subnetMask,
            subnetCidr = selected.subnetDisplay,
            availableInterfaces = interfaces
        )
    }

    suspend fun scanConnectedDevices(
        preferredInterface: String? = null,
        onProgress: (Float) -> Unit
    ): Pair<HotspotNetworkInfo, List<ConnectedDevice>> = withContext(Dispatchers.IO) {
        val netInfo = detectNetworkInfo(preferredInterface)
        val subnetPrefix = ArpAndNeighborParser.extractSubnetPrefix24(netInfo.localIp)
            ?: return@withContext Pair(netInfo, emptyList())

        val ownIps = netInfo.availableInterfaces.map { it.localIp }.toSet()
        onProgress(0.05f)

        // 1. Read kernel neighbor table before sweep
        val neighborEntries = ConcurrentHashMap<String, RawNeighborEntry>()
        readNeighborTable(subnetPrefix, netInfo.interfaceName).forEach { entry ->
            neighborEntries[entry.ipAddress] = entry
        }

        // 2. Sweep all 254 IPs in the /24 subnet to discover active devices
        val activeLatencies = ConcurrentHashMap<String, Int>()
        val semaphore = Semaphore(48)
        val completed = AtomicInteger(0)

        coroutineScope {
            (1..254).map { host ->
                val ip = "$subnetPrefix.$host"
                async {
                    semaphore.withPermit {
                        if (ip !in ownIps) {
                            sendUdpTrigger(ip)
                            val rtt = probeHostMs(ip, timeoutMs = 220)
                            if (rtt != null) {
                                activeLatencies[ip] = rtt
                            }
                        }
                        val done = completed.incrementAndGet()
                        if (done % 10 == 0 || done == 254) {
                            onProgress(0.08f + (done / 254f) * 0.78f)
                        }
                    }
                }
            }.awaitAll()
        }

        // 3. Re-read kernel neighbor table after sweep
        readNeighborTable(subnetPrefix, netInfo.interfaceName).forEach { entry ->
            val existing = neighborEntries[entry.ipAddress]
            if (existing == null || (existing.macAddress == null && entry.macAddress != null)) {
                neighborEntries[entry.ipAddress] = entry
            } else if (entry.arpState == "REACHABLE") {
                neighborEntries[entry.ipAddress] = entry
            }
        }

        onProgress(0.92f)

        // 4. Combine discovered IPs and verify any STALE neighbor entries
        val candidateIps = (activeLatencies.keys + neighborEntries.keys)
            .filter { it.startsWith("$subnetPrefix.") && it !in ownIps }
            .distinct()

        val devices = coroutineScope {
            candidateIps.map { ip ->
                async {
                    val arp = neighborEntries[ip]
                    val latency = activeLatencies[ip] ?: pingHostNativeMs(ip)
                    val isReachableInArp = arp?.arpState in setOf("REACHABLE", "DELAY", "PROBE")

                    // Only include devices that actually responded or are actively reachable in ARP
                    if (latency == null && !isReachableInArp) {
                        return@async null
                    }

                    val realMac = arp?.macAddress?.takeIf { OuiVendorLookup.isValidClientMac(it) }
                    val vendor = realMac?.let { OuiVendorLookup.lookupVendor(it) }
                    val hostname = resolveHostName(ip)

                    val label = when {
                        !hostname.isNullOrBlank() -> hostname
                        !vendor.isNullOrBlank() -> vendor
                        !netInfo.isActive && ip.endsWith(".1") -> "Router / Gateway"
                        else -> "Connected Device"
                    }

                    ConnectedDevice(
                        ipAddress = ip,
                        deviceName = label,
                        macAddress = realMac,
                        latencyMs = latency,
                        isOnline = true
                    )
                }
            }.awaitAll().filterNotNull()
        }.sortedBy { ArpAndNeighborParser.ipToSortableLong(it.ipAddress) }

        onProgress(1.0f)
        Pair(netInfo, devices)
    }

    private fun findActiveInterfaces(): List<NetworkInterfaceInfo> {
        val wifiApEnabled = isWifiApEnabledReflective()
        val found = LinkedHashMap<String, NetworkInterfaceInfo>()

        // 1. Standard Java NetworkInterface enumeration
        try {
            val netIfaces = NetworkInterface.getNetworkInterfaces()
            if (netIfaces != null) {
                for (nif in Collections.list(netIfaces)) {
                    if (!nif.isUp || nif.isLoopback) continue
                    val name = nif.name.lowercase(Locale.US)
                    if (isIgnoredCellularOrVirtual(name)) continue

                    for (ia in nif.interfaceAddresses) {
                        val addr = ia.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: continue
                            val prefix = ArpAndNeighborParser.extractSubnetPrefix24(ip) ?: continue
                            val cidr = ia.networkPrefixLength.toInt().let { if (it in 8..30) it else 24 }
                            val isHotspot = isHotspotInterfaceNameOrIp(name, ip, wifiApEnabled)
                            found[nif.name] = NetworkInterfaceInfo(
                                interfaceName = nif.name,
                                localIp = ip,
                                subnetPrefix = prefix,
                                subnetMask = ArpAndNeighborParser.prefixLengthToSubnetMask(cidr),
                                cidrPrefix = cidr,
                                isHotspotInterface = isHotspot
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Also check `ip -4 addr show` in case Samsung `swlan0` or `ap0` is hidden from Java API
        val ipAddrOutput = runCommand(arrayOf("ip", "-4", "addr", "show"))
        if (ipAddrOutput.isNotBlank()) {
            var currentIface: String? = null
            val ifaceRegex = Regex("""^\d+:\s+([a-zA-Z0-9_.-]+):""")
            val inetRegex = Regex("""inet\s+(\d+\.\d+\.\d+\.\d+)/(\d+)""")
            ipAddrOutput.lineSequence().forEach { line ->
                val trimmed = line.trim()
                ifaceRegex.find(trimmed)?.let { m ->
                    currentIface = m.groupValues[1].substringBefore('@')
                }
                inetRegex.find(trimmed)?.let { m ->
                    val iface = currentIface ?: return@let
                    val lower = iface.lowercase(Locale.US)
                    if (lower == "lo" || isIgnoredCellularOrVirtual(lower)) return@let
                    val ip = m.groupValues[1]
                    val cidr = m.groupValues[2].toIntOrNull()?.let { if (it in 8..30) it else 24 } ?: 24
                    val prefix = ArpAndNeighborParser.extractSubnetPrefix24(ip) ?: return@let
                    if (!found.containsKey(iface)) {
                        found[iface] = NetworkInterfaceInfo(
                            interfaceName = iface,
                            localIp = ip,
                            subnetPrefix = prefix,
                            subnetMask = ArpAndNeighborParser.prefixLengthToSubnetMask(cidr),
                            cidrPrefix = cidr,
                            isHotspotInterface = isHotspotInterfaceNameOrIp(lower, ip, wifiApEnabled)
                        )
                    }
                }
            }
        }

        return found.values.sortedByDescending { it.isHotspotInterface }
    }

    private fun isIgnoredCellularOrVirtual(name: String): Boolean {
        return name.startsWith("rmnet") ||
            name.startsWith("ccmni") ||
            name.startsWith("dummy") ||
            name.startsWith("tun") ||
            name.startsWith("v4-") ||
            name.startsWith("clat") ||
            name.startsWith("epdg")
    }

    private fun isHotspotInterfaceNameOrIp(
        lowerName: String,
        ip: String,
        wifiApEnabled: Boolean
    ): Boolean {
        if (HOTSPOT_IFACE_PREFIXES.any { lowerName.startsWith(it) }) return true
        if (HOTSPOT_SUBNET_PREFIXES.any { ip.startsWith(it) }) return true
        if (wifiApEnabled && lowerName.startsWith("wlan")) return true
        return false
    }

    private fun readNeighborTable(subnetPrefix: String, interfaceName: String): List<RawNeighborEntry> {
        val map = LinkedHashMap<String, RawNeighborEntry>()

        val commands = listOf(
            arrayOf("ip", "-4", "neigh", "show", "dev", interfaceName),
            arrayOf("ip", "-4", "neigh", "show"),
            arrayOf("ip", "neigh", "show")
        )
        for (cmd in commands) {
            val out = runCommand(cmd)
            if (out.isNotBlank()) {
                ArpAndNeighborParser.parseIpNeighOutput(out)
                    .filter { it.ipAddress.startsWith("$subnetPrefix.") }
                    .forEach { entry ->
                        val prev = map[entry.ipAddress]
                        if (prev == null || (prev.macAddress == null && entry.macAddress != null)) {
                            map[entry.ipAddress] = entry
                        }
                    }
            }
        }

        try {
            val arpFile = File("/proc/net/arp")
            if (arpFile.exists() && arpFile.canRead()) {
                ArpAndNeighborParser.parseProcNetArp(arpFile.readText())
                    .filter { it.ipAddress.startsWith("$subnetPrefix.") }
                    .forEach { entry ->
                        val prev = map[entry.ipAddress]
                        if (prev == null || (prev.macAddress == null && entry.macAddress != null)) {
                            map[entry.ipAddress] = entry
                        }
                    }
            }
        } catch (_: Exception) {
        }

        return map.values.toList()
    }

    private fun sendUdpTrigger(ip: String) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 60
                val data = byteArrayOf(0)
                socket.send(DatagramPacket(data, data.size, InetAddress.getByName(ip), 33434))
            }
        } catch (_: Exception) {
        }
    }

    private fun probeHostMs(ip: String, timeoutMs: Int): Int? {
        try {
            val addr = InetAddress.getByName(ip)
            val start = System.nanoTime()
            if (addr.isReachable(timeoutMs)) {
                return ((System.nanoTime() - start) / 1_000_000.0).roundToInt().coerceAtLeast(1)
            }
        } catch (_: Exception) {
        }

        val ports = intArrayOf(80, 443, 62078, 5353, 22)
        for (port in ports) {
            val start = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 140)
                }
                return ((System.nanoTime() - start) / 1_000_000.0).roundToInt().coerceAtLeast(1)
            } catch (_: java.net.ConnectException) {
                val elapsed = ((System.nanoTime() - start) / 1_000_000.0).roundToInt()
                if (elapsed < 140) {
                    return elapsed.coerceAtLeast(1)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun pingHostNativeMs(ip: String): Int? {
        val out = runCommand(arrayOf("/system/bin/ping", "-c", "1", "-W", "1", ip))
        return ArpAndNeighborParser.parsePingLatencyMs(out)
    }

    private fun resolveHostName(ip: String): String? {
        return try {
            val host = InetAddress.getByName(ip).canonicalHostName?.trim()
            if (!host.isNullOrEmpty() && host != ip && !ArpAndNeighborParser.isValidIpv4(host)) {
                host
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun runCommand(cmd: Array<String>): String {
        return try {
            val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val text = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
            proc.waitFor()
            text
        } catch (_: Exception) {
            ""
        }
    }

    private fun isWifiApEnabledReflective(): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return false
            val m = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
            m.isAccessible = true
            m.invoke(wm) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }
}
