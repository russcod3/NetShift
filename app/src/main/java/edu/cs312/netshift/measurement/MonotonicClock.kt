package edu.cs312.netshift.measurement

import android.os.SystemClock

/** Durations use [nowNs] only; [wallMs] is for display timestamps and must never be subtracted for durations. */
interface MonotonicClock {
    fun nowNs(): Long
    fun wallMs(): Long
}

class SystemMonotonicClock : MonotonicClock {
    override fun nowNs(): Long = SystemClock.elapsedRealtimeNanos()
    override fun wallMs(): Long = System.currentTimeMillis()
}
