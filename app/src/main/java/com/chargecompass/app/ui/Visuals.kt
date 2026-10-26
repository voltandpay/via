package com.chargecompass.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import com.chargecompass.app.R
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
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
        if (showText) T("VIA", TextStyle(fontFamily = AstaSans, fontSize = (size.value * 0.68f).sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp))
    }
}

/**
 * 원형 게이지: [fraction] 만큼 채우고 [marker] 위치(0~1)에 눈금. 270도 호(아래가 비는 계기판 모양).
 * [targetColor]를 주면 눈금 대신 [marker]까지를 옅은 호로 칠한다("여기까지 찬다" — 라벨 없는 눈금은 깨진 선처럼 보였다).
 * [animate]이면 0에서 차오른다(정차·충전 중에만 — 주행 중 애니메이션 금지).
 */
@Composable
fun RingGauge(
    fraction: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 280.dp, stroke: Dp = 28.dp,
    marker: Float? = null, targetColor: Color? = null, animate: Boolean = true, content: @Composable BoxScope.() -> Unit = {},
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
            if (marker != null && targetColor != null)
                drawArc(targetColor, 135f, 270f * marker.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (anim.value > 0f) drawArc(color, 135f, 270f * anim.value, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (marker != null && targetColor == null) {
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

/**
 * 사업자 배지 색: 각 사업자 브랜드의 대표 색에 가깝게 고르되, 흰 글자가 읽히도록 명도를 맞췄다(근사값).
 * 표에 없는 사업자는 이름으로 10색 중 하나를 고른다.
 */
private val operatorColors = mapOf(
    "me" to 0xFF12A5C9,       // 환경부: 하늘색(한전·워터 파랑과 구분)
    "kepco" to 0xFF13408C,    // 한전: 남색
    "chaevi" to 0xFF00A06A,   // 채비: 초록
    "skel" to 0xFFE2231A,     // SK일렉링크: 빨강
    "gscv" to 0xFF00857C,     // GS차지비: 청록
    "everon" to 0xFF6DB33F,   // 에버온: 연두(채비 초록과 구분)
    "water" to 0xFF2D7FF9,    // 워터: 밝은 파랑
    "epit" to 0xFF1E2A5A,     // 이피트: 짙은 남색
    "evsis" to 0xFF6A3FD8,    // 이브이시스: 보라
    "pluglink" to 0xFFF07B1E, // 플러그링크: 주황
)
private val badgePalette = listOf(
    0xFF2F6BFF, 0xFF00A86B, 0xFF6A4CFF, 0xFFF28C28, 0xFF00A3A3, 0xFFE5487D, 0xFF3D5A99, 0xFFA0642D, 0xFFD9480F, 0xFF5C6370,
)
fun badgeColor(operatorId: String): Color =
    Color(operatorColors[operatorId] ?: badgePalette[Math.floorMod(operatorId.hashCode(), badgePalette.size)])

/** 도감 배지: 모으면 사업자 색 원에 흰 문양, 못 모았으면 점선 원에 자물쇠. 방금 모았으면 톡 커지며 나타난다. [label]은 읽어 주기용 이름. */
@Composable
fun StationBadge(operatorId: String, label: String, collected: Boolean, fresh: Boolean = false, size: Dp = 112.dp, medal: Int? = null) {
    val c = colors
    val pop = remember { Animatable(if (fresh) 0.4f else 1f) }
    LaunchedEffect(fresh) { if (fresh) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    if (medal != null) {
        // 3D 메달: 메달 테두리까지 꽉 찬 이미지라 평면 배지와 거의 같은 크기
        // 못 모은 메달은 반투명으로 물러나 보이게(모은 메달이 돋보이도록)
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(medal), label, Modifier.size(size * 1.05f).scale(pop.value),
            alpha = if (collected) 1f else 0.45f)
        return
    }
    Box(Modifier.size(size).scale(pop.value), contentAlignment = Alignment.Center) {
        val color = badgeColor(operatorId)
        val symbol = operatorSymbol(operatorId)
        Canvas(Modifier.size(size)) {
            val r = this.size.width / 2
            if (collected) {
                drawCircle(color, r)
                drawCircle(Color.White.copy(alpha = 0.35f), r - 6.dp.toPx(), style = Stroke(3.dp.toPx()))
                drawPath(symbolPath(symbol, center, r * 0.44f), Color.White)
            } else {
                drawCircle(c.line.c(), r - 2.dp.toPx(), style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
            }
        }
        if (!collected) LockIcon(color = c.text3.c())
    }
}

/**
 * 사업자 문양: 3D 메달(tools/blender_style.py SHAPES)과 같은 윤곽. 글자 대신 모양으로 사업자를 가른다(같은 계열 색이 많다).
 * 좌표는 대략 -1~1, 위가 +y. 사업자 로고를 흉내 내지 않는 단순한 도형.
 */
private fun star(n: Int, r1: Float, r2: Float) = List(2 * n) { i ->
    val a = Math.toRadians(90.0 + 180.0 * i / n); val r = if (i % 2 == 0) r1 else r2
    (r * kotlin.math.cos(a)).toFloat() to (r * kotlin.math.sin(a)).toFloat()
}
private fun ngon(n: Int, r: Float, a0: Double, dy: Float = 0f) = List(n) { i ->
    val a = Math.toRadians(a0 + 360.0 * i / n)
    (r * kotlin.math.cos(a)).toFloat() to (dy + r * kotlin.math.sin(a)).toFloat()
}
private fun rotate45(pts: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    val k = kotlin.math.sqrt(0.5f)
    return pts.map { (x, y) -> (x - y) * k to (x + y) * k }
}
private val symbolShapes: Map<String, List<List<Pair<Float, Float>>>> = mapOf(
    "bolt" to listOf(listOf(0.2f to 1f, -0.7f to -0.2f, -0.1f to -0.2f, -0.2f to -1f, 0.7f to 0.2f, 0.1f to 0.2f)),
    "plug" to listOf(listOf(-0.1f to -0.95f, 0.1f to -0.95f, 0.1f to -0.5f, 0.35f to -0.42f, 0.55f to -0.15f, 0.55f to 0.5f, 0.4f to 0.5f,
        0.4f to 0.95f, 0.22f to 0.95f, 0.22f to 0.5f, -0.22f to 0.5f, -0.22f to 0.95f, -0.4f to 0.95f, -0.4f to 0.5f, -0.55f to 0.5f,
        -0.55f to -0.15f, -0.35f to -0.42f, -0.1f to -0.5f)),
    "hexagon" to listOf(ngon(6, 0.92f, 0.0)),
    "sparkle" to listOf(star(4, 1f, 0.3f)),
    "star" to listOf(star(5, 1f, 0.45f)),
    "triangle" to listOf(ngon(3, 0.95f, 90.0, dy = -0.1f)),
    "plus" to listOf(listOf(-0.2f to -0.85f, 0.2f to -0.85f, 0.2f to -0.2f, 0.85f to -0.2f, 0.85f to 0.2f, 0.2f to 0.2f, 0.2f to 0.85f,
        -0.2f to 0.85f, -0.2f to 0.2f, -0.85f to 0.2f, -0.85f to -0.2f, -0.2f to -0.2f)),
    "drop" to listOf(run {
        val a = Math.toDegrees(kotlin.math.acos(0.62 / 1.3))   // 꼭짓점(0,1)에서 원(중심 0,-0.3)에 그은 접선
        listOf(0f to 1f) + (0..30).map { i ->
            val t = Math.toRadians(90 + a + (360 - 2 * a) * i / 30)
            (0.62 * kotlin.math.cos(t)).toFloat() to (-0.3 + 0.62 * kotlin.math.sin(t)).toFloat()
        }
    }),
    "leaf" to run {   // 끝이 뾰족한 잎 + 꼭지(꼭지가 없으면 작게 볼 때 타원처럼 읽힌다)
        val top = (0..24).map { i -> -0.8f + 1.7f * i / 24 to (0.46 * Math.pow(kotlin.math.sin(Math.PI * i / 24), 0.8)).toFloat() }
        val leaf = top.map { (x, y) -> x to -y } + top.drop(1).dropLast(1).reversed()
        listOf(rotate45(leaf), rotate45(listOf(-1.05f to -0.05f, -0.6f to -0.05f, -0.6f to 0.05f, -1.05f to 0.05f)))
    },
    "chevron" to listOf(listOf(-0.55f to 0.9f, -0.1f to 0.9f, 0.6f to 0f, -0.1f to -0.9f, -0.55f to -0.9f, 0.15f to 0f)),
    "diamond" to listOf(listOf(0f to 1f, -0.7f to 0f, 0f to -1f, 0.7f to 0f)),
)
private val operatorSymbols = mapOf(
    "me" to "leaf", "kepco" to "plug", "skel" to "hexagon", "chaevi" to "bolt", "evsis" to "sparkle",
    "gscv" to "triangle", "everon" to "plus", "water" to "drop", "pluglink" to "chevron",
)
/** 문양이 정해지지 않은 사업자(이피트 등)는 마름모. */
fun operatorSymbol(operatorId: String): String = operatorSymbols[operatorId] ?: "diamond"

fun symbolPath(name: String, center: Offset, scale: Float): Path = Path().apply {
    symbolShapes.getValue(name).forEach { pts ->
        pts.forEachIndexed { i, (x, y) ->
            val px = center.x + x * scale; val py = center.y - y * scale
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

/** 도감 3D 메달(Blender 렌더, tools/blender_medal.py): 사업자별 동·은·금. 메달이 없는 사업자는 null(평면 배지). */
private val operatorMedals = mapOf(
    "me" to listOf(R.drawable.medal_me_bronze, R.drawable.medal_me_silver, R.drawable.medal_me_gold),
    "kepco" to listOf(R.drawable.medal_kepco_bronze, R.drawable.medal_kepco_silver, R.drawable.medal_kepco_gold),
    "skel" to listOf(R.drawable.medal_skel_bronze, R.drawable.medal_skel_silver, R.drawable.medal_skel_gold),
    "chaevi" to listOf(R.drawable.medal_chaevi_bronze, R.drawable.medal_chaevi_silver, R.drawable.medal_chaevi_gold),
    "evsis" to listOf(R.drawable.medal_evsis_bronze, R.drawable.medal_evsis_silver, R.drawable.medal_evsis_gold),
    "gscv" to listOf(R.drawable.medal_gscv_bronze, R.drawable.medal_gscv_silver, R.drawable.medal_gscv_gold),
    "everon" to listOf(R.drawable.medal_everon_bronze, R.drawable.medal_everon_silver, R.drawable.medal_everon_gold),
    "water" to listOf(R.drawable.medal_water_bronze, R.drawable.medal_water_silver, R.drawable.medal_water_gold),
    "pluglink" to listOf(R.drawable.medal_pluglink_bronze, R.drawable.medal_pluglink_silver, R.drawable.medal_pluglink_gold),
)
fun medalRes(operatorId: String, tier: com.chargecompass.core.dex.MedalTier): Int? = operatorMedals[operatorId]?.get(tier.ordinal)

/** 사업자 데이 한정 메달(보라 테두리). 시연 데이터의 사업자 데이가 있는 곳만. */
fun eventMedalRes(operatorId: String): Int? = when (operatorId) {
    "chaevi" -> R.drawable.medal_chaevi_event
    "skel" -> R.drawable.medal_skel_event
    else -> null
}

/** 지역 엠블럼(방패): 다 모으면 금, 아니면 회색. */
fun regionEmblemRes(regionId: String, done: Boolean): Int? = when (regionId) {
    "downtown" -> if (done) R.drawable.emblem_downtown_gold else R.drawable.emblem_downtown_locked
    "gangnam" -> if (done) R.drawable.emblem_gangnam_gold else R.drawable.emblem_gangnam_locked
    "gyeonggi" -> if (done) R.drawable.emblem_gyeonggi_gold else R.drawable.emblem_gyeonggi_locked
    else -> null
}

/** 충전 멤버십 카드(사업자 색 3D 카드). */
fun membershipCardRes(operatorId: String): Int? = when (operatorId) {
    "me" -> R.drawable.card_me; "kepco" -> R.drawable.card_kepco; "skel" -> R.drawable.card_skel
    "chaevi" -> R.drawable.card_chaevi; "evsis" -> R.drawable.card_evsis; "gscv" -> R.drawable.card_gscv
    "everon" -> R.drawable.card_everon; "water" -> R.drawable.card_water; "pluglink" -> R.drawable.card_pluglink
    "epit" -> R.drawable.card_epit
    else -> null
}

/** 제휴 쿠폰 티켓(3D). */
fun couponTicketRes(operatorId: String): Int? = when (operatorId) {
    "kepco" -> R.drawable.ticket_kepco; "chaevi" -> R.drawable.ticket_chaevi; "water" -> R.drawable.ticket_water
    else -> null
}

/** 새 스탬프 획득 연출: 메달이 세로축으로 도는 8장면(0 = 정면). 시연 충전소(채비)만 있다. */
fun stampSpinFrames(operatorId: String): List<Int>? = if (operatorId == "chaevi") listOf(
    R.drawable.stamp_spin_chaevi_0, R.drawable.stamp_spin_chaevi_1, R.drawable.stamp_spin_chaevi_2, R.drawable.stamp_spin_chaevi_3,
    R.drawable.stamp_spin_chaevi_4, R.drawable.stamp_spin_chaevi_5, R.drawable.stamp_spin_chaevi_6, R.drawable.stamp_spin_chaevi_7,
) else null

/** Blender로 렌더링한 소품 이미지(tools/blender_props.py). */
@Composable
fun Prop(res: Int, size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) =
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(res), null, modifier.size(size), alpha = alpha)

/** 포인트 코인 + 숫자. */
@Composable
fun CoinAmount(text: String, style: TextStyle, color: Color, coin: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(coin * 0.18f)) {
        Prop(R.drawable.prop_coin, coin)
        T(text, style, color)
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

/** 최근 7일 막대 차트(오늘 강조). 막대 위에 금액, 막대 폭은 [maxBarWidth]까지. 기록 없는 날은 점선 바닥. */
@Composable
fun WeekBars(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 160.dp, maxBarWidth: Dp = 72.dp) {
    val c = colors
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(height + 76.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val today = i == values.lastIndex
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (v > 0) {
                    T(compactWon(v), ViaType.labelS.copy(fontWeight = if (today) FontWeight.Bold else FontWeight.Normal), if (today) c.activeText.c() else c.text2.c())
                    val barH = (height.value * barFraction(v, max)).coerceAtLeast(10f).dp
                    Box(Modifier.widthIn(max = maxBarWidth).fillMaxWidth().height(barH).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(if (today) c.active.c() else c.active.c().copy(alpha = if (c.isDark) 0.7f else 0.45f)))
                } else {
                    Canvas(Modifier.widthIn(max = maxBarWidth).fillMaxWidth().height(6.dp)) {
                        drawLine(c.line.c(), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(), StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    }
                }
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

/**
 * 앞으로 24시간 띠: 왼쪽 끝이 지금. 자정을 넘는 이벤트도 한 덩어리로 보인다.
 * 아래 눈금은 지금부터 6시간 간격의 실제 시각.
 */
@Composable
fun UpcomingTimeline(startHour: Int, endHour: Int, nowMinutes: Int, color: Color, modifier: Modifier = Modifier) {
    val c = colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val h = this.size.height
            drawRoundRect(c.track.c(), size = this.size, cornerRadius = CornerRadius(h / 2))
            upcomingWindowSegments(startHour, endHour, nowMinutes).forEach { (a, b) ->
                drawRoundRect(color, Offset(this.size.width * a, 0f), Size(this.size.width * (b - a), h), CornerRadius(h / 2))
            }
            drawCircle(c.text.c(), h * 0.32f, Offset(h / 2, h / 2))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..4).forEach { i ->
                val label = if (i == 0) "지금" else "${(nowMinutes / 60 + i * 6) % 24}시"
                T(label, ViaType.labelS.copy(fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal), if (i == 0) c.text.c() else c.text3.c())
            }
        }
    }
}

/**
 * 경로 띠: 출발(지금 배터리) → 후보 충전소(순위 번호) → 목적지(도착 예상).
 * 겹침을 피하려고 이름표는 1위에만 달고, 나머지는 번호 원만 둔다.
 */
@Composable
fun RouteBand(
    stops: List<RouteStop>, startSoc: Int?, destination: String, arrivalSoc: Int?, totalKm: Int?, bestKm: Int?,
    modifier: Modifier = Modifier,
) {
    val c = colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                T("출발", ViaType.labelS, c.text3.c())
                T(startSoc?.let { "$it%" } ?: "-", ViaType.bodyM.copy(fontWeight = FontWeight.Bold))
            }
            Column(horizontalAlignment = Alignment.End) {
                T("$destination${totalKm?.let { " · ${it}km" } ?: ""}", ViaType.labelS, c.text3.c())
                T(arrivalSoc?.let { "도착 $it%" } ?: "-", ViaType.bodyM.copy(fontWeight = FontWeight.Bold), if ((arrivalSoc ?: 100) < 10) c.neg.c() else c.text.c())
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
            val w = maxWidth
            val marker = 52.dp
            val lineY = 32.dp
            Canvas(Modifier.matchParentSize()) {
                val y = lineY.toPx(); val r = 10.dp.toPx()
                drawLine(c.track.c(), Offset(r, y), Offset(size.width - r, y), 10.dp.toPx(), StrokeCap.Round)
                // 1위 충전소까지는 진하게: 지금 가는 구간
                stops.firstOrNull()?.let { drawLine(c.pos.c(), Offset(r, y), Offset(r + (size.width - 2 * r) * it.fraction, y), 10.dp.toPx(), StrokeCap.Round) }
                drawCircle(c.text.c(), r, Offset(r, y))
                drawRect(c.text.c(), Offset(size.width - 2 * r, y - r), Size(2 * r, 2 * r))
            }
            // 뒤 순위부터 그려 1위가 위에 오게
            stops.withIndex().reversed().forEach { (i, st) ->
                val x = (w - marker) * st.fraction
                Box(
                    Modifier.offset(x = x, y = lineY - marker / 2).size(marker).clip(CircleShape)
                        .background(if (i == 0) c.pos.c() else c.card.c()).border(3.dp, if (i == 0) c.pos.c() else c.line.c(), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == 0) BoltIcon(26.dp) else T("${i + 1}", ViaType.labelM.copy(fontWeight = FontWeight.Bold), c.text2.c())
                }
            }
        }
        stops.firstOrNull()?.let { best ->
            // 1위 이름표: 마커 아래, 띠 밖으로 나가지 않게 좌우를 맞춘다
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val labelW = 280.dp.coerceAtMost(maxWidth)
                val x = ((maxWidth - 52.dp) * best.fraction + 26.dp - labelW / 2).coerceIn(0.dp, maxWidth - labelW)
                Column(Modifier.offset(x = x).widthIn(max = labelW), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("1위 · ${bestKm ?: "-"}km 앞", ViaType.labelM.copy(fontWeight = FontWeight.Bold), c.pos.c())
                    T("도착 시 ${best.socPct?.let { "$it%" } ?: "-"}", ViaType.labelS, c.text2.c())
                }
            }
        }
    }
}

/** 영수증 카드: 아래 가장자리가 톱니 모양. */
@Composable
fun ReceiptCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = colors
    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            val tooth = 14.dp.toPx(); val r = 32.dp.toPx(); val w = size.width; val h = size.height - tooth
            val path = Path().apply {
                moveTo(r, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r); lineTo(w, h)
                val n = (w / (tooth * 2)).toInt().coerceAtLeast(1); val step = w / n
                for (k in n downTo 1) { lineTo(step * k - step / 2, h + tooth); lineTo(step * (k - 1), h) }
                lineTo(0f, r); quadraticTo(0f, 0f, r, 0f); close()
            }
            if (!c.isDark) drawPath(path, Color(0x14000000), style = Stroke(2.dp.toPx()))
            drawPath(path, c.card.c())
            clipPath(path) { drawRect(c.active.c(), Offset(0f, 0f), Size(w, 10.dp.toPx())) }
        }
        Column(Modifier.padding(start = 44.dp, end = 44.dp, top = 40.dp, bottom = 52.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** 영수증 한 줄: 왼쪽 항목, 오른쪽 값. */
@Composable
fun ReceiptRow(label: String, value: String, valueColor: Color = colors.text.c(), bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, ViaType.bodyM, colors.text2.c(), Modifier.weight(1f))
        T(value, ViaType.bodyM.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), valueColor)
    }
}

/** 점선 구분선. */
@Composable
fun DashedDivider() {
    val c = colors
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        drawLine(c.line.c(), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
    }
}

/** 짧은 축하 효과: 작은 조각들이 위로 튀었다가 떨어지며 사라진다(정차 중 화면에서 한 번만). */
@Composable
fun ConfettiBurst(key: Any, modifier: Modifier = Modifier) {
    val c = colors
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(1400, easing = androidx.compose.animation.core.LinearEasing)) }
    if (t.value >= 1f) return
    val pieces = remember(key) {
        val rnd = java.util.Random(key.hashCode().toLong())
        List(36) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 2 - 1, 0.6f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 360f, rnd.nextInt(4).toFloat()) }
    }
    val palette = listOf(c.active.c(), c.pos.c(), c.event.c(), Color(0xFFFFB020))
    Canvas(modifier) {
        val p = t.value
        pieces.forEach { (x0, vx, vy, rot, ci) ->
            val x = size.width * (0.2f + 0.6f * x0) + vx * size.width * 0.25f * p
            val y = size.height * 0.55f - vy * size.height * 0.9f * p + size.height * 1.1f * p * p
            rotate(rot + 540f * p, Offset(x, y)) {
                drawRect(palette[ci.toInt()].copy(alpha = 1f - p * p), Offset(x - 6.dp.toPx(), y - 3.dp.toPx()), Size(12.dp.toPx(), 6.dp.toPx()))
            }
        }
    }
}


/** 말하기 버튼: 누르면 "무엇을 도와드릴까요?" 하고 듣는다(주행 중에도 하나뿐인 큰 버튼). */
@Composable
fun VoiceAskButton(c: com.chargecompass.app.AppContainer, modifier: Modifier = Modifier) {
    val col = colors
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(col.pos.c()).pressable { c.askAssistant(scope) }
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MicIcon(30.dp)
        T("말하기", ViaType.bodyM.copy(fontWeight = FontWeight.Bold), Color.White)
    }
}
