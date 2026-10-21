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

    @Test fun zeroLimitIsAlwaysBlocked() {
        val rule = reddit.copy(dailyLimitMin = 0, schedules = listOf(Schedule(startMin = 22 * 60, endMin = 7 * 60)))
        assertEquals(BlockReason.ALWAYS, RuleEngine.blockReason(rule, 0, at(2, 12), unlocked = false))
        assertEquals(BlockReason.ALWAYS, RuleEngine.blockReason(rule, 0, at(2, 23), unlocked = false))
        assertNull(RuleEngine.blockReason(rule, 0, at(2, 12), unlocked = true))
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
        val reels = Rule("r", TargetType.IG_REELS, "", "Instagram Reels")
        assertTrue(RuleEngine.matches(reels, ActiveTarget("com.instagram.android", isReels = true)))
        assertFalse(RuleEngine.matches(reels, ActiveTarget("com.instagram.android", isReels = false)))
        assertTrue(RuleEngine.matches(reels, ActiveTarget("com.android.chrome", url = "www.instagram.com/reels/abc")))
        assertTrue(RuleEngine.matches(reels, ActiveTarget("com.android.chrome", url = "instagram.com/reel/xyz")))
        assertFalse(RuleEngine.matches(reels, ActiveTarget("com.android.chrome", url = "instagram.com/fulano")))
        // Reels não pega o Shorts e vice-versa.
        assertFalse(RuleEngine.matches(reels, ActiveTarget("com.google.android.youtube", isShorts = true)))
        assertFalse(RuleEngine.matches(shorts, ActiveTarget("com.instagram.android", isReels = true)))
        assertTrue(TargetType.IG_REELS.isSection && TargetType.YT_SHORTS.isSection && !TargetType.APP.isSection)
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

/** Contagem de tempo e bloqueio com vários alvos na tela e liberações por regra. */
class UsageAndUnlockTest {
    private val now = LocalDateTime.of(2026, 10, 21, 15, 0)
    private val nowMs = 1_000_000L

    private val youtubeApp = Rule("yt", TargetType.APP, "com.google.android.youtube", "YouTube", dailyLimitMin = 30)
    private val shorts = Rule("sh", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 0)
    private val netflix = Rule("nf", TargetType.APP, "com.netflix.mediaclient", "Netflix", dailyLimitMin = 60)
    private val reddit = Rule("rd", TargetType.APP, "com.reddit.frontpage", "Reddit", dailyLimitMin = 15)
    private val rules = listOf(youtubeApp, shorts, netflix, reddit)

    private val youtubeVideo = ActiveTarget("com.google.android.youtube")
    private val youtubeShorts = ActiveTarget("com.google.android.youtube", isShorts = true)
    private val netflixPip = ActiveTarget("com.netflix.mediaclient")
    private val launcher = ActiveTarget("com.google.android.apps.nexuslauncher")
    private val redditApp = ActiveTarget("com.reddit.frontpage")

    private fun blocking(target: ActiveTarget, used: Map<String, Long> = emptyMap(), unlocks: Map<String, Long> = emptyMap(), devotion: Boolean = false) =
        RuleEngine.blocking(rules, target, used, now, nowMs, unlocks, devotion).map { it.first.id to it.second }

    @Test fun pipVideoCountsWhileAnotherAppIsInFront() {
        // Netflix em picture-in-picture por cima da tela inicial: o tempo é da Netflix.
        assertEquals(listOf("nf"), RuleEngine.inUse(rules, listOf(launcher, netflixPip)).map { it.id })
        // YouTube em PiP enquanto o Reddit está aberto: contam os dois.
        assertEquals(listOf("yt", "rd"), RuleEngine.inUse(rules, listOf(redditApp, youtubeVideo)).map { it.id })
    }

    @Test fun ruleSeenInTwoWindowsCountsOnce() {
        assertEquals(listOf("yt"), RuleEngine.inUse(rules, listOf(youtubeVideo, youtubeVideo)).map { it.id })
    }

    @Test fun nothingOnScreenFromTheRulesCountsNothing() {
        assertTrue(RuleEngine.inUse(rules, listOf(launcher)).isEmpty())
        assertTrue(RuleEngine.inUse(listOf(reddit.copy(enabled = false)), listOf(redditApp)).isEmpty())
    }

    @Test fun unlockedRuleStillCountsTime() {
        // A liberação não pausa o contador: o tempo livre gasta o limite do dia.
        val unlocks = mapOf("rd" to nowMs + 60_000)
        assertEquals(listOf("rd"), RuleEngine.inUse(rules, listOf(redditApp)).map { it.id })
        assertTrue(blocking(redditApp, mapOf("rd" to 20 * 60L), unlocks).isEmpty())
        // Acabou a liberação: com 20 min usados de 15, bloqueia de novo.
        assertEquals(listOf("rd" to BlockReason.LIMIT), blocking(redditApp, mapOf("rd" to 20 * 60L), mapOf("rd" to nowMs)))
    }

    @Test fun unlockFreesOnlyItsRule() {
        val used = mapOf("rd" to 15 * 60L, "nf" to 60 * 60L)
        val unlocks = mapOf("rd" to nowMs + 60_000)
        assertTrue(blocking(redditApp, used, unlocks).isEmpty())
        assertEquals(listOf("nf" to BlockReason.LIMIT), blocking(netflixPip, used, unlocks))
    }

    @Test fun appWithTwoRulesNeedsBothUnlocked() {
        // Shorts casa com a regra do Shorts e com a do app YouTube.
        val used = mapOf("yt" to 30 * 60L)
        assertEquals(listOf("yt" to BlockReason.LIMIT, "sh" to BlockReason.ALWAYS), blocking(youtubeShorts, used))
        assertEquals(listOf("yt" to BlockReason.LIMIT), blocking(youtubeShorts, used, mapOf("sh" to nowMs + 1)))
        assertTrue(blocking(youtubeShorts, used, mapOf("sh" to nowMs + 1, "yt" to nowMs + 1)).isEmpty())
        // Vídeo normal do YouTube não é Shorts: só a regra do app.
        assertEquals(listOf("yt" to BlockReason.LIMIT), blocking(youtubeVideo, used))
    }

    @Test fun devotionIgnoresUnlocks() {
        val unlocks = rules.associate { it.id to nowMs + 60_000 }
        assertEquals(listOf("rd" to BlockReason.DEVOTION), blocking(redditApp, unlocks = unlocks, devotion = true))
        assertTrue(blocking(launcher, devotion = true).isEmpty())
    }

    @Test fun unlockEndsExactlyAtItsTime() {
        assertTrue(RuleEngine.isUnlocked(reddit, mapOf("rd" to nowMs + 1), nowMs))
        assertFalse(RuleEngine.isUnlocked(reddit, mapOf("rd" to nowMs), nowMs))
        assertFalse(RuleEngine.isUnlocked(reddit, emptyMap(), nowMs))
    }
}
