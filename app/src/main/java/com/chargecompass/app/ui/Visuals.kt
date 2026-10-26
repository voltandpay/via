package com.chargecompass.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val viaBlue = Color(0xFF0064FF)

/** VIA 로고: 앱 아이콘과 같은 모티프(출발점 → 충전 경유지(번개) → 목적지). */
@Composable
fun ViaLogo(size: Dp = 56.dp) {
    Canvas(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(viaBlue)) {
        val s = this.size.width / 108f
        // 안전 영역(가운데 66)만 쓰는 런처 아이콘보다 크게: 108 격자를 1.45배 확대해 가운데 맞춤
        val k = 1.45f
        fun p(x: Float, y: Float) = Offset((54 + (x - 54) * k) * s, (54 + (y - 54) * k) * s)
        val road = Path().apply {
            val a = p(33f, 76f); moveTo(a.x, a.y)
            val c1 = p(33f, 60f); val c2 = p(48f, 62f); val b = p(54f, 54f); cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
            val c3 = p(60f, 46f); val c4 = p(75f, 48f); val e = p(75f, 32f); cubicTo(c3.x, c3.y, c4.x, c4.y, e.x, e.y)
        }
        drawPath(road, Color.White, style = Stroke(5f * s * k, cap = StrokeCap.Round))
        drawCircle(Color.White, 5f * s * k, p(33f, 76f))
        val d = p(70f, 27f); drawRect(Color.White, d, Size(10f * s * k, 10f * s * k))
        drawCircle(Color.White, 13f * s * k, p(54f, 54f))
        val bolt = Path().apply {
            listOf(56f to 44f, 47f to 56f, 53f to 56f, 52f to 64f, 61f to 52f, 55f to 52f).forEachIndexed { i, (x, y) ->
                val q = p(x, y); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
            }
            close()
        }
        drawPath(bolt, viaBlue)
    }
}

/** 로고 + VIA 글자. */
@Composable
fun ViaWordmark(size: Dp = 56.dp, showText: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(size * 0.28f)) {
        ViaLogo(size)
        if (showText) T("VIA", TextStyle(fontSize = (size.value * 0.68f).sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp))
    }
}

/**
 * 원형 게이지: [fraction] 만큼 채우고 [marker] 위치(0~1)에 목표 눈금. 270도 호(아래가 비는 계기판 모양).
 * [animate]이면 0에서 차오른다(정차·충전 중에만 — 주행 중 애니메이션 금지).
 */
@Composable
fun RingGauge(
    fraction: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 280.dp, stroke: Dp = 28.dp,
    marker: Float? = null, animate: Boolean = true, content: @Composable BoxScope.() -> Unit = {},
) {
    val c = colors
    val anim = remember { Animatable(if (animate) 0f else fraction) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), tween(if (animate) 900 else 0, easing = FastOutSlowInEasing)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(c.track.c(), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (anim.value > 0f) drawArc(color, 135f, 270f * anim.value, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (marker != null) {
                val ang = Math.toRadians(135.0 + 270.0 * marker)
                val r = (this.size.width - w) / 2
                val cx = this.size.width / 2; val cy = this.size.height / 2
                val o = Offset((cx + (r + w * 0.75f) * Math.cos(ang)).toFloat(), (cy + (r + w * 0.75f) * Math.sin(ang)).toFloat())
                val i = Offset((cx + (r - w * 0.75f) * Math.cos(ang)).toFloat(), (cy + (r - w * 0.75f) * Math.sin(ang)).toFloat())
                drawLine(c.text.c(), i, o, 5.dp.toPx(), StrokeCap.Round)
            }
        }
        content()
    }
}

/** 0부터 [value]까지 올라가는 숫자(정차·충전 중의 보상 장면). */
@Composable
fun CountUpWon(value: Int, style: TextStyle, color: Color, animate: Boolean = true, suffix: String = "원") {
    val anim = remember { Animatable(if (animate) 0f else value.toFloat()) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(if (animate) 1200 else 0, easing = FastOutSlowInEasing)) }
    T(won(anim.value.toInt()).removeSuffix("원") + suffix, style, color)
}

/** 사업자별 배지 색(Pleos 의미 색과 겹치지 않게 채도를 맞춘 10색). */
private val badgePalette = listOf(
    0xFF2F6BFF, 0xFF00A86B, 0xFF6A4CFF, 0xFFF28C28, 0xFF00A3A3, 0xFFE5487D, 0xFF3D5A99, 0xFFA0642D, 0xFFD9480F, 0xFF5C6370,
)
fun badgeColor(key: String): Color = Color(badgePalette[Math.floorMod(key.hashCode(), badgePalette.size)])

