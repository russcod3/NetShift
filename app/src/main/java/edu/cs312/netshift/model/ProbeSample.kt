package edu.cs312.netshift.model

/** An attempted probe. Unlaunched slots must not be represented as failed samples. */
data class ProbeSample(
    val index: Int,
    val rttMs: Double?,
    val failureReason: String? = null,
) {
    init {
        require(index >= 0) { "Probe index must be nonnegative" }
        require(rttMs == null || (rttMs.isFinite() && rttMs >= 0)) { "Invalid probe RTT" }
        require((rttMs != null && failureReason == null) ||
            (rttMs == null && !failureReason.isNullOrBlank())) {
            "A probe needs either an RTT or a failure reason"
        }
    }
}
