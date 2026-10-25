package com.chargecompass.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeTokensTest {
    private val palettes = listOf("light" to ViaPalette.light, "dark" to ViaPalette.dark)

    @Test
    fun `본문 글자색은 배경과 카드 위에서 모두 대비 4_5 이상`() {
        for ((name, p) in palettes) {
            for (surface in listOf(p.bg, p.card)) {
                for ((label, fg) in listOf("text" to p.text, "text2" to p.text2, "text3" to p.text3, "activeText" to p.activeText)) {
                    val ratio = contrastRatio(fg, surface)
                    assertTrue(ratio >= 4.5, "$name $label 대비 $ratio")
                }
            }
        }
    }

    @Test
    fun `대비 계산은 흑백 21대1, 같은 색 1대1`() {
        assertEquals(21.0, contrastRatio(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, contrastRatio(0xFF777777, 0xFF777777), 0.001)
    }

    @Test
    fun `가이드 색 토큰 값`() {
        assertEquals(0xFFF7F8FA, ViaPalette.light.bg)
        assertEquals(0xFF1D1E21, ViaPalette.dark.bg)
        assertEquals(0xFF0064FF, ViaPalette.light.pos)
        assertEquals(0xFF0082FF, ViaPalette.dark.pos)
        assertEquals(0xFF02C265, ViaPalette.light.active)
        assertEquals(0xFF00E074, ViaPalette.dark.active)
        assertEquals(0xFFFE3D16, ViaPalette.light.neg)
        assertEquals(0xFFFF4C28, ViaPalette.dark.neg)
    }
}
