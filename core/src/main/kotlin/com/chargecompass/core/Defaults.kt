package com.chargecompass.core

/** 설계 문서의 [제안] 기본값. 바꿀 때는 여기만 고친다. */
object Defaults {
    const val NEED_CHARGE_SOC = 15.0
    const val TARGET_SOC = 80.0
    const val DETOUR_TIME_WON_PER_MIN = 0
    const val VOICE_SUGGESTIONS = 1
    const val COMPARE_SUGGESTIONS = 3
    const val EFFICIENT_TRIP_MIN_KM = 10.0
    const val POINT_VALIDITY_MONTHS = 12L

    const val POINTS_EFFICIENT_TRIP = 10
    const val POINTS_NEW_STAMP = 20
    const val POINTS_CHARGE = 10

    const val STATION_MATCH_RADIUS_M = 100.0
    const val CHARGE_GAP_TOLERANCE_SEC = 60L
}
