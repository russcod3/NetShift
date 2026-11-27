package edu.cs312.netshift.model

enum class NetworkTransport(val displayName: String) {
    WIFI("Wi-Fi"),
    CELLULAR("Mobile data"),
    VPN("VPN"),
    OTHER("Other network"),
    NONE("Offline"),
    UNKNOWN("Checking connection"),
}

data class NetworkSnapshot(
    val transport: NetworkTransport = NetworkTransport.UNKNOWN,
    val networkHandle: Long? = null,
    val ipAddresses: List<String> = emptyList(),
    val isValidated: Boolean = false,
    val isMetered: Boolean? = null,
    val isCaptivePortal: Boolean = false,
    val unavailableReason: String? = null,
)
