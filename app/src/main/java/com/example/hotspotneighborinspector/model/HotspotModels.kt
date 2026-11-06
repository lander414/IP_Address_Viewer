package com.example.hotspotneighborinspector.model

data class NetworkInterfaceInfo(
    val interfaceName: String,
    val localIp: String,
    val subnetPrefix: String,
    val subnetMask: String,
    val cidrPrefix: Int,
    val isHotspotInterface: Boolean
) {
    val subnetDisplay: String
        get() = "$subnetPrefix.0/$cidrPrefix"
}

data class HotspotNetworkInfo(
    val isActive: Boolean = false,
    val interfaceName: String = "None",
    val localIp: String = "—",
    val subnetMask: String = "—",
    val subnetCidr: String = "—",
    val availableInterfaces: List<NetworkInterfaceInfo> = emptyList()
)

data class ConnectedDevice(
    val ipAddress: String,
    val deviceName: String,
    val macAddress: String? = null,
    val latencyMs: Int? = null,
    val isOnline: Boolean = true
)

enum class AutoRefreshInterval(val seconds: Int, val label: String) {
    OFF(0, "Off"),
    SECONDS_10(10, "10s"),
    SECONDS_30(30, "30s")
}
