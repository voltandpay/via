package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.shape.CircleShape

fun Long.c() = Color(this)

val LocalViaColors = staticCompositionLocalOf { ViaPalette.light }
val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.WIDE }

/** Pleos 타이포 스케일(size / line height). doc/UI-디자인-가이드-정리.md 3절. */
object ViaType {
    private fun s(size: Int, line: Int, w: FontWeight = FontWeight.Normal) = TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = w)
    /** 화면당 하나뿐인 핵심 숫자(도착 배터리, 절약액). 가이드 Headline보다 크게 둔 예외. */
    val display = s(120, 128, FontWeight.ExtraBold)
    val headline = s(60, 72, FontWeight.ExtraBold)
    val titleL = s(40, 52, FontWeight.ExtraBold)
    val titleM = s(36, 44, FontWeight.ExtraBold)
    val titleS = s(32, 40, FontWeight.ExtraBold)
    val bodyL = s(30, 38)
    val bodyM = s(28, 36)
    val labelM = s(24, 28)
    val labelS = s(20, 24)
}

/** 시스템 주야간 설정을 따른다(Pleos: 조명에 따라 밝기·대비 자동 조정). 앱 창 폭으로 넓은/좁은 배치를 정한다. */
@Composable
fun ViaTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) ViaPalette.dark else ViaPalette.light
    val scheme = if (colors.isDark) {
        darkColorScheme(primary = colors.pos.c(), background = colors.bg.c(), surface = colors.card.c())
    } else {
        lightColorScheme(primary = colors.pos.c(), background = colors.bg.c(), surface = colors.card.c())
    }
    MaterialTheme(colorScheme = scheme) {
        BoxWithConstraints(Modifier.background(colors.bg.c())) {
            CompositionLocalProvider(
                LocalViaColors provides colors,
                LocalWindowLayout provides WindowLayout.of(maxWidth.value.toInt()),
                content = content,
            )
        }
    }
}

val colors: ViaColors @Composable get() = LocalViaColors.current
val isWide: Boolean @Composable get() = LocalWindowLayout.current == WindowLayout.WIDE

@Composable
fun T(
    text: String, style: TextStyle, color: Color = colors.text.c(), modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE, align: androidx.compose.ui.text.style.TextAlign? = null,
) = Text(
    text, modifier = modifier, style = if (align != null) style.copy(textAlign = align) else style, color = color,
    maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
)

/** 카드: 라이트에서는 그림자 Level 1, 다크에서는 면 색으로만 구분. */
@Composable
fun ViaCard(
    modifier: Modifier = Modifier,
    padding: Dp = 40.dp,
    highlight: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = colors
    val shape = RoundedCornerShape(32.dp)
    var m = modifier.fillMaxWidth()
    if (!c.isDark) m = m.shadow(8.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
    m = m.clip(shape).background(c.card.c())
    if (highlight != null) m = m.border(3.dp, highlight, shape)
    else if (c.isDark) m = m.border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    Column(m.padding(padding), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

/** 모든 터치 요소: 누르는 동안 95%로 줄어들고, 누르면 짧게 진동한다(Pleos Interaction·Feedback). */
@Composable
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val view = LocalView.current
    return this
        .graphicsLayer { val s = if (pressed) 0.95f else 1f; scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role) {
            // Pleos: 터치에 진동 피드백
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
}

/**
 * Pleos Basic button. [loading]이면 버튼 안에 로더를 보이고 누를 수 없다(응답 0.25초를 넘는 작업).
 * [primary]는 채운 파란 버튼, 아니면 테두리 버튼.
 */
@Composable
fun ViaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    loading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 96.dp,
) {
    val c = colors
    val shape = RoundedCornerShape(24.dp)
    val active = enabled && !loading
    val bg = when {
        !enabled -> c.chip.c()
        primary -> c.pos.c()
        else -> Color.Transparent
    }
    val fg = if (primary && enabled) Color.White else c.text.c()
    Box(
        modifier
            .heightIn(min = height)
            .widthIn(min = 240.dp)
            .clip(shape)
            .background(bg)
            .then(if (primary) Modifier else Modifier.border(2.dp, c.line.c(), shape))
            .pressable(enabled = active, onClick = onClick)
            .padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(32.dp), color = fg, strokeWidth = 4.dp)
            T(if (loading) "추가하는 중…" else text, ViaType.bodyL.copy(fontWeight = FontWeight.Bold), fg)
        }
    }
}

/** 작은 정보 칩. */
@Composable
fun Chip(text: String, bg: Color = colors.chip.c(), fg: Color = colors.text.c(), bold: Boolean = false) {
    T(
        text, ViaType.bodyM.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), fg,
        Modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** 가로 막대(배터리·요금 비교). [marker]는 0~1 위치의 세로 기준선. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 24.dp, marker: Float? = null) {
    val c = colors
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(c.track.c())
            .drawBehind {
                drawRoundRect(color, size = size.copy(width = size.width * fraction.coerceIn(0f, 1f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                if (marker != null) drawLine(c.text.c(), Offset(size.width * marker, 0f), Offset(size.width * marker, size.height), strokeWidth = 4.dp.toPx())
            },
    )
}

/** 앱 표식: VIA 로고 아이콘. */
@Composable
fun ViaMark(size: Dp = 56.dp) = ViaLogo(size)

/**
 * Pleos Page Navigation + Line 탭. 화면 위쪽에 둔다(아래쪽은 시스템 GNB 자리).
 */
@Composable
fun PageNavigation(
    subtitle: String, tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, onSettings: (() -> Unit)? = null,
    icons: List<@Composable (Color) -> Unit> = emptyList(),
) {
    val c = colors
    Column(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ViaWordmark(56.dp)
            T(subtitle, ViaType.labelM, c.text3.c(), Modifier.weight(1f))
            // Page Navigation의 Suffix 자리: 설정
            if (onSettings != null) {
                Box(Modifier.size(64.dp).clip(CircleShape).pressable(onClick = onSettings), contentAlignment = Alignment.Center) {
                    GearIcon(color = c.text2.c())
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .drawBehind { drawLine(c.line.c(), Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) },
            horizontalArrangement = Arrangement.spacedBy(if (isWide) 48.dp else 28.dp),
        ) {
            tabs.forEachIndexed { i, label ->
                val sel = i == selected
                val tint = if (sel) c.text.c() else c.text3.c()
                // Pleos: 아이콘은 텍스트 라벨과 함께
                Row(
                    Modifier.pressable(role = Role.Tab) { onSelect(i) }
                        .drawBehind { if (sel) drawLine(c.text.c(), Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 4.dp.toPx()) }
                        .padding(horizontal = 4.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    icons.getOrNull(i)?.invoke(tint)
                    T(label, ViaType.bodyM.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal), tint)
                }
            }
        }
    }
}
