package com.example.hotspotneighborinspector.network

import java.util.Locale

object OuiVendorLookup {

    private val ouiMap: Map<String, String> = mapOf(
        // Apple, Inc.
        "A4:83:E7" to "Apple, Inc.",
        "F0:18:98" to "Apple, Inc.",
        "BC:D0:74" to "Apple, Inc.",
        "DC:A9:04" to "Apple, Inc.",
        "AC:BC:32" to "Apple, Inc.",
        "3C:22:FB" to "Apple, Inc.",
        "14:7D:DA" to "Apple, Inc.",
        "70:56:81" to "Apple, Inc.",
        "8C:85:90" to "Apple, Inc.",
        "60:03:08" to "Apple, Inc.",
        "D0:81:7A" to "Apple, Inc.",
        "28:CF:E9" to "Apple, Inc.",
        "98:01:A7" to "Apple, Inc.",

        // Samsung Electronics
        "DC:EF:CA" to "Samsung Electronics",
        "E4:5E:37" to "Samsung Electronics",
        "5C:E8:EB" to "Samsung Electronics",
        "84:38:38" to "Samsung Electronics",
        "BC:14:85" to "Samsung Electronics",
        "A8:7C:01" to "Samsung Electronics",
        "C4:57:6E" to "Samsung Electronics",
        "38:D4:0B" to "Samsung Electronics",
        "F8:04:2E" to "Samsung Electronics",
        "78:47:1D" to "Samsung Electronics",

        // Google, Inc.
        "3C:5A:B4" to "Google LLC",
        "F4:F5:D8" to "Google LLC",
        "54:60:09" to "Google LLC",
        "94:EB:2C" to "Google LLC",
        "D8:6C:63" to "Google LLC",
        "A4:77:33" to "Google LLC",
        "48:D6:D5" to "Google LLC",

        // Espressif Inc. (ESP32 / ESP8266 IoT clients)
        "24:6F:28" to "Espressif Inc. (ESP32)",
        "30:AE:A4" to "Espressif Inc. (ESP32)",
        "A4:CF:12" to "Espressif Inc. (ESP32)",
        "CC:50:E3" to "Espressif Inc. (ESP32)",
        "84:CC:A8" to "Espressif Inc. (ESP32)",
        "EC:FA:BC" to "Espressif Inc. (ESP32)",
        "5C:CF:7F" to "Espressif Inc. (ESP8266)",
        "AC:67:B2" to "Espressif Inc. (ESP32)",

        // Raspberry Pi Trading
        "B8:27:EB" to "Raspberry Pi Foundation",
        "DC:A6:32" to "Raspberry Pi Trading",
        "E4:5F:01" to "Raspberry Pi Trading",
        "28:CD:C1" to "Raspberry Pi Trading",
        "D8:3A:DD" to "Raspberry Pi Trading",

        // Intel Corporate (Laptops / Wi-Fi AX200/AX210/BE200)
        "8C:8D:28" to "Intel Corporate",
        "A4:34:D9" to "Intel Corporate",
        "40:74:E0" to "Intel Corporate",
        "3C:9C:0F" to "Intel Corporate",
        "F8:63:3F" to "Intel Corporate",
        "00:1B:21" to "Intel Corporate",
        "AC:ED:5C" to "Intel Corporate",
        "B4:69:21" to "Intel Corporate",

        // Xiaomi Communications
        "64:CC:2E" to "Xiaomi Communications",
        "28:6C:07" to "Xiaomi Communications",
        "7C:1D:D9" to "Xiaomi Communications",
        "F8:A4:5F" to "Xiaomi Communications",
        "50:64:2B" to "Xiaomi Communications",

        // OnePlus / OPPO / Realme
        "94:65:2D" to "OnePlus Technology",
        "C0:EE:FB" to "OnePlus Technology",
        "64:A2:F9" to "OnePlus Technology",
        "A0:91:69" to "Guangdong Oppo Mobile",

        // Sony / PlayStation
        "00:D9:D1" to "Sony Interactive Entertainment",
        "70:9E:29" to "Sony Interactive Entertainment",
        "FC:0F:E6" to "Sony Corporation",

        // Nintendo
        "98:B6:E9" to "Nintendo Co., Ltd.",
        "04:03:D6" to "Nintendo Co., Ltd.",
        "D8:6B:F7" to "Nintendo Co., Ltd.",

        // Microsoft / Surface / Xbox
        "60:45:BD" to "Microsoft Corporation",
        "28:18:78" to "Microsoft Corporation",
        "7C:1E:52" to "Microsoft Corporation",

        // Qualcomm / Realtek / MediaTek / Broadcom
        "00:E0:4C" to "Realtek Semiconductor",
        "52:54:00" to "QEMU / KVM Virtual NIC",
        "02:15:B2" to "Android Emulator Virtual Wi-Fi",
        "00:A0:C9" to "Intel Pro/1000",
        "88:D7:F6" to "ASUSTek Computer",
        "30:5A:3A" to "ASUSTek Computer",
        "B0:6E:BF" to "TP-Link Technologies",
        "50:C7:BF" to "TP-Link Technologies",
        "F0:9F:C2" to "Ubiquiti Networks",
        "FC:EC:DA" to "Ubiquiti Networks",
        "F8:B1:56" to "Dell Inc.",
        "D4:BE:D9" to "Dell Inc.",
        "60:F6:77" to "Lenovo",
        "E8:6A:64" to "Lenovo",
        "3C:D9:2B" to "Hewlett Packard",
        "44:65:0D" to "Amazon Technologies",
        "FC:65:DE" to "Amazon Technologies"
    )

    fun normalizeMac(rawMac: String): String {
        val cleaned = rawMac.trim()
            .replace("-", ":")
            .uppercase(Locale.US)
        val parts = cleaned.split(":")
        if (parts.size != 6) return cleaned
        return parts.joinToString(":") { part ->
            part.padStart(2, '0').takeLast(2)
        }
    }

    fun isValidClientMac(rawMac: String): Boolean {
        val mac = normalizeMac(rawMac)
        if (mac.length != 17) return false
        if (mac == "00:00:00:00:00:00" || mac == "FF:FF:FF:FF:FF:FF") return false
        val octets = mac.split(":")
        if (octets.size != 6) return false
        return octets.all { octet ->
            octet.length == 2 && octet.all { ch ->
                ch in '0'..'9' || ch in 'A'..'F'
            }
        }
    }

    /**
     * Checks if the MAC address has the Locally Administered bit (bit 1 of first octet) set.
     * Modern Android 10+ and iOS 14+ devices default to randomized MAC addresses per Wi-Fi SSID,
     * where the second hex digit of the first octet is 2, 6, A, or E.
     */
    fun isLocallyAdministeredMac(rawMac: String): Boolean {
        val mac = normalizeMac(rawMac)
        if (!isValidClientMac(mac)) return false
        val firstOctet = mac.substring(0, 2).toIntOrNull(16) ?: return false
        // Bit 0 is multicast, Bit 1 (0x02) is Locally Administered
        return (firstOctet and 0x02) != 0 && (firstOctet and 0x01) == 0
    }

    fun lookupVendor(rawMac: String): String? {
        val mac = normalizeMac(rawMac)
        if (!isValidClientMac(mac)) return null
        val prefix = mac.substring(0, 8)
        return ouiMap[prefix]
    }
}

