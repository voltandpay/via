package com.chargecompass.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Background = Color(0xFF0E1116)
val Surface = Color(0xFF1A1F27)
val Accent = Color(0xFF00C389)
val Warn = Color(0xFFFFB020)
val Muted = Color(0xFF8B95A3)

@Composable
fun ChargeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Surface, onPrimary = Color.Black),
        content = content,
    )
}

/** 차 안에서 읽기 쉬운 큰 글씨 카드. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Surface, RoundedCornerShape(20.dp)).padding(28.dp),
        content = content,
    )
}

@Composable
fun Title(text: String, color: Color = Color.White) = Text(text, color = color, fontSize = 46.sp, fontWeight = FontWeight.Bold)

@Composable
fun Body(text: String, color: Color = Color.White) = Text(text, color = color, fontSize = 36.sp)

@Composable
fun Caption(text: String) = Text(text, color = Muted, fontSize = 30.sp)
