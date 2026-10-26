package com.chargecompass.app.store

import com.chargecompass.app.rewards.ChargeSummary
import com.chargecompass.core.Defaults
import com.chargecompass.core.rewards.RewardEntry
import com.chargecompass.core.rewards.RewardHistory
import com.chargecompass.core.rewards.RewardKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

/** 사용자가 고른 멤버십과 차종. */
interface MembershipStore {
    val owned: StateFlow<Set<String>>
    val vehicleModelId: StateFlow<String?>
    suspend fun set(ids: Set<String>)
    suspend fun setVehicle(id: String)
}

/** 포인트 원장. 현금성 포인트가 아니라 제휴 할인 쿠폰 교환용이며, 12개월이 지나면 잔액에서 빠진다. */
interface RewardLedger : RewardHistory {
    val entries: StateFlow<List<RewardEntry>>
    suspend fun append(e: List<RewardEntry>)
    fun balance(now: LocalDateTime): Int =
        entries.value.filter { it.at.isAfter(now.minusMonths(Defaults.POINT_VALIDITY_MONTHS)) }.sumOf { it.points }

    override fun stampedStationIds(): Set<String> =
        entries.value.filter { it.kind == RewardKind.NEW_STAMP }.mapNotNull { it.stationId }.toSet()

    override fun eventBonusDates(eventId: String): Set<LocalDate> =
        entries.value.filter { it.kind == RewardKind.EVENT_BONUS && it.eventId == eventId }.mapNotNull { it.eventDate }.toSet()

    override fun lastChargeAt(stationId: String, notAfter: LocalDateTime): LocalDateTime? =
        entries.value.filter { it.kind == RewardKind.CHARGE && it.stationId == stationId && !it.at.isAfter(notAfter) }.maxOfOrNull { it.at }
}

open class InMemoryRewardLedger(initial: List<RewardEntry> = emptyList()) : RewardLedger {
    protected val _entries = MutableStateFlow(initial)
    override val entries: StateFlow<List<RewardEntry>> = _entries
    override suspend fun append(e: List<RewardEntry>) { _entries.value = _entries.value + e }
}

/** 한 줄에 항목 하나: kind|points|at|stationId|eventId|eventDate|regionId (빈 값은 빈 칸, 예전 줄은 regionId 없음). */
class FileRewardLedger(private val file: File) : InMemoryRewardLedger(read(file)) {
    override suspend fun append(e: List<RewardEntry>) {
        super.append(e)
        appendLines(file, e.joinToString("") { encode(it) + "\n" })
    }

    private companion object {
        fun encode(e: RewardEntry) =
            listOf(e.kind.name, e.points, e.at, e.stationId ?: "", e.eventId ?: "", e.eventDate ?: "", e.regionId ?: "").joinToString("|")

        fun read(file: File): List<RewardEntry> = if (!file.exists()) emptyList() else file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split("|")
                runCatching {
                    RewardEntry(
                        RewardKind.valueOf(p[0]), p[1].toInt(), LocalDateTime.parse(p[2]),
                        p[3].ifEmpty { null }, p[4].ifEmpty { null }, p[5].ifEmpty { null }?.let(LocalDate::parse),
                        p.getOrNull(6)?.ifEmpty { null },
                    )
                }.getOrNull()
            }
    }
}

/** 첫 줄: 차종 id, 둘째 줄: 멤버십 id(쉼표 구분). */
class FileMembershipStore(private val file: File) : MembershipStore {
    private val lines = if (file.exists()) file.readLines() else emptyList()
    private val _vehicle = MutableStateFlow(lines.getOrNull(0)?.ifBlank { null })
    private val _owned = MutableStateFlow(lines.getOrNull(1)?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty())
    override val owned: StateFlow<Set<String>> = _owned
    override val vehicleModelId: StateFlow<String?> = _vehicle