/** 도감 배지: 모으면 사업자 색 원에 첫 글자, 못 모았으면 점선 원에 자물쇠. 방금 모았으면 톡 커지며 나타난다. */
@Composable
fun StationBadge(initial: String, color: Color, collected: Boolean, fresh: Boolean = false, size: Dp = 112.dp) {
    val c = colors
    val pop = remember { Animatable(if (fresh) 0.4f else 1f) }
    LaunchedEffect(fresh) { if (fresh) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.size(size).scale(pop.value), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.width / 2
            if (collected) {
                drawCircle(color, r)
                drawCircle(Color.White.copy(alpha = 0.35f), r - 6.dp.toPx(), style = Stroke(3.dp.toPx()))
            } else {
                drawCircle(c.line.c(), r - 2.dp.toPx(), style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
            }
        }
        if (collected) T(initial, ViaType.titleM, Color.White) else LockIcon(color = c.text3.c())
    }
}

@Composable
fun LockIcon(size: Dp = 36.dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val st = Stroke(2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(color, Offset(5 * s, 11 * s), Size(14 * s, 10 * s), CornerRadius(2 * s), style = st)
        drawArc(color, 180f, 180f, false, Offset(8 * s, 4 * s), Size(8 * s, 12 * s), style = st)
    }
}

/** 24시간 띠: 이벤트 시간대를 칠하고 지금 시각에 세로선. 아래에 0·6·12·18·24시. */
@Composable
fun DayTimeline(startHour: Int, endHour: Int, nowFraction: Float, color: Color, modifier: Modifier = Modifier) {
    val c = colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val h = this.size.height
            drawRoundRect(c.track.c(), size = this.size, cornerRadius = CornerRadius(h / 2))
            dayWindowSegments(startHour, endHour).forEach { (a, b) ->
                drawRoundRect(color, Offset(this.size.width * a, 0f), Size(this.size.width * (b - a), h), CornerRadius(h / 2))
            }
            val x = this.size.width * nowFraction
            drawLine(c.text.c(), Offset(x, -6.dp.toPx()), Offset(x, h + 6.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0시", "6시", "12시", "18시", "24시").forEach { T(it, ViaType.labelS, c.text3.c()) }
        }
    }
}

/** 최근 7일 막대 차트(오늘 강조). 값이 모두 0이면 바닥선만. */
@Composable
fun WeekBars(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val c = colors
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(height + 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val today = i == values.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val barH = (height.value * v / max).coerceAtLeast(6f).dp
                Box(Modifier.fillMaxWidth().height(barH).clip(RoundedCornerShape(10.dp)).background(if (today) c.active.c() else if (v > 0) c.active.c().copy(alpha = 0.45f) else c.track.c()))
                T(labels[i], ViaType.labelS.copy(fontWeight = if (today) FontWeight.Bold else FontWeight.Normal), if (today) c.text.c() else c.text3.c())
            }
        }
    }
}

/** 겹친 막대: 회색 전체 = 비회원가, 그 안에 내 요금, 그 차이 = 아낀 만큼(점선 테두리). */
@Composable
fun SavingBar(mine: Int, nonMember: Int, color: Color, modifier: Modifier = Modifier, height: Dp = 20.dp) {
    val c = colors
    val f = if (nonMember > 0) (mine.toFloat() / nonMember).coerceIn(0f, 1f) else 1f
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(this.size.height / 2)
        drawRoundRect(c.activeSoft.c(), size = this.size, cornerRadius = r)
        drawRoundRect(color, size = Size(this.size.width * f, this.size.height), cornerRadius = r)
    }
}

/** 티켓 모양 카드(양옆이 파인 쿠폰). */
@Composable
fun TicketCard(modifier: Modifier = Modifier, accent: Color, content: @Composable () -> Unit) {
    val c = colors
    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            val notch = 18.dp.toPx(); val r = 24.dp.toPx(); val w = this.size.width; val h = this.size.height
            val path = Path().apply {
                moveTo(r, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r)
                lineTo(w, h / 2 - notch); arcTo(androidx.compose.ui.geometry.Rect(w - notch, h / 2 - notch, w + notch, h / 2 + notch), 270f, -180f, false)
                lineTo(w, h - r); quadraticTo(w, h, w - r, h); lineTo(r, h); quadraticTo(0f, h, 0f, h - r)
                lineTo(0f, h / 2 + notch); arcTo(androidx.compose.ui.geometry.Rect(-notch, h / 2 - notch, notch, h / 2 + notch), 90f, -180f, false)
                lineTo(0f, r); quadraticTo(0f, 0f, r, 0f); close()
            }
            drawPath(path, c.card.c())
            drawRect(accent, Offset(0f, 0f), Size(12.dp.toPx(), h))
            val x = w * 0.72f
            drawLine(c.line.c(), Offset(x, 16.dp.toPx()), Offset(x, h - 16.dp.toPx()), 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)))
        }
        Box(Modifier.padding(horizontal = 48.dp, vertical = 32.dp)) { content() }
    }
}
