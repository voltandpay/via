package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.DataMode
import com.chargecompass.app.drivestate.DriveState
import com.chargecompass.app.navi.PleosNaviRepository
import com.chargecompass.app.navi.RouteSnapshot
import com.chargecompass.app.vehicle.TripSummary
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch
import com.chargecompass.app.rewards.ChargeSummary

/** 디버그 빌드 전용 시연 패널. Mock 모드에서 차량·경로 상태를 직접 바꾼다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugPanel(c: AppContainer, onSwitchMode: (DataMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val energy by c.vehicle.energy.collectAsState()
    val drive by c.drive.state.collectAsState()
    val route by c.navi.route.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        if (!open) {
            TextButton(onClick = { open = true }) { Text("DEV", color = Color(0xFF8B95A3), fontSize = 18.sp) }
            return@Box
        }
        Column(
            Modifier.width(560.dp).padding(16.dp).background(Color(0xEE000000), RoundedCornerShape(16.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("시연 패널 · ${c.mode} · $drive", color = Color.White, fontSize = 20.sp)
            Text("SoC ${energy.socPct} · 경로 ${route?.destinationName ?: "-"} ${route?.remainingKm ?: ""}km", color = Color(0xFF8B95A3), fontSize = 18.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val other = if (c.mode == DataMode.MOCK) DataMode.PLEOS else DataMode.MOCK
                OutlinedButton(onClick = { onSwitchMode(other) }) { Text("$other 모드로") }
                OutlinedButton(onClick = { open = false }) { Text("닫기") }
                OutlinedButton(onClick = { c.demoClock.jumpTo(LocalDate.now().atTime(23, 30)) }) { Text("밤 11:30으로") }
                OutlinedButton(onClick = { c.demoClock.reset() }) { Text("실제 시각") }
                c.data.operatorDays.firstOrNull()?.let { d -> OutlinedButton(onClick = { c.demoClock.jumpTo(d.date.atTime(12, 0)) }) { Text("${d.title}로") } }
                OutlinedButton(onClick = { c.narrowPreview.value = !c.narrowPreview.value }) { Text("좁은 화면") }
                OutlinedButton(onClick = { c.flatBadges.value = !c.flatBadges.value }) { Text("평면 배지") }
                // 시연용: 지난 6일 충전 요약 예시(홈의 7일 차트가 비어 보이지 않게). 실제 기록 파일에 남는다
                OutlinedButton(onClick = { scope.launch { val have = c.summaries.summaries.value; demoWeekSummaries(c.demoClock.now()).filter { d -> have.none { it.endedAt == d.endedAt } }.forEach { c.summaries.append(it) } } }) { Text("지난 6일 예시 기록") }
                // 시연용: 지난주들에 미션 6개를 달성한 것처럼 기록 → 이번 주 미션 하나만 더 하면 미션 상자
                OutlinedButton(onClick = { scope.launch { c.ledger.append(demoPastMissions(c.demoClock.now(), c.ledger.entries.value)) } }) { Text("미션 스탬프 6개") }
                // 같은 충전소 보상은 2시간에 한 번이라, 메달 단계(동→은→금)를 보여 줄 때는 하루씩 넘긴다
                OutlinedButton(onClick = { c.demoClock.jumpTo(c.demoClock.now().plusDays(1)) }) { Text("하루 뒤로") }
                // 가는 도중 상태 변화 시연: 1위 충전소가 사용 중이 된다 / 되돌린다
                OutlinedButton(onClick = { c.statusBoard.set("st-yangjae-chaevi", com.chargecompass.core.model.ChargerStatus.IN_USE) }) { Text("양재 채비 사용 중") }
                OutlinedButton(onClick = { c.statusBoard.clear() }) { Text("상태 되돌리기") }
                // 월간 절약 리포트 시연: 다음 달 1일 아침으로(지난달 = 이번 달 기록)
                OutlinedButton(onClick = { c.demoClock.jumpTo(c.demoClock.now().toLocalDate().withDayOfMonth(1).plusMonths(1).atTime(8, 0)) }) { Text("다음 달 1일로") }
                (c.navi as? PleosNaviRepository)?.let { navi ->
                    OutlinedButton(onClick = { navi.requestDemoRoute() }) { Text("서울역 경로 요청") }
                    OutlinedButton(onClick = { navi.requestLongTripRoute() }) { Text("부산역 경로 요청") }
                }
            }
            if (c.mode == DataMode.MOCK) {
                val v = c.mockVehicle
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { c.toggleDrive.set(DriveState.DRIVING) }) { Text("주행") }
                    OutlinedButton(onClick = { c.toggleDrive.set(DriveState.PARKED) }) { Text("정차") }
                    OutlinedButton(onClick = { v.setSoc(15) }) { Text("SoC 15") }
                    OutlinedButton(onClick = { v.setSoc(25) }) { Text("SoC 25") }
                    OutlinedButton(onClick = { v.setSoc(80) }) { Text("SoC 80") }
                    OutlinedButton(onClick = { c.mockNavi.setRoute(RouteSnapshot(1, "서울역", 23.0, 2040)) }) { Text("서울역 안내") }
                    // 장거리 다중 충전 시연: 410km, 충전 2~3번
                    OutlinedButton(onClick = { v.setSoc(40); c.mockNavi.setRoute(RouteSnapshot(9, "부산역", 410.0, 16_200)) }) { Text("부산 안내(SoC 40)") }
                    OutlinedButton(onClick = { c.mockNavi.setRoute(null) }) { Text("안내 종료") }
                    OutlinedButton(onClick = {
                        c.data.stations.first { it.id == "st-yangjae-chaevi" }.let { v.setLocation(it.lat, it.lng) }
                    }) { Text("양재 채비 도착") }
                    OutlinedButton(onClick = { v.setCharging(true) }) { Text("충전 시작") }
                    OutlinedButton(onClick = { v.endCharging() }) { Text("충전 끝") }
                    OutlinedButton(onClick = { v.endTrip(TripSummary(12.0, 5.6, c.demoClock.now())) }) { Text("효율 트립 종료") }
                    OutlinedButton(onClick = { scope.launch { c.tripLog.append(demoUsualTrips(c.demoClock.now())) } }) { Text("평소 경로 4회 기록") }
                    OutlinedButton(onClick = { v.setDriverSeated(if (v.driverSeated.value == false) true else false) }) { Text("운전석 비움/앉음") }
                }
            }
        }
    }
}

/** 지난 6일(오늘 제외) 중 4일에 충전한 예시 요약. */
internal fun demoWeekSummaries(now: LocalDateTime): List<ChargeSummary> = listOf(
    Triple(6, "st-pangyo-skel", 2860), Triple(4, "st-yangjae-chaevi", 4120), Triple(3, "st-banpo-kepco", 1980), Triple(1, "st-seocho-evsis", 3350),
).map { (back, id, saving) -> ChargeSummary(stationNames[id] ?: id, 32.0, 9800, saving, 10, now.minusDays(back.toLong()).withHour(19).withMinute(0), id) }

