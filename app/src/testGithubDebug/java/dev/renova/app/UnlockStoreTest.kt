package dev.renova.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.data.AppStore
import dev.renova.app.domain.Rule
import dev.renova.app.domain.TargetType
import dev.renova.app.domain.UnlockOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Liberações por leitura gravadas por regra. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UnlockStoreTest {
    private val store get() = AppStore.get(ApplicationProvider.getApplicationContext())

    @Before fun rules() {
        runBlocking {
            store.clearUnlocks()
            store.saveRule(Rule("a", TargetType.APP, "com.reddit.frontpage", "Reddit", dailyLimitMin = 0))
            store.saveRule(Rule("b", TargetType.APP, "com.netflix.mediaclient", "Netflix", dailyLimitMin = 0))
        }
    }

    @Test fun grantUnlocksOnlyTheChosenRule() = runBlocking {
        val until = store.grantUnlock(UnlockOption.CHAPTER, listOf("a"))
        val unlocks = store.unlocks.first()
        assertEquals(until, unlocks["a"])
        assertFalse("b" in unlocks)
        assertTrue(store.isUnlocked(listOf("a")))
        assertFalse(store.isUnlocked(listOf("a", "b")))
    }

    @Test fun shorterReadingNeverCutsALongerUnlock() = runBlocking {
        val hour = store.grantUnlock(UnlockOption.CHAPTERS, listOf("a"))
        assertEquals(hour, store.grantUnlock(UnlockOption.VERSE, listOf("a")))
    }

    @Test fun editOnlyReadingUnlocksNoApp() = runBlocking {
        store.clearEditUnlock()
        store.grantUnlock(UnlockOption.CHAPTER, emptyList())
        assertFalse(store.isUnlocked(listOf("a")))
        assertFalse(store.isUnlocked(listOf("b")))
        assertTrue(store.isUnlockedForEdits())
    }

    @Test fun legacyGlobalUnlockStillFreesEveryRuleUntilItEnds() = runBlocking {
        val until = System.currentTimeMillis() + 10 * 60_000
        store.setLegacyUnlock(until)
        assertTrue(store.isUnlocked(listOf("a", "b")))
        // Uma leitura nova para "a" não tira o que restava da liberação antiga de "b".
        store.grantUnlock(UnlockOption.VERSE, listOf("a"))
        assertEquals(until, store.unlocks.first()["b"])
        store.setLegacyUnlock(System.currentTimeMillis() - 1)
        assertTrue(store.isUnlocked(listOf("b")))
    }
}
