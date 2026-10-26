package com.chargecompass.core.dex

import com.chargecompass.core.model.Station

/** 도감 지역. 실서비스에서는 지역 단위로 끊어 "끝낼 수 있는 목표"를 준다. */
data class DexRegion(val id: String, val label: String)

/** 충전 출력 타입(포켓몬 도감의 속성처럼). */
enum class PowerType(val label: String) {
    ULTRA("초급속"), FAST("급속"), SLOW("완속");

    companion object {
        fun of(maxKw: Int): PowerType = when {
            maxKw >= 200 -> ULTRA
            maxKw >= 50 -> FAST
            else -> SLOW
        }
    }
}

/** 같은 곳을 다시 충전하면 메달이 동 → 은 → 금으로 올라간다. */
enum class MedalTier(val label: String, val minVisits: Int) {
    BRONZE("동", 1), SILVER("은", 3), GOLD("금", 5);

    companion object {
        fun of(visits: Int): MedalTier? = entries.lastOrNull { visits >= it.minVisits }

        /** 다음 단계까지 남은 충전 횟수. 금이면 null. */
        fun visitsToNext(visits: Int): Int? = entries.firstOrNull { visits < it.minVisits }?.let { it.minVisits - visits }
    }
}

/** 못 본 곳 → 추천에 나와 발견한 곳 → 충전해 등록한 곳. */
enum class DexState { UNSEEN, SEEN, REGISTERED }

data class DexEntry(
    val no: Int,
    val station: Station,
    val type: PowerType,
    val state: DexState,
    val visits: Int,
    val tier: MedalTier?,
)

object Dex {
    /** 시연 경로(판교 → 서울역) 기준 지역. 도감 번호도 이 순서를 따른다. */
    val regions = listOf(
        DexRegion("downtown", "서울 도심"),
        DexRegion("gangnam", "강남·서초"),
        DexRegion("gyeonggi", "경기 남부"),
    )

    fun build(stations: List<Station>, seen: Set<String>, visits: Map<String, Int>): List<DexEntry> {
        val order = regions.map { it.id }
        val sorted = stations.withIndex().sortedWith(compareBy({ order.indexOf(it.value.region).let { i -> if (i < 0) order.size else i } }, { it.index }))
        return sorted.mapIndexed { i, (_, st) ->
            val v = visits[st.id] ?: 0
            val state = when {
                v > 0 -> DexState.REGISTERED
                st.id in seen -> DexState.SEEN
                else -> DexState.UNSEEN
            }
            DexEntry(i + 1, st, PowerType.of(st.maxKw), state, v, MedalTier.of(v))
        }
    }

    /** (등록한 곳, 전체) */
    fun progress(dex: List<DexEntry>, regionId: String): Pair<Int, Int> {
        val inRegion = dex.filter { it.station.region == regionId }
        return inRegion.count { it.state == DexState.REGISTERED } to inRegion.size
    }

    fun label(no: Int): String = "No.%03d".format(no)
}
