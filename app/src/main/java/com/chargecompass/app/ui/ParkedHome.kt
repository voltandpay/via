package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargecompass.app.AppContainer
import com.chargecompass.app.recommend.RecoState
import java.time.LocalDateTime

private val tabs = listOf("홈", "충전소 비교", "도감", "이벤트", "포인트")

/** 정차 중 화면: 하단 탭 5개, 한 번의 터치로 이동. */
@Composable
fun ParkedHome(c: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(40.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when (tab) {
                0 -> HomeTab(c)
                1 -> CompareScreen(c)
                2 -> StampBook(c)
                3 -> EventsScreen(c)
                else -> PointsExchange(c)
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = Surface) {
            tabs.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, modifier = Modifier.height(96.dp)) {
                    Text(label, fontSize = 32.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeTab(c: AppContainer) {
    val reco by c.reco.state.collectAsState()
    val entries by c.ledger.entries.collectAsState()
    val now = LocalDateTime.now()
    val weekPoints = entries.filter { it.at.isAfter(now.minusDays(7)) }.sumOf { it.points }

    (reco as? RecoState.AwaitingTouch)?.let { s ->
        Card {
            Caption("주행 중 추천한 충전소")
            Title(s.choice.station.name)
            Body("${s.choice.price.wonPerKwh}원/kWh · ${won(s.choice.savingWon)} 절약 예상")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { c.reco.confirmByTouch(s.choice.station.id) }, modifier = Modifier.fillMaxWidth().height(88.dp)) {
                Text("경유지로 추가", fontSize = 36.sp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(Modifier.weight(1f)) {
            Caption("이번 주 포인트")
            Title("${weekPoints}P", Accent)
            Caption("보유 ${c.ledger.balance(now)}P")
        }
        Card(Modifier.weight(1f)) {
            Caption("오늘의 이벤트 충전소")
            val event = c.data.events.firstOrNull()
            if (event == null) Body("진행 중인 이벤트가 없어요") else {
                Body(event.title)
                Caption("${event.startHour}시~${event.endHour}시 · 포인트 ${event.multiplier}배")
            }
        }
    }
    Card {
        when (val s = reco) {
            is RecoState.Added -> { Caption("경유지로 추가한 충전소"); Title(s.station.station.name, Accent) }
            is RecoState.Enough -> Body("지금 경로는 충전 없이 도착할 수 있어요")
            RecoState.NoneAvailable -> Body("경로 위에 쓸 수 있는 충전소가 없어요", Warn)
            else -> Body("목적지를 정하면 내 멤버십 기준으로 가장 싼 충전소를 찾아드려요")
        }
    }
}
