package com.chargecompass.app.store

import com.chargecompass.app.rewards.ChargeSummary
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SummaryLogTest {
    @Test
    fun `충전 요약 기록은 다시 열어도 남는다`() = runBlocking {
        val file = File(Files.createTempDirectory("cc").toFile(), "summaries.txt")
        val s = ChargeSummary("양재 채비 충전소", 19.35, 5786, 4276, 30, LocalDateTime.of(2026, 11, 3, 14, 21))
        FileSummaryLog(file).append(s)
        assertEquals(listOf(s), FileSummaryLog(file).summaries.value)
    }
}
