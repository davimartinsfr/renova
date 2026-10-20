package dev.renova.app

import dev.renova.app.data.Bible
import dev.renova.app.data.DevotionVerses
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.VerseRef
import dev.renova.app.ui.stepToneLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class DevotionTest {
    @Test fun stepsAddUpToSevenMinutes() {
        assertEquals(7, Devotion.STEPS.size)
        assertEquals(7 * 60, Devotion.TOTAL_SECONDS)
        assertEquals("Orar-ler", Devotion.STEPS[Devotion.PRAY_READ_STEP].title)
    }

    @Test fun stepAtWalksThroughSteps() {
        assertEquals(0 to 0, Devotion.stepAt(0))
        assertEquals(0 to 29, Devotion.stepAt(29))
        assertEquals(1 to 0, Devotion.stepAt(30))
        assertEquals(2 to 0, Devotion.stepAt(90))
        assertEquals(6 to 59, Devotion.stepAt(419))
        assertNull(Devotion.stepAt(420))
    }

    @Test fun dayTurnsAtFourAm() {
        assertEquals("2026-10-19", Devotion.dayKey(LocalDateTime.of(2026, 10, 20, 3, 59)))
        assertEquals("2026-10-20", Devotion.dayKey(LocalDateTime.of(2026, 10, 20, 4, 0)))
        assertTrue(Devotion.isDone("2026-10-19", LocalDateTime.of(2026, 10, 20, 2, 0)))
        assertFalse(Devotion.isDone("2026-10-19", LocalDateTime.of(2026, 10, 20, 4, 1)))
        assertFalse(Devotion.isDone(null, LocalDateTime.of(2026, 10, 20, 4, 1)))
    }

    @Test fun pickForDayRotatesThroughList() {
        val list = listOf("a", "b", "c")
        val day = LocalDate.of(2026, 10, 19)
        val picks = (0L..5L).map { Devotion.pickForDay(list, day.plusDays(it)) }
        assertEquals(picks.take(3).toSet(), list.toSet())
        assertEquals(picks.take(3), picks.drop(3))
        assertNull(Devotion.pickForDay(emptyList<String>(), day))
    }
}

class StepToneTest {
    @Test fun seventyPercentOfAlarmVolume() {
        assertEquals(null to 70, stepToneLevel(15, 15))
        assertEquals(null to 70, stepToneLevel(7, 15))
    }

    @Test fun neverBelowTenPercentOfMax() {
        // 70% de 2/15 daria 9%: sobe para 10% do máximo sem mexer na faixa.
        assertEquals(null to 75, stepToneLevel(2, 15))
        // Faixa abaixo de 10%: sobe a faixa só durante o tom.
        assertEquals(2 to 75, stepToneLevel(1, 15))
        assertEquals(1 to 70, stepToneLevel(0, 7))
    }
}

class VerseReferenceTest {
    private val bible by lazy { File("src/main/assets/bible/ara.json").inputStream().use { Bible.parse(it) } }

    @Test fun parsesNamesAbbreviationsAndRanges() {
        assertEquals(VerseRef(18, 23, 1, 2), bible.parseReference("Salmos 23:1-2"))
        assertEquals(VerseRef(18, 23, 1, 1), bible.parseReference("Sl 23:1"))
        assertEquals(VerseRef(61, 1, 9, 9), bible.parseReference("1 João 1:9"))
        assertEquals(VerseRef(42, 3, 16, 16), bible.parseReference("joao 3:16"))
        assertEquals(VerseRef(42, 3, 16, 16), bible.parseReference("Jo 3:16"))
        assertEquals(VerseRef(17, 1, 21, 21), bible.parseReference("Jó 1:21"))
        assertEquals(VerseRef(54, 3, 16, 16), bible.parseReference("2 Timóteo 3:16"))
        assertEquals("Efésios 6:17-18", bible.title(bible.parseReference("efesios 6:17–18")!!))
    }

    @Test fun rejectsInvalidReferences() {
        assertNull(bible.parseReference("Livro 1:1"))
        assertNull(bible.parseReference("Salmos 151:1"))
        assertNull(bible.parseReference("Salmos 23:7"))
        assertNull(bible.parseReference("Salmos 23:3-1"))
        assertNull(bible.parseReference("Salmos 23"))
    }

    @Test fun bundledListParsesCompletely() {
        val lines = File("src/main/assets/devotion/orar-ler.txt").readLines()
        val entries = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val parsed = DevotionVerses.parse(lines, bible)
        assertEquals("todas as linhas da lista devem ser referências válidas", entries.size, parsed.size)
        assertTrue(parsed.isNotEmpty())
    }
}

class DevotionVersesTest {
    private val a = VerseRef(48, 6, 17, 18)
    private val b = VerseRef(54, 3, 16, 16)
    private val c = VerseRef(18, 23, 1, 2)

    @Test fun mergeAppendsUserVersesAfterBuiltIn() {
        assertEquals(listOf(a, b, c), DevotionVerses.merge(listOf(a, b), listOf(c)))
    }

    @Test fun mergeSkipsDuplicates() {
        assertEquals(listOf(a, b, c), DevotionVerses.merge(listOf(a, b), listOf(b, c, c)))
    }
}