    override suspend fun set(ids: Set<String>) { _owned.value = ids; save() }
    override suspend fun setVehicle(id: String) { _vehicle.value = id; save() }

    /** 임시 파일에 쓴 뒤 바꿔치기한다 — 쓰는 도중 꺼져도 이전 내용이 남는다. */
    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText("${_vehicle.value.orEmpty()}\n${_owned.value.sorted().joinToString(",")}\n")
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/** 끝난 충전의 요약 기록(홈의 최근 7일 절약 차트). */
interface SummaryLog {
    val summaries: StateFlow<List<ChargeSummary>>
    suspend fun append(s: ChargeSummary)
}

open class InMemorySummaryLog(initial: List<ChargeSummary> = emptyList()) : SummaryLog {
    protected val _summaries = MutableStateFlow(initial)
    override val summaries: StateFlow<List<ChargeSummary>> = _summaries
    override suspend fun append(s: ChargeSummary) { _summaries.value = _summaries.value + s }
}

/** 한 줄에 하나: stationName|chargedKwh|costWon|savingWon|points|endedAt */
class FileSummaryLog(private val file: File) : InMemorySummaryLog(read(file)) {
    override suspend fun append(s: ChargeSummary) {
        super.append(s)
        appendLines(file, listOf(s.stationName.replace("|", " "), s.chargedKwh, s.costWon, s.savingWon, s.points, s.endedAt).joinToString("|") + "\n")
    }

    private companion object {
        fun read(file: File): List<ChargeSummary> = if (!file.exists()) emptyList() else file.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split("|")
                runCatching { ChargeSummary(p[0], p[1].toDouble(), p[2].toInt(), p[3].toInt(), p[4].toInt(), LocalDateTime.parse(p[5])) }.getOrNull()
            }
    }
}

/** 추천으로 보여 준(도감에서 "발견"한) 충전소. */
interface SeenStore {
    val seen: StateFlow<Set<String>>
    suspend fun add(ids: Collection<String>)
}

open class InMemorySeenStore(initial: Set<String> = emptySet()) : SeenStore {
    protected val _seen = MutableStateFlow(initial)
    override val seen: StateFlow<Set<String>> = _seen
    override suspend fun add(ids: Collection<String>) { _seen.value = _seen.value + ids }
}

/** 한 줄에 충전소 id 하나. 새로 발견한 것만 덧붙인다. */
class FileSeenStore(private val file: File) : InMemorySeenStore(if (file.exists()) file.readLines().filter { it.isNotBlank() }.toSet() else emptySet()) {
    override suspend fun add(ids: Collection<String>) {
        val fresh = ids.filter { it !in _seen.value }.distinct()
        if (fresh.isEmpty()) return
        super.add(fresh)
        appendLines(file, fresh.joinToString("") { it + "\n" })
    }
}

private val appendLock = Mutex()   // IO 스레드 여러 개가 같은 파일에 동시에 덧붙여 줄이 섞이지 않게

/**
 * 기록 파일 끝에 줄을 덧붙인다(IO 스레드에서). 이전 쓰기가 줄 중간에 끊겨 파일이 줄바꿈으로 끝나지 않으면
 * 먼저 줄을 바꿔, 끊긴 조각과 새 줄이 한 줄로 합쳐져 둘 다 읽히지 않는 일을 막는다(끊긴 조각만 읽을 때 버려진다).
 * 기록은 스탬프·메달 단계·미션 횟수의 근거라 지우거나 줄이지 않는다(한 줄 100바이트 안팎이라 수년 써도 수백 KB).
 */
internal suspend fun appendLines(file: File, text: String) = appendLock.withLock { withContext(Dispatchers.IO) {
    file.parentFile?.mkdirs()
    val needsNewline = file.exists() && file.length() > 0 && RandomAccessFile(file, "r").use { f -> f.seek(f.length() - 1); f.read() != '\n'.code }
    file.appendText((if (needsNewline) "\n" else "") + text)
} }
