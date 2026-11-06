package com.example.hotspotneighborinspector.network

import com.example.hotspotneighborinspector.model.ConnectedDevice
import java.util.Locale

data class RawNeighborEntry(
    val ipAddress: String,
    val macAddress: String?,
    val interfaceName: String,
    val arpState: String
)

object ArpAndNeighborParser {

    private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val PING_TIME_REGEX = Regex("""time[=<]\s*([\d.]+)\s*ms""", RegexOption.IGNORE_CASE)

    fun isValidIpv4(ip: String): Boolean {
        val match = IPV4_REGEX.matchEntire(ip.trim()) ?: return false
        return match.groupValues.drop(1).all { octet ->
            val v = octet.toIntOrNull() ?: return@all false
            v in 0..255
        }
    }

    fun ipToSortableLong(ip: String): Long {
        val match = IPV4_REGEX.matchEntire(ip.trim()) ?: return Long.MAX_VALUE
        val o1 = match.groupValues[1].toLong()
        val o2 = match.groupValues[2].toLong()
        val o3 = match.groupValues[3].toLong()
        val o4 = match.groupValues[4].toLong()
        return (o1 shl 24) or (o2 shl 16) or (o3 shl 8) or o4
    }

    fun prefixLengthToSubnetMask(prefixLength: Int): String {
        val clamped = prefixLength.coerceIn(0, 32)
        val mask = if (clamped == 0) 0 else (-1 shl (32 - clamped))
        val o1 = (mask ushr 24) and 0xFF
        val o2 = (mask ushr 16) and 0xFF
        val o3 = (mask ushr 8) and 0xFF
        val o4 = mask and 0xFF
        return "$o1.$o2.$o3.$o4"
    }

    fun extractSubnetPrefix24(ip: String): String? {
        if (!isValidIpv4(ip)) return null
        val parts = ip.trim().split(".")
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }

    fun parseIpNeighOutput(output: String): List<RawNeighborEntry> {
        val entries = mutableListOf<RawNeighborEntry>()
        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val tokens = line.split(Regex("""\s+"""))
            val ip = tokens.firstOrNull() ?: return@forEach
            if (!isValidIpv4(ip)) return@forEach

            var dev = "unknown"
            var mac: String? = null
            for (i in 1 until tokens.size - 1) {
                when (tokens[i].lowercase(Locale.US)) {
                    "dev" -> dev = tokens[i + 1]
                    "lladdr" -> {
                        val candidate = OuiVendorLookup.normalizeMac(tokens[i + 1])
                        if (OuiVendorLookup.isValidClientMac(candidate)) {
                            mac = candidate
                        }
                    }
                }
            }
            val lastToken = tokens.last().uppercase(Locale.US)
            val state = if (lastToken in setOf(
                    "REACHABLE", "STALE", "DELAY", "PROBE", "PERMANENT", "INCOMPLETE", "FAILED"
                )
            ) {
                lastToken
            } else {
                "REACHABLE"
            }

            if (state !in setOf("FAILED", "INCOMPLETE")) {
                entries.add(
                    RawNeighborEntry(
                        ipAddress = ip,
                        macAddress = mac,
                        interfaceName = dev,
                        arpState = state
                    )
                )
            }
        }
        return entries
    }

    fun parseProcNetArp(content: String): List<RawNeighborEntry> {
        val entries = mutableListOf<RawNeighborEntry>()
        content.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || (index == 0 && line.startsWith("IP address", ignoreCase = true))) {
                return@forEachIndexed
            }
            val tokens = line.split(Regex("""\s+"""))
            if (tokens.size < 6) return@forEachIndexed
            val ip = tokens[0]
            if (!isValidIpv4(ip)) return@forEachIndexed
            val flags = tokens[2].lowercase(Locale.US)
            val rawMac = OuiVendorLookup.normalizeMac(tokens[3])
            val device = tokens[5]

            val validMac = if (OuiVendorLookup.isValidClientMac(rawMac)) rawMac else null
            if (validMac == null && flags == "0x0") return@forEachIndexed

            val state = if (flags == "0x2" || flags == "0x6") "REACHABLE" else "STALE"
            entries.add(
                RawNeighborEntry(
                    ipAddress = ip,
                    macAddress = validMac,
                    interfaceName = device,
                    arpState = state
                )
            )
        }
        return entries
    }

    fun parsePingLatencyMs(pingStdout: String): Int? {
        val match = PING_TIME_REGEX.find(pingStdout) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        return value.toInt().coerceAtLeast(1)
    }

    fun filterDevices(
        devices: List<ConnectedDevice>,
        searchQuery: String
    ): List<ConnectedDevice> {
        val q = searchQuery.trim().lowercase(Locale.US)
        if (q.isEmpty()) return devices.sortedBy { ipToSortableLong(it.ipAddress) }
        return devices.filter { device ->
            device.ipAddress.lowercase(Locale.US).contains(q) ||
                device.deviceName.lowercase(Locale.US).contains(q) ||
                device.macAddress?.lowercase(Locale.US)?.contains(q) == true
        }.sortedBy { ipToSortableLong(it.ipAddress) }
    }
}
