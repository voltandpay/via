package com.chargecompass.app.store

import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardKind
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FileStoresTest {
    private val dir: File = Files.createTempDirectory("cc").toFile()
    private val at = LocalDateTime.of(2026, 11, 3, 23, 30)

    @Test
    fun `원장은 다시 열어도 남아 있고 스탬프와 이벤트 회차를 알려준다`() = runBlocking {
        val file = File(dir, "ledger.txt")
        FileRewardLedger(file).append(
            listOf(
                RewardEntry(RewardKind.NEW_STAMP, 20, at, "st-1"),
                RewardEntry(RewardKind.EVENT_BONUS, 10, LocalDateTime.of(2026, 11, 4, 1, 0), "st-e", "ev-1", LocalDate.of(2026, 11, 3)),
                RewardEntry(RewardKind.EFFICIENT_TRIP, 10, at),
            ),
        )
        val reopened = FileRewardLedger(file)
        assertEquals(3, reopened.entries.value.size)
        assertEquals(setOf("st-1"), reopened.stampedStationIds())
        // 01시 보너스는 RewardEngine이 정한 전날 회차로 남는다
        assertEquals(setOf(LocalDate.of(2026, 11, 3)), reopened.eventBonusDates("ev-1"))
    }

    @Test
    fun `멤버십과 차종은 다시 열어도 남아 있다`() = runBlocking {
        val file = File(dir, "profile.txt")
        FileMembershipStore(file).apply { set(setOf("m-chaevi", "m-me")); setVehicle("ioniq5") }
        val reopened = FileMembershipStore(file)
        assertEquals(setOf("m-chaevi", "m-me"), reopened.owned.value)
        assertEquals("ioniq5", reopened.vehicleModelId.value)
    }

    @Test
    fun `지역 완성 항목의 지역도 다시 열면 남고, 지역 칸이 없는 예전 줄도 읽힌다`() = runBlocking {
        val file = File(dir, "ledger2.txt")
        file.writeText("NEW_STAMP|20|2026-11-03T23:30|st-1|||\n")
        FileRewardLedger(file).append(listOf(RewardEntry(RewardKind.REGION_COMPLETE, 50, at, "st-2", regionId = "gangnam")))
        val reopened = FileRewardLedger(file).entries.value
        assertEquals(listOf(RewardKind.NEW_STAMP, RewardKind.REGION_COMPLETE), reopened.map { it.kind })
        assertEquals("gangnam", reopened.last().regionId)
    }

    @Test
    fun `발견한 충전소는 다시 열어도 남는다`() = runBlocking {
        val file = File(dir, "seen.txt")
        val store = FileSeenStore(file)
        store.add(listOf("st-a", "st-b"))
        store.add(listOf("st-b", "st-c"))
        assertEquals(setOf("st-a", "st-b", "st-c"), FileSeenStore(file).seen.value)
    }

    @Test
    fun `마지막 줄이 쓰다 끊겼어도 다음 항목은 온전히 남고 깨진 줄만 버린다`() = runBlocking {
        val file = File(dir, "ledger3.txt")
        file.writeText("NEW_STAMP|20|2026-11-03T23:30|st-1|||\nCHARGE|10|2026-11-0")   // 줄바꿈 없이 끊긴 조각
        FileRewardLedger(file).append(listOf(RewardEntry(RewardKind.NEW_STAMP, 20, at, "st-2")))
        val reopened = FileRewardLedger(file)
        assertEquals(setOf("st-1", "st-2"), reopened.stampedStationIds())
    }

    @Test
    fun `충전 요약·발견 기록도 끊긴 마지막 줄 뒤에 붙여 쓰지 않는다`() = runBlocking {
        val seen = File(dir, "seen2.txt").apply { writeText("st-a\nst-b") }   // 마지막 줄 줄바꿈 없음(정상 값)
        FileSeenStore(seen).add(listOf("st-c"))
        assertEquals(setOf("st-a", "st-b", "st-c"), FileSeenStore(seen).seen.value)
        val sum = File(dir, "sum2.txt").apply { writeText("양재|10.0|3000|2000|10|2026-11-03T12:00\n양재|5.") }
        FileSummaryLog(sum).append(com.chargecompass.app.rewards.ChargeSummary("반포", 20.0, 6000, 1000, 10, at))
        assertEquals(listOf("양재", "반포"), FileSummaryLog(sum).summaries.value.map { it.stationName })
    }
}