private val stationNames = mapOf(
    "st-pangyo-skel" to "판교테크노밸리 SK일렉링크", "st-yangjae-chaevi" to "양재 채비 충전소",
    "st-banpo-kepco" to "반포 한전 충전소", "st-seocho-evsis" to "서초 이브이시스",
)

/** 지난 6주에 미션을 하나씩 달성한 기록(미션 상자 시연용). */
internal fun demoPastMissions(now: LocalDateTime, existing: List<com.chargecompass.core.rewards.RewardEntry>): List<com.chargecompass.core.rewards.RewardEntry> = (1..6).map { w ->
    val at = now.minusWeeks(w.toLong())
    com.chargecompass.core.rewards.RewardEntry(com.chargecompass.core.rewards.RewardKind.MISSION, 15, at,
        eventId = com.chargecompass.core.missions.Mission.EFFICIENT_TRIP.name, eventDate = com.chargecompass.core.missions.Missions.weekStart(at.toLocalDate()))
}.filter { e -> existing.none { it.kind == e.kind && it.eventId == e.eventId && it.eventDate == e.eventDate } }   // 두 번 눌러도 중복되지 않게

/** 시연용: 지난 4주 같은 요일·시각에 판교 → 서울역(23km)을 내비 목적지로 간 주행. */
internal fun demoUsualTrips(now: LocalDateTime): List<com.chargecompass.core.usual.TripRecord> = (1..4).map { w ->
    com.chargecompass.core.usual.TripRecord(37.4128, 127.0974, 37.5547, 126.9707, 23.0, now.minusWeeks(w.toLong()), "서울역")
}
