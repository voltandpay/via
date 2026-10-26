package com.chargecompass.core

/** 설계 문서의 [제안] 기본값. 바꿀 때는 여기만 고친다. */
object Defaults {
    const val NEED_CHARGE_SOC = 15.0
    const val TARGET_SOC = 80.0
    /** 이보다 낮은 잔량으로만 닿는 충전소는 추천하지 않는다(전비 오차 여유). */
    const val MIN_ARRIVAL_SOC = 5.0
    const val COMPARE_SUGGESTIONS = 3
    const val EFFICIENT_TRIP_MIN_KM = 10.0
    const val POINT_VALIDITY_MONTHS = 12L

    const val POINTS_EFFICIENT_TRIP = 10
    const val POINTS_NEW_STAMP = 20
    const val POINTS_CHARGE = 10
    const val POINTS_REGION_COMPLETE = 50
    /** 같은 충전소에서 이 시간 안에 다시 시작한 충전은 같은 방문(보상 한 번). */
    const val CHARGE_REWARD_COOLDOWN_MIN = 120L
    const val POINTS_MISSION = 15
    const val POINTS_BREAKTHROUGH = 100
    const val MISSIONS_PER_BREAKTHROUGH = 7

    /** 경부하 시간대(시연용 예시: 23~9시). 이때 충전을 시작하면 충전 포인트 2배. 실제 시간대별 요금 확인 필요. */
    const val OFF_PEAK_START_HOUR = 23
    const val OFF_PEAK_END_HOUR = 9

    const val STATION_MATCH_RADIUS_M = 100.0
    const val CHARGE_GAP_TOLERANCE_SEC = 60L
}
