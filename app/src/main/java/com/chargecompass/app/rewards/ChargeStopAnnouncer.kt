package com.chargecompass.app.rewards

import com.chargecompass.app.ui.won
import com.chargecompass.core.Defaults
import com.chargecompass.core.charge.ChargeStopAdvice
import com.chargecompass.core.session.ChargeSession
import kotlin.math.roundToInt

/**
 * 충전 중 목적지까지 충분히 찼으면 "지금 끝내도 된다"를 충전 1회에 한 번 음성으로 알린다.
 * 충전하는 동안 차 밖에 있을 수 있어 화면만으로는 놓친다. 이미 충분한 상태로 꽂았으면 말하지 않는다.
 */
class ChargeStopAnnouncer {
    private var announcedFor: ChargeSession? = null

    fun onUpdate(session: ChargeSession, socPct: Int?, destination: String, advice: ChargeStopAdvice?): String? {
        if (advice == null || socPct == null || !advice.reached(socPct)) return null
        if (session.startSoc?.let(advice::reached) == true) return null
        if (announcedFor?.startedAt == session.startedAt) return null
        announcedFor = session
        return chargeStopSpeech(destination, advice)
    }
}

fun chargeStopSpeech(destination: String, a: ChargeStopAdvice): String =
    "지금 충전을 끝내도 ${destination}에 ${a.arrivalSoc}%로 도착해요. " +
        "${Defaults.TARGET_SOC.toInt()}%까지 채우면 ${a.extraMinutes}분, 약 ${won((a.extraWon / 100.0).roundToInt() * 100)} 더 들어요."
