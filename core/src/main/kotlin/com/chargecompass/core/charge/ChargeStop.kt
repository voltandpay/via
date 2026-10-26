package com.chargecompass.core.charge

import com.chargecompass.core.Defaults
import com.chargecompass.core.energy.EnergyState
import com.chargecompass.core.model.VehicleModel
import java.time.Month
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 충전 시간 근사. 급속(50kW 이상)은 80%까지 최대 출력의 [BULK], 그 위로는 [TAPER]만 받는다고 본다.
 * 겨울(12~2월)은 배터리가 차가워 출력이 더 떨어지므로 [WINTER]를 곱한다.
 * 실제 충전 곡선은 차종·충전기·배터리 온도마다 달라 근사값이다(Vehicle SDK에 배터리·외기 온도가 없다).
 */
object ChargeCurve {
    const val BULK = 0.85
    const val TAPER = 0.35
    const val WINTER = 0.8
    private const val TAPER_FROM = 80
    private const val DC_MIN_KW = 50

    fun isWinter(month: Month): Boolean = month == Month.DECEMBER || month == Month.JANUARY || month == Month.FEBRUARY

    fun minutes(fromSoc: Int, toSoc: Int, batteryKwh: Double, maxKw: Int, month: Month): Int {
        if (toSoc <= fromSoc || maxKw <= 0) return 0
        val power = maxKw * if (isWinter(month)) WINTER else 1.0
        val taper = if (maxKw >= DC_MIN_KW) TAPER else BULK   // 완속은 끝까지 거의 같은 출력
        val bulkKwh = (minOf(toSoc, TAPER_FROM) - fromSoc).coerceAtLeast(0) / 100.0 * batteryKwh
        val taperKwh = (toSoc - maxOf(fromSoc, TAPER_FROM)).coerceAtLeast(0) / 100.0 * batteryKwh
        return ceil((bulkKwh / (power * BULK) + taperKwh / (power * taper)) * 60).toInt()
    }
}

/**
 * 충전을 언제 끝내도 되는지. [enoughSoc]까지만 채우면 목적지에 [arrivalSoc]%로 도착하고,
 * 목표([Defaults.TARGET_SOC])까지 더 채우면 [extraMinutes]분·[extraWon]원이 더 든다.
 */
data class ChargeStopAdvice(
    val enoughSoc: Int,
    val minutesToEnough: Int,
    val arrivalSoc: Int,
    val extraMinutes: Int,
    val extraWon: Int,
    val winter: Boolean,
) {
    fun reached(socPct: Int): Boolean = socPct >= enoughSoc
}

object ChargeStopAdvisor {
    /** 목적지까지 [kmLeft]km를 가서 [arriveSoc]%가 남으려면 지금 몇 %가 필요한지(올림). */
    fun enoughSoc(kmLeft: Double, energy: EnergyState, car: VehicleModel, arriveSoc: Double = Defaults.NEED_CHARGE_SOC): Int {
        val eff = energy.efficiencyKmPerKwh?.takeIf { it > 0 } ?: car.baseEfficiencyKmPerKwh
        return ceil(kmLeft.coerceAtLeast(0.0) * 100.0 / (car.batteryKwh * eff) + arriveSoc).toInt()
    }

    /**
     * 남은 거리를 모르거나, 목표까지 채워야 목적지에 닿으면(장거리 중간 정류지) null — 일찍 끝낼 지점이 없다.
     * 이미 목표 이상이어도 null.
     */
    fun advise(
        energy: EnergyState, car: VehicleModel, kmLeft: Double?, maxKw: Int, wonPerKwh: Int, month: Month,
        target: Int = Defaults.TARGET_SOC.toInt(),
    ): ChargeStopAdvice? {
        val soc = energy.socPct ?: return null
        if (kmLeft == null || soc >= target) return null
        val enough = enoughSoc(kmLeft, energy, car)
        if (enough >= target) return null
        val stopAt = maxOf(soc, enough)
        val eff = energy.efficiencyKmPerKwh?.takeIf { it > 0 } ?: car.baseEfficiencyKmPerKwh
        val arrival = stopAt - kmLeft * 100.0 / (car.batteryKwh * eff)
        return ChargeStopAdvice(
            enoughSoc = enough,
            minutesToEnough = ChargeCurve.minutes(soc, enough, car.batteryKwh, maxKw, month),
            arrivalSoc = arrival.roundToInt(),
            extraMinutes = ChargeCurve.minutes(stopAt, target, car.batteryKwh, maxKw, month),
            extraWon = ((target - stopAt) / 100.0 * car.batteryKwh * wonPerKwh).roundToInt(),
            winter = ChargeCurve.isWinter(month),
        )
    }
}
