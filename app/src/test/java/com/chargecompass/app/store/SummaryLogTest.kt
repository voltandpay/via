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

    @Test
    fun `충전소 id도 남고, id 칸이 없는 예전 줄도 읽힌다`() = runBlocking {
        val file = File(Files.createTempDirectory("cc").toFile(), "summaries.txt")
        file.writeText("반포 한전 충전소|10.0|3240|1460|10|2026-11-02T09:00\n")
        val s = ChargeSummary("양재 채비 충전소", 19.35, 5786, 4276, 30, LocalDateTime.of(2026, 11, 3, 14, 21), "st-yangjae-chaevi")
        FileSummaryLog(file).append(s)
        val read = FileSummaryLog(file).summaries.value
        assertEquals(listOf(null, "st-yangjae-chaevi"), read.map { it.stationId })
    }
}
