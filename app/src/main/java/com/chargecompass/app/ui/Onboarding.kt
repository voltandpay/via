package com.chargecompass.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import kotlinx.coroutines.launch

/** 첫 실행(정차 중): 차종과 보유 멤버십을 고르고, 어떤 데이터를 왜 읽는지 알린다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(c: AppContainer) {
    val scope = rememberCoroutineScope()
    val col = colors
    var vehicle by remember { mutableStateOf<String?>(null) }
    var owned by remember { mutableStateOf(c.membership.owned.value) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Prop(com.chargecompass.app.R.drawable.prop_symbol, 140.dp)
            Column {
                T("VIA 시작하기", ViaType.titleL)
                T("가는 길에, 가장 싸게, 차가 확인한 보상", ViaType.bodyM, col.text2.c())
            }
        }
        T("읽는 데이터: 배터리·전비·충전 상태·주행거리·위치 — 충전소 추천과 포인트 확인에만 씁니다", ViaType.labelM, col.text3.c())
        T("내 차", ViaType.titleS)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            c.data.vehicles.forEach { v -> ChoiceChip(v.name, vehicle == v.id) { vehicle = v.id } }
        }
        T("가지고 있는 충전 멤버십", ViaType.titleS)
        T("없으면 비회원가로 계산해요", ViaType.labelM, col.text3.c())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            c.data.memberships.forEach { m -> MembershipCard(m.name, m.operatorId, m.id in owned) { owned = if (m.id in owned) owned - m.id else owned + m.id } }
        }
        ViaButton(
            "시작하기", enabled = vehicle != null, modifier = Modifier.fillMaxWidth(),
            onClick = { scope.launch { c.membership.set(owned); vehicle?.let { c.membership.setVehicle(it) } } },
        )
    }
}
