package com.chargecompass.core.session

import java.time.LocalDateTime

/** 충전 1회. [stationId]가 null이면 충전소 매칭에 실패한 것(보상 없음). */
data class ChargeSession(
    val stationId: String?,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val startSoc: Int?,
    val endSoc: Int?,
)
