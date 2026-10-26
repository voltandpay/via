package com.chargecompass.app.navi

import com.chargecompass.core.model.ChargerStatus
import com.chargecompass.core.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 충전기 실시간 상태. 본선에서는 공공 충전소 상태 API로 채우고, 1차는 시연 패널에서 바꾼다.
 * 목업 데이터의 상태 위에 덮어쓴다.
 */
class StationStatusBoard {
    private val _overrides = MutableStateFlow<Map<String, ChargerStatus>>(emptyMap())
    val overrides: StateFlow<Map<String, ChargerStatus>> = _overrides

    fun set(stationId: String, status: ChargerStatus) { _overrides.value = _overrides.value + (stationId to status) }
    fun clear() { _overrides.value = emptyMap() }

    fun apply(stations: List<Station>): List<Station> {
        val o = _overrides.value
        return if (o.isEmpty()) stations else stations.map { s -> o[s.id]?.let { s.copy(status = it) } ?: s }
    }
}
