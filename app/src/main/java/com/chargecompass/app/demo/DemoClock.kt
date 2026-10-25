package com.chargecompass.app.demo

import java.time.Duration
import java.time.LocalDateTime

/** 시연용 시계. 낮에 PT를 해도 심야 이벤트(22~02시) 장면을 보여줄 수 있게 시각을 옮긴다. */
class DemoClock(private val real: () -> LocalDateTime = LocalDateTime::now) {
    @Volatile private var offset: Duration = Duration.ZERO

    fun now(): LocalDateTime = real().plus(offset)

    fun jumpTo(time: LocalDateTime) { offset = Duration.between(real(), time) }

    fun reset() { offset = Duration.ZERO }
}
