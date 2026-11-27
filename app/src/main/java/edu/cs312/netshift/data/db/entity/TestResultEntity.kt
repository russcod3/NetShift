package edu.cs312.netshift.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import edu.cs312.netshift.model.MeasurementConfig
import java.util.UUID

@Entity(
    tableName = "test_results",
    indices = [
        Index(value = ["startedAtEpochMs"]),
        Index(value = ["measuredTransport", "locationKey", "startedAtEpochMs"]),
    ],
)
data class TestResultEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long? = null,
    val durationMs: Long? = null,
    val locationName: String = "Unlabelled",
    val locationKey: String = "unlabelled",
    val status: String = "RUNNING",
    val failureReason: String? = null,
    val requestedTransport: String,
    val measuredTransport: String? = null,
    val networkHandle: Long? = null,
    val startIpAddressesJson: String? = null,
    val endIpAddressesJson: String? = null,
    val ssid: String? = null,
    val wifiRssiDbm: Int? = null,
    val wifiFrequencyMhz: Int? = null,
    val wifiLinkSpeedMbps: Int? = null,
    val cellularNetworkType: String? = null,
    val cellularSignalLevel: Int? = null,
    val carrierName: String? = null,
    val subscriptionId: Int? = null,
    val metadataAvailabilityJson: String = "{}",
    val isMetered: Boolean? = null,
    val wasValidatedAtStart: Boolean? = null,
    val endpointBaseUrl: String = MeasurementConfig.ENDPOINT,
    val serverAddress: String? = null,
    val profileId: String = MeasurementConfig.PROFILE_ID,
    val probeCountPlanned: Int = MeasurementConfig.PROBE_COUNT,
    val probeCountAttempted: Int = 0,
    val probeCountSucceeded: Int = 0,
    val probeCountFailed: Int = 0,
    val probeSamplesJson: String = "{\"version\":1,\"samples\":[]}",
    val latencyMedianMs: Double? = null,
    val jitterMs: Double? = null,
    val jitterPairCount: Int = 0,
    val probeLossPercent: Double? = null,
    val downloadBytes: Long = 0,
    val downloadBodyDurationMs: Double? = null,
    val downloadWallDurationMs: Long? = null,
    val downloadMbps: Double? = null,
    val downloadStopReason: String? = null,
    val measurementPayloadBytes: Long = 0,
    val qualityFlagsJson: String = "[]",
    val verdictRulesVersion: Int = 1,
    val activityVerdictsJson: String = "[]",
)
