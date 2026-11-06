package com.example.hotspotneighborinspector

import com.example.hotspotneighborinspector.model.ConnectedDevice
import com.example.hotspotneighborinspector.network.ArpAndNeighborParser
import com.example.hotspotneighborinspector.network.OuiVendorLookup
import org.junit.Assert.assertEquals
import org.junit.Test

class ArpAndNeighborParserTest {

    @Test
    fun parseIpNeighOutput_extractsReachableAndStaleNeighbors() {
        val sampleOutput = """
            192.168.43.102 dev swlan0 lladdr a4:83:e7:4b:12:9c REACHABLE
            192.168.43.142 dev swlan0 REACHABLE
            192.168.43.250 dev swlan0 FAILED
        """.trimIndent()

        val entries = ArpAndNeighborParser.parseIpNeighOutput(sampleOutput)
        assertEquals(2, entries.size)
        assertEquals("192.168.43.102", entries[0].ipAddress)
        assertEquals("A4:83:E7:4B:12:9C", entries[0].macAddress)
        assertEquals("192.168.43.142", entries[1].ipAddress)
    }

    @Test
    fun filterDevices_filtersAndSortsByIp() {
        val devices = listOf(
            ConnectedDevice(
                ipAddress = "192.168.43.154",
                deviceName = "ThinkPad",
                latencyMs = 15
            ),
            ConnectedDevice(
                ipAddress = "192.168.43.102",
                deviceName = "MacBook",
                latencyMs = 8
            )
        )

        val sortedAll = ArpAndNeighborParser.filterDevices(devices, "")
        assertEquals("192.168.43.102", sortedAll[0].ipAddress)
        assertEquals("192.168.43.154", sortedAll[1].ipAddress)

        val filtered = ArpAndNeighborParser.filterDevices(devices, "154")
        assertEquals(1, filtered.size)
        assertEquals("192.168.43.154", filtered[0].ipAddress)
    }

    @Test
    fun ouiVendorLookup_normalizesMac() {
        assertEquals("A4:83:E7:4B:12:9C", OuiVendorLookup.normalizeMac("a4-83-e7-4b-12-9c"))
    }
}
