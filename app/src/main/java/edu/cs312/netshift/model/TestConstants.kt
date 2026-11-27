package edu.cs312.netshift.model

/** String constants for the enum-like columns in schema v1 (stored as TEXT, never as ordinals). */
object TestStatus {
    const val RUNNING = "RUNNING"
    const val COMPLETE = "COMPLETE"
    const val PARTIAL = "PARTIAL"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val ABORTED = "ABORTED"
}

object TransportKey {
    const val WIFI = "WIFI"
    const val CELLULAR = "CELLULAR"
}

object FailureReason {
    const val NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE"
    const val NETWORK_CHANGED = "NETWORK_CHANGED"
    const val ENDPOINT_ERROR = "ENDPOINT_ERROR"
    const val ENDPOINT_UNREACHABLE = "ENDPOINT_UNREACHABLE"
    const val DEADLINE = "DEADLINE"
    const val PROCESS_ENDED = "PROCESS_ENDED"
    const val CAPTIVE_PORTAL = "CAPTIVE_PORTAL"
    const val UNSUPPORTED_VPN = "UNSUPPORTED_VPN"
    const val UNSUPPORTED_PROXY = "UNSUPPORTED_PROXY"
    const val TRANSPORT_MISMATCH = "TRANSPORT_MISMATCH"
    const val PERMISSION_DENIED = "PERMISSION_DENIED"
    const val USER_CANCEL = "USER_CANCEL"
    const val ALREADY_RUNNING = "ALREADY_RUNNING"
    const val SAVE_FAILED = "SAVE_FAILED"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}

object StopReason {
    const val EOF = "EOF"
    const val BYTE_LIMIT = "BYTE_LIMIT"
    const val TIME_LIMIT = "TIME_LIMIT"
    const val NETWORK_LOST = "NETWORK_LOST"
    const val IO_ERROR = "IO_ERROR"
    const val HTTP_ERROR = "HTTP_ERROR"
    const val USER_CANCEL = "USER_CANCEL"
}

object QualityFlag {
    const val SHORT_DOWNLOAD = "SHORT_DOWNLOAD"
    const val SPARSE_PROBES = "SPARSE_PROBES"
    const val ENDPOINT_ERROR = "ENDPOINT_ERROR"
    const val DEFAULT_ROUTE_CHANGED = "DEFAULT_ROUTE_CHANGED"
}
