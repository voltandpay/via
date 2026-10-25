package com.chargecompass.core.session

import com.chargecompass.core.Defaults
import java.time.Duration
import java.time.LocalDateTime

data class ChargingSample(val at: LocalDateTime, val charging: Boolean, val socPct: Int?, val lat: Double?, val lng: Double?)

sealed interface SessionEvent {
    data class Started(val session: ChargeSession) : SessionEvent
    data class Ended(val session: ChargeSession) : SessionEvent
}

/**
 * 충전 여부 샘플을 세션으로 묶는다. 충전 신호가 [gapTolerance] 안에서 깜빡이면 같은 세션으로 본다
 * (그래야 스탬프·포인트가 두 번 쌓이지 않는다).
 */
class ChargeSessionDetector(
    private val stationMatcher: (lat: Double, lng: Double) -> String?,
    private val gapTolerance: Duration = Duration.ofSeconds(Defaults.CHARGE_GAP_TOLERANCE_SEC),
) {
    private var current: ChargeSession? = null
    private var lastChargingSoc: Int? = null
    private var stoppedAt: LocalDateTime? = null

    fun onSample(sample: ChargingSample): SessionEvent? {
        val session = current
        if (sample.charging) {
            stoppedAt = null
            if (session != null) {
                lastChargingSoc = sample.socPct ?: lastChargingSoc
                return null
            }
            lastChargingSoc = sample.socPct
            val stationId = if (sample.lat != null && sample.lng != null) stationMatcher(sample.lat, sample.lng) else null
            val started = ChargeSession(stationId, sample.at, null, sample.socPct, null)
            current = started
            return SessionEvent.Started(started)
        }
        if (session == null) return null
        val since = stoppedAt ?: sample.at.also { stoppedAt = it }
        if (Duration.between(since, sample.at) <= gapTolerance) return null
        current = null
        stoppedAt = null
        return SessionEvent.Ended(session.copy(endedAt = since, endSoc = lastChargingSoc))
    }
}
