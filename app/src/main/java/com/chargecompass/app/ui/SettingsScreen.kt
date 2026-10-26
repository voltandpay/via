package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chargecompass.app.AppContainer
import kotlinx.coroutines.launch

/** 정차 중 설정: 차종과 보유 멤버십을 바꾼다(온보딩 이후에도 카드가 바뀔 수 있다). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(c: AppContainer, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val col = colors
    var vehicle by remember { mutableStateOf(c.membership.vehicleModelId.value) }
    var owned by remember { mutableStateOf(c.membership.owned.value) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).pressable(onClick = onClose), contentAlignment = Alignment.Center) {
                CloseIcon(color = col.text2.c())
            }
            T("설정", ViaType.titleL)
        }
        T("내 차", ViaType.titleS)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            c.data.vehicles.forEach { v -> ChoiceChip(v.name, vehicle == v.id) { vehicle = v.id } }
        }
        T("가지고 있는 충전 멤버십", ViaType.titleS)
        T("새로 만든 카드를 추가하면 다음 추천부터 회원가로 계산해요", ViaType.labelM, col.text3.c())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            c.data.memberships.forEach { m -> MembershipCard(m.name, m.operatorId, m.id in owned) { owned = if (m.id in owned) owned - m.id else owned + m.id } }
        }
        ViaButton(
            "저장", modifier = Modifier.fillMaxWidth(), enabled = vehicle != null,
            onClick = { scope.launch { c.membership.set(owned); vehicle?.let { c.membership.setVehicle(it) }; onClose() } },
        )
    }
}

/** 선택 칩(Pleos Toggle button 형태): 선택되면 파란 테두리·옅은 파란 바탕. */
/** 충전 멤버십 카드(3D 렌더): 가진 카드는 또렷하게 + 파란 테두리·체크, 없는 카드는 흐리게. */
@Composable
fun MembershipCard(name: String, operatorId: String, selected: Boolean, onClick: () -> Unit) {
    val col = colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.width(200.dp).clip(shape)
            .background(if (selected) col.posSoft.c() else col.card.c())
            .border(if (selected) 3.dp else 2.dp, if (selected) col.pos.c() else col.line.c(), shape)
            .pressable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            membershipCardRes(operatorId)?.let { Prop(it, 150.dp, alpha = if (selected) 1f else if (col.isDark) 0.7f else 0.45f) }
                ?: StationBadge(operatorId, name, selected, size = 96.dp)
            if (selected) Box(Modifier.size(36.dp).clip(CircleShape).background(col.pos.c()), contentAlignment = Alignment.Center) {
                T("✓", ViaType.labelM.copy(fontWeight = FontWeight.Bold), androidx.compose.ui.graphics.Color.White)
            }
        }
        T(name, ViaType.labelM.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal), if (selected) col.pos.c() else col.text2.c(), maxLines = 1)
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val col = colors
    val shape = RoundedCornerShape(20.dp)
    T(
        label, ViaType.bodyM.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        if (selected) col.pos.c() else col.text.c(),
        Modifier.clip(shape)
            .background(if (selected) col.posSoft.c() else col.card.c())
            .border(if (selected) 3.dp else 2.dp, if (selected) col.pos.c() else col.line.c(), shape)
            .pressable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 20.dp),
    )
}

/** 빈 화면: 아이콘 + 무엇이 보일지 + 다음에 할 일. */
@Composable
fun EmptyState(icon: @Composable () -> Unit, title: String, body: String) {
    val col = colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(col.card.c()).padding(vertical = 64.dp, horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(col.posSoft.c()), contentAlignment = Alignment.Center) { icon() }
        T(title, ViaType.titleS)
        T(body, ViaType.bodyM, col.text2.c())
    }
}
