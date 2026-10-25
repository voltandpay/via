package com.chargecompass.core.data

import com.chargecompass.core.model.Membership
import com.chargecompass.core.model.RewardEvent
import com.chargecompass.core.model.Station
import com.chargecompass.core.model.Tariff
import com.chargecompass.core.model.VehicleModel
import kotlinx.serialization.json.Json
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class MockData(
    val vehicles: List<VehicleModel>,
    val tariffs: Map<String, Tariff>,
    val stations: List<Station>,
    val events: List<RewardEvent>,
    val memberships: List<Membership>,
)

/** 리소스 읽는 방법은 호출 쪽이 정한다 (JVM 테스트는 클래스로더, 앱은 같은 클래스로더나 assets). */
object MockDataLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(read: (path: String) -> String): MockData = MockData(
        vehicles = json.decodeFromString(read("mock/vehicles.json")),
        tariffs = json.decodeFromString<List<Tariff>>(read("mock/operators_tariffs.json")).associateBy { it.operatorId },
        stations = json.decodeFromString(read("mock/stations_demo_route.json")),
        events = json.decodeFromString(read("mock/events.json")),
        memberships = json.decodeFromString(read("mock/memberships.json")),
    )
}

/** [radiusM] 안에서 가장 가까운 충전소 id. 없으면 null. */
fun nearestStation(stations: List<Station>, lat: Double, lng: Double, radiusM: Double = 100.0): String? =
    stations.map { it.id to distanceM(lat, lng, it.lat, it.lng) }
        .filter { it.second <= radiusM }
        .minByOrNull { it.second }?.first

private fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}
