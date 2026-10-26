package com.chargecompass.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Connector { DC_COMBO, AC_TYPE1, AC_TYPE2, CHADEMO, NACS }

@Serializable
enum class ChargerStatus { AVAILABLE, IN_USE, BROKEN, MAINTENANCE, UNKNOWN }

@Serializable
data class VehicleModel(
    val id: String,
    val name: String,
    val batteryKwh: Double,
    val baseEfficiencyKmPerKwh: Double,
    val connectors: Set<Connector>,
    val vinPrefixes: List<String>,
)

@Serializable
data class Membership(val id: String, val operatorId: String, val name: String)

/** 사업자 요금표. [membershipIds]에 있는 멤버십을 가진 사람은 회원가를 받는다. */
@Serializable
data class Tariff(
    val operatorId: String,
    val memberWonPerKwh: Int,
    val roamingWonPerKwh: Int?,
    val nonMemberWonPerKwh: Int,
    val membershipIds: Set<String>,
)

/** [alongRouteKm]·[detourKm]는 현재 경로 기준 값이다. MVP에서는 시연 경로 기준으로 목업 데이터에 미리 넣는다. */
@Serializable
data class Station(
    val id: String,
    val name: String,
    val operatorId: String,
    val lat: Double,
    val lng: Double,
    val maxKw: Int,
    val connectors: Set<Connector>,
    val status: ChargerStatus,
    val alongRouteKm: Double,
    val detourKm: Double,
    /** 도감 지역 id(Dex.regions). */
    val region: String = "",
)

/** 레이드형 이벤트 충전소. 시간대는 22~2시처럼 자정을 넘을 수 있다. */
@Serializable
data class RewardEvent(
    val id: String,
    val stationId: String,
    val title: String,
    val startHour: Int,
    val endHour: Int,
    val multiplier: Int,
)

/** 사업자 데이(포켓몬 고의 커뮤니티 데이): 그날 그 사업자 충전소에서 충전하면 포인트 [multiplier]배와 한정 메달. 사업자가 비용을 내는 제휴 프로모션. */
data class OperatorDay(
    val id: String,
    val operatorId: String,
    val date: java.time.LocalDate,
    val multiplier: Int,
    val title: String,
)
