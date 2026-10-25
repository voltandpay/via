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
private fun LineIcon(size: Dp, color: Color, draw: (s: Float) -> List<Path>) {
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
