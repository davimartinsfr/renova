package dev.renova.app

import dev.renova.app.domain.UsageHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHistoryTest {

    @Test fun goingForwardAndBackRestoresTheDay() {
        val day21 = mapOf("calendar" to 300L)
        val (h22, usage22) = UsageHistory.rollOver(emptyMap(), "2026-10-21", day21, "2026-10-22")
        assertTrue(usage22.isEmpty())
        val (h21, usage21) = UsageHistory.rollOver(h22, "2026-10-22", mapOf("calendar" to 5L), "2026-10-21")
        assertEquals(day21, usage21)
        // E o dia 22 continua guardado com o que foi usado nele.
        assertEquals(mapOf("calendar" to 5L), h21["2026-10-22"])
    }

    @Test fun farFutureDateDoesNotEraseToday() {
        val (future, _) = UsageHistory.rollOver(emptyMap(), "2026-10-21", mapOf("a" to 60L), "2027-10-21")
        val (_, back) = UsageHistory.rollOver(future, "2027-10-21", emptyMap(), "2026-10-21")
        assertEquals(mapOf("a" to 60L), back)
    }

    @Test fun keepsOnlyTheLastVisitedDays() {
        var history = emptyMap<String, Map<String, Long>>()
        for (day in 1..10) history = UsageHistory.record(history, "2026-10-%02d".format(day), mapOf("a" to day.toLong()))
        assertEquals(UsageHistory.KEEP_DAYS, history.size)
        assertEquals((3..10).map { "2026-10-%02d".format(it) }, history.keys.toList())
    }
}
