package com.chargecompass.app.ui

import kotlin.math.pow

/**
 * Pleos Connect UI 디자인 가이드의 색 토큰(ARGB). doc/UI-디자인-가이드-정리.md 4절.
 * 보조 글자는 기준색에 투명도 84%/64%를 준 text_secondary/text_tertiary.
 */
data class ViaColors(
    val isDark: Boolean,
    val bg: Long, val card: Long, val chip: Long, val track: Long, val line: Long,
    val text: Long, val text2: Long, val text3: Long,
    val pos: Long, val posSoft: Long,
    val active: Long, val activeSoft: Long, val activeText: Long,
    val neg: Long, val event: Long, val muted: Long,
)

object ViaPalette {
    val light = ViaColors(
        isDark = false,
        bg = 0xFFF7F8FA, card = 0xFFFFFFFF, chip = 0xFFEDEEF2, track = 0xFFE0E1E6, line = 0xFFE0E1E6,
        text = 0xFF131417, text2 = 0xD6131417, text3 = 0xA3131417,
        pos = 0xFF0064FF, posSoft = 0x1A0064FF,
        active = 0xFF02C265, activeSoft = 0x2402C265, activeText = 0xFF017A40,
        neg = 0xFFFE3D16, event = 0xFF5A46FA, muted = 0xFFA4A8AE,
    )
    val dark = ViaColors(
        isDark = true,
        bg = 0xFF1D1E21, card = 0xFF313236, chip = 0xFF44464E, track = 0xFF44464E, line = 0xFF44464E,
        text = 0xFFFFFFFF, text2 = 0xD6FFFFFF, text3 = 0xA3FFFFFF,
        pos = 0xFF0082FF, posSoft = 0x2E0082FF,
        active = 0xFF00E074, activeSoft = 0x2900E074, activeText = 0xFF00E074,
        neg = 0xFFFF4C28, event = 0xFF6A57FF, muted = 0xFF686A72,
    )
}

/** WCAG 대비. 반투명 글자색은 배경 위에 합성한 색으로 잰다. */
fun contrastRatio(fg: Long, bg: Long): Double {
    val a = ((fg ushr 24) and 0xFF) / 255.0
    fun ch(c: Long, shift: Int) = ((c ushr shift) and 0xFF).toDouble()
    fun mix(shift: Int) = ch(fg, shift) * a + ch(bg, shift) * (1 - a)
    fun lin(v: Double): Double { val s = v / 255; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
    fun lum(r: Double, g: Double, b: Double) = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    val l1 = lum(mix(16), mix(8), mix(0))
    val l2 = lum(ch(bg, 16), ch(bg, 8), ch(bg, 0))
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}

/** 앱 창 폭(dp)에 따른 배치. 분할 화면 세로 창(840dp 이하)은 한 줄로 쌓는다. */
enum class WindowLayout {
    WIDE, NARROW;

    companion object {
        fun of(widthDp: Int): WindowLayout = if (widthDp >= 1000) WIDE else NARROW
    }
}
