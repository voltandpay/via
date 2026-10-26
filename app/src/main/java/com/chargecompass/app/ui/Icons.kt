package com.chargecompass.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 24 격자 기준 선 아이콘(Pleos: 단순한 Outlined 아이콘). 장식용이라 접근성 이름은 붙이지 않는다. */
@Composable
internal fun LineIcon(size: Dp, color: Color, draw: (s: Float) -> List<Path>) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        draw(s).forEach { drawPath(it, color, style = stroke) }
    }
}

@Composable
fun BoltIcon(size: Dp = 26.dp, color: Color = Color.White) = LineIcon(size, color) { s ->
    listOf(Path().apply {
        moveTo(13 * s, 2 * s); lineTo(4 * s, 14 * s); lineTo(11 * s, 14 * s); lineTo(10 * s, 22 * s)
        lineTo(19 * s, 10 * s); lineTo(12 * s, 10 * s); close()
    })
}

@Composable
fun MicIcon(size: Dp = 32.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(color, Offset(9 * s, 2 * s), Size(6 * s, 12 * s), CornerRadius(3 * s), style = stroke)
        drawArc(color, 0f, 180f, false, Offset(5 * s, 3 * s), Size(14 * s, 14 * s), style = stroke)
        drawLine(color, Offset(12 * s, 17 * s), Offset(12 * s, 22 * s), stroke.width, StrokeCap.Round)
    }
}

@Composable
fun PlusIcon(size: Dp = 30.dp, color: Color = Color.White) = LineIcon(size, color) { s ->
    listOf(
        Path().apply { moveTo(12 * s, 5 * s); lineTo(12 * s, 19 * s) },
        Path().apply { moveTo(5 * s, 12 * s); lineTo(19 * s, 12 * s) },
    )
}

/** 설정(톱니): 바깥 이빨 8개 + 가운데 원. */
@Composable
fun GearIcon(size: Dp = 36.dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(width = 2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val c = Offset(12 * s, 12 * s)
        drawCircle(color, radius = 7 * s, center = c, style = stroke)
        drawCircle(color, radius = 3 * s, center = c, style = stroke)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val from = Offset((12 + 7 * Math.cos(a)).toFloat() * s, (12 + 7 * Math.sin(a)).toFloat() * s)
            val to = Offset((12 + 10 * Math.cos(a)).toFloat() * s, (12 + 10 * Math.sin(a)).toFloat() * s)
            drawLine(color, from, to, stroke.width, StrokeCap.Round)
        }
    }
}

@Composable
fun CloseIcon(size: Dp = 28.dp, color: Color) = LineIcon(size, color) { s ->
    listOf(
        Path().apply { moveTo(6 * s, 6 * s); lineTo(18 * s, 18 * s) },
        Path().apply { moveTo(18 * s, 6 * s); lineTo(6 * s, 18 * s) },
    )
}

/** 스탬프(동그라미 안 체크). */
@Composable
fun StampIcon(size: Dp = 48.dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(color, radius = 10 * s, center = Offset(12 * s, 12 * s), style = stroke)
        drawPath(Path().apply { moveTo(7.5f * s, 12.5f * s); lineTo(10.5f * s, 15.5f * s); lineTo(16.5f * s, 9 * s) }, color, style = stroke)
    }
}

/** 쿠폰(양옆이 파인 티켓). */
@Composable
fun TicketIcon(size: Dp = 48.dp, color: Color) = LineIcon(size, color) { s ->
    listOf(
        Path().apply {
            moveTo(3 * s, 7 * s); lineTo(21 * s, 7 * s); lineTo(21 * s, 10 * s)
            quadraticTo(18.5f * s, 12 * s, 21 * s, 14 * s); lineTo(21 * s, 17 * s); lineTo(3 * s, 17 * s); lineTo(3 * s, 14 * s)
            quadraticTo(5.5f * s, 12 * s, 3 * s, 10 * s); close()
        },
        Path().apply { moveTo(14 * s, 8 * s); lineTo(14 * s, 16 * s) },
    )
}

/** 경로(지도 핀). */
@Composable
fun PinIcon(size: Dp = 48.dp, color: Color) = LineIcon(size, color) { s ->
    listOf(
        Path().apply {
            moveTo(12 * s, 21 * s)
            cubicTo(7 * s, 15.5f * s, 5 * s, 12.5f * s, 5 * s, 9.5f * s)
            cubicTo(5 * s, 5.5f * s, 8 * s, 2.5f * s, 12 * s, 2.5f * s)
            cubicTo(16 * s, 2.5f * s, 19 * s, 5.5f * s, 19 * s, 9.5f * s)
            cubicTo(19 * s, 12.5f * s, 17 * s, 15.5f * s, 12 * s, 21 * s); close()
        },
        Path().apply { addOval(androidx.compose.ui.geometry.Rect(9.5f * s, 7 * s, 14.5f * s, 12 * s)) },
    )
}

@Composable
fun HomeIcon(size: Dp = 32.dp, color: Color) = LineIcon(size, color) { s ->
    listOf(Path().apply { moveTo(4 * s, 11 * s); lineTo(12 * s, 4 * s); lineTo(20 * s, 11 * s); lineTo(20 * s, 20 * s); lineTo(4 * s, 20 * s); close() },
        Path().apply { moveTo(10 * s, 20 * s); lineTo(10 * s, 14 * s); lineTo(14 * s, 14 * s); lineTo(14 * s, 20 * s) })
}

@Composable
fun ListIcon(size: Dp = 32.dp, color: Color) = LineIcon(size, color) { s ->
    listOf(6f, 12f, 18f).map { y -> Path().apply { moveTo(9 * s, y * s); lineTo(20 * s, y * s) } } +
        listOf(6f, 12f, 18f).map { y -> Path().apply { moveTo(4 * s, y * s); lineTo(5 * s, y * s) } }
}

@Composable
fun CalendarIcon(size: Dp = 32.dp, color: Color) = LineIcon(size, color) { s ->
    listOf(Path().apply { moveTo(4 * s, 6 * s); lineTo(20 * s, 6 * s); lineTo(20 * s, 20 * s); lineTo(4 * s, 20 * s); close() },
        Path().apply { moveTo(4 * s, 10 * s); lineTo(20 * s, 10 * s) },
        Path().apply { moveTo(8 * s, 3 * s); lineTo(8 * s, 7 * s) }, Path().apply { moveTo(16 * s, 3 * s); lineTo(16 * s, 7 * s) })
}
