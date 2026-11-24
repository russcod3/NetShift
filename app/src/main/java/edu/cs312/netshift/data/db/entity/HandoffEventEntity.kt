package edu.cs312.netshift.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "handoff_events",
    indices = [
        Index(value = ["detectedAtEpochMs"]),
        Index(value = ["sessionId", "detectedAtEpochMs"]),
    ],
)
data class HandoffEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val kind: String,
    val status: String = "PENDING",
    val detectedAtEpochMs: Long,
    val detectedElapsedMs: Long,
    val toAvailableAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null,
    val fromTransport: String,
    val toTransport: String = "NONE",
    val fromNetworkHandle: Long? = null,
    val toNetworkHandle: Long? = null,
    val fromIpAddressesJson: String? = null,
    val toIpAddressesJson: String? = null,
    val fromPrimaryIp: String? = null,
    val toPrimaryIp: String? = null,
    val fromValidated: Boolean? = null,
    val toValidated: Boolean? = null,
    val routeGapMs: Long? = null,
    val lastSuccessfulProbeElapsedMs: Long? = null,
    val firstFailedProbeElapsedMs: Long? = null,
    val firstRecoveredProbeElapsedMs: Long? = null,
    val observedInterruptionMs: Long? = null,
    val interruptionUpperBoundMs: Long? = null,
    val switchToReachableMs: Long? = null,
    val heartbeatIntervalMs: Int? = null,
    val timingQuality: String = "CALLBACK_ONLY",
    val linkedTestId: String? = null,
    val detailsJson: String = "{}",
)
