package dev.renova.app

import dev.renova.app.domain.ActiveTarget
import dev.renova.app.domain.BlockReason
import dev.renova.app.domain.Rule
import dev.renova.app.domain.RuleEngine
import dev.renova.app.domain.RuleEngine.isActive
import dev.renova.app.domain.Schedule
import dev.renova.app.domain.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RuleEngineTest {
    // 2026-10-19 é uma segunda-feira.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 18 + day, h, m)

    private val reddit = Rule("r", TargetType.APP, "com.reddit.frontpage", "Reddit")

    @Test fun scheduleSameDay() {
        val s = Schedule(setOf(1, 2, 3, 4, 5), 8 * 60, 18 * 60)
        assertTrue(s.isActive(at(1, 8)))
        assertTrue(s.isActive(at(1, 17, 59)))
        assertFalse(s.isActive(at(1, 18)))
        assertFalse(s.isActive(at(1, 7, 59)))
        assertFalse(s.isActive(at(6, 12))) // sábado
    }

    @Test fun scheduleOvernightBelongsToStartDay() {
        val s = Schedule(setOf(7), 23 * 60, 6 * 60) // domingo 23h → segunda 6h
        assertTrue(s.isActive(at(0, 23, 30)))   // domingo
        assertTrue(s.isActive(at(1, 5, 59)))    // segunda de madrugada
        assertFalse(s.isActive(at(1, 23, 30)))  // segunda à noite não está nos dias
        assertFalse(s.isActive(at(0, 5)))       // domingo de madrugada (sábado não está nos dias)
    }

    @Test fun scheduleWholeDay() {
        val s = Schedule(setOf(1), 0, 0)
        assertTrue(s.isActive(at(1, 0)))
        assertTrue(s.isActive(at(1, 23, 59)))
        assertFalse(s.isActive(at(2, 0)))
    }

    @Test fun limitAndUnlock() {
        val rule = reddit.copy(dailyLimitMin = 10)
        assertNull(RuleEngine.blockReason(rule, 599, at(1, 12), unlocked = false))
        assertEquals(BlockReason.LIMIT, RuleEngine.blockReason(rule, 600, at(1, 12), unlocked = false))
        assertNull(RuleEngine.blockReason(rule, 600, at(1, 12), unlocked = true))
        assertNull(RuleEngine.blockReason(rule.copy(enabled = false), 600, at(1, 12), unlocked = false))
    }

    @Test fun scheduleWinsOverRemainingLimit() {
        val rule = reddit.copy(dailyLimitMin = 30, schedules = listOf(Schedule(startMin = 22 * 60, endMin = 7 * 60)))
        assertEquals(BlockReason.SCHEDULE, RuleEngine.blockReason(rule, 0, at(2, 23), unlocked = false))
        assertNull(RuleEngine.blockReason(rule, 0, at(2, 12), unlocked = false))
    }

    @Test fun urlMatching() {
        assertTrue(RuleEngine.urlMatches("reddit.com/r/brasil", "reddit.com"))
        assertTrue(RuleEngine.urlMatches("https://www.reddit.com", "reddit.com"))
        assertTrue(RuleEngine.urlMatches("old.reddit.com", "reddit.com"))
        assertFalse(RuleEngine.urlMatches("notreddit.com", "reddit.com"))
        assertTrue(RuleEngine.urlMatches("m.youtube.com/shorts/abc", "youtube.com/shorts"))
        assertFalse(RuleEngine.urlMatches("youtube.com/watch?v=1", "youtube.com/shorts"))
        assertFalse(RuleEngine.urlMatches("pesquisar reddit", "reddit.com"))
    }

    @Test fun targetMatching() {
        val shorts = Rule("s", TargetType.YT_SHORTS, "", "YouTube Shorts")
        assertTrue(RuleEngine.matches(shorts, ActiveTarget("com.google.android.youtube", isShorts = true)))
        assertFalse(RuleEngine.matches(shorts, ActiveTarget("com.google.android.youtube", isShorts = false)))
        assertTrue(RuleEngine.matches(shorts, ActiveTarget("com.android.chrome", url = "youtube.com/shorts/x")))
        assertTrue(RuleEngine.matches(reddit, ActiveTarget("com.reddit.frontpage")))
        // O app bloqueia também o próprio site no navegador.
        assertTrue(RuleEngine.matches(reddit, ActiveTarget("com.android.chrome", url = "www.reddit.com/r/all")))
        assertFalse(RuleEngine.matches(reddit, ActiveTarget("com.android.chrome", url = "notreddit.com")))
        assertFalse(RuleEngine.matches(reddit, ActiveTarget("com.android.chrome", url = null)))
        val unknown = Rule("u", TargetType.APP, "com.exemplo.app", "Exemplo")
        assertFalse(RuleEngine.matches(unknown, ActiveTarget("com.android.chrome", url = "exemplo.app")))
        val site = Rule("w", TargetType.WEBSITE, "reddit.com", "reddit.com")
        assertFalse(RuleEngine.matches(site, ActiveTarget("com.android.chrome", url = "www.reddit.com/r/all")))
    }

    @Test fun loosening() {
        val base = reddit.copy(dailyLimitMin = 15, schedules = listOf(Schedule(setOf(1, 2, 3, 4, 5), 8 * 60, 18 * 60)))
        assertFalse(RuleEngine.isLoosening(null, base))
        assertTrue(RuleEngine.isLoosening(base, null))
        assertTrue(RuleEngine.isLoosening(base, base.copy(enabled = false)))
        assertTrue(RuleEngine.isLoosening(base, base.copy(dailyLimitMin = 20)))
        assertTrue(RuleEngine.isLoosening(base, base.copy(dailyLimitMin = null)))
        assertFalse(RuleEngine.isLoosening(base, base.copy(dailyLimitMin = 5)))
        assertTrue(RuleEngine.isLoosening(base, base.copy(schedules = listOf(Schedule(setOf(1, 2, 3, 4, 5), 9 * 60, 18 * 60)))))
        assertFalse(RuleEngine.isLoosening(base, base.copy(schedules = listOf(Schedule(startMin = 0, endMin = 0)))))
        assertTrue(RuleEngine.isLoosening(base, base.copy(target = "com.other")))
        assertFalse(RuleEngine.isLoosening(base.copy(enabled = false), null))
    }
}

class UpdaterTest {
    @Test fun versionComparison() {
        assertTrue(dev.renova.app.data.Updater.isNewer("1.1", "1.0"))
        assertTrue(dev.renova.app.data.Updater.isNewer("1.10", "1.9"))
        assertTrue(dev.renova.app.data.Updater.isNewer("2", "1.9.9"))
        assertFalse(dev.renova.app.data.Updater.isNewer("1.0", "1.0"))
        assertFalse(dev.renova.app.data.Updater.isNewer("1.0", "1.0.1"))
    }
}
