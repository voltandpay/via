package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chargecompass.app.AppContainer
import kotlinx.coroutines.launch

/** 첫 실행(정차 중): 차종과 보유 멤버십을 고르고, 어떤 데이터를 왜 읽는지 알린다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(c: AppContainer) {
    val scope = rememberCoroutineScope()
    var vehicle by remember { mutableStateOf<String?>(null) }
    var owned by remember { mutableStateOf(c.membership.owned.value) }
    Column(Modifier.fillMaxSize().padding(40.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Title("충전 나침반 시작하기")
        Caption("읽는 데이터: 배터리·전비·충전 상태·주행거리·위치 — 충전소 추천과 포인트 확인에만 씁니다")
        Body("내 차")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            c.data.vehicles.forEach { v ->
                FilterChip(selected = vehicle == v.id, onClick = { vehicle = v.id }, label = { Text(v.name, fontSize = 32.sp) }, modifier = Modifier.height(72.dp))
            }
        }
        Body("가지고 있는 충전 멤버십 (없으면 비회원가로 계산해요)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            c.data.memberships.forEach { m ->
                FilterChip(
                    selected = m.id in owned,
                    onClick = { owned = if (m.id in owned) owned - m.id else owned + m.id },
                    label = { Text(m.name, fontSize = 32.sp) },
                    modifier = Modifier.height(72.dp),
                )
            }
        }
        Button(
            onClick = { scope.launch { c.membership.set(owned); vehicle?.let { c.membership.setVehicle(it) } } },
            enabled = vehicle != null,
            modifier = Modifier.fillMaxWidth().height(88.dp),
        ) { Text("시작하기", fontSize = 36.sp) }
    }
}
