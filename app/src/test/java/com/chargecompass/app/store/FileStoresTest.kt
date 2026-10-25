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
}
