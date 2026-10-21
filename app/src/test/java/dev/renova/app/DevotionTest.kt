package dev.renova.app

import dev.renova.app.data.Bible
import dev.renova.app.data.DevotionVerses
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.DevotionPick
import dev.renova.app.domain.VerseRef
import dev.renova.app.ui.devotionReturnsWhen
import dev.renova.app.ui.stepToneLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import kotlin.random.Random

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

    @Test fun pickKeepsTodaysVerseWhileItIsInTheList() {
        val a = VerseRef(48, 6, 17, 18)
        val b = VerseRef(54, 3, 16, 16)
        val saved = DevotionPick("2026-10-21", a)
        assertEquals(saved, Devotion.pickForDay(listOf(a, b), saved, "2026-10-21"))
        // Removido da lista no mesmo dia: sorteia outro.
        assertEquals(DevotionPick("2026-10-21", b), Devotion.pickForDay(listOf(b), saved, "2026-10-21"))
    }

    @Test fun newDayAvoidsRepeatingYesterday() {
        val a = VerseRef(48, 6, 17, 18)
        val b = VerseRef(54, 3, 16, 16)
        val saved = DevotionPick("2026-10-20", a)
        repeat(20) { seed ->
            assertEquals(b, Devotion.pickForDay(listOf(a, b), saved, "2026-10-21", Random(seed))?.ref)
        }
        // Com um trecho só, ele se repete.
        assertEquals(a, Devotion.pickForDay(listOf(a), saved, "2026-10-21")?.ref)
        assertNull(Devotion.pickForDay(emptyList(), saved, "2026-10-21"))
    }

    @Test fun pickIsRandomAmongTheList() {
        val list = (1..5).map { VerseRef(18, it, 1, 1) }
        val picks = (0..200).mapNotNull { Devotion.pickForDay(list, null, "2026-10-21", Random(it))?.ref }.toSet()
        assertEquals(list.toSet(), picks)
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

    @Test fun poolUsesUserVersesWhenThereAreAny() {
        assertEquals(listOf(c), DevotionVerses.pool(listOf(a, b), listOf(c)))
    }

    @Test fun poolFallsBackToBuiltIn() {
        assertEquals(listOf(a, b), DevotionVerses.pool(listOf(a, b), emptyList()))
    }
}

class DevotionReturnTextTest {
    @Test fun doneBeforeFourReturnsToday() {
        assertEquals("hoje", devotionReturnsWhen(2))
        assertEquals("amanhã", devotionReturnsWhen(4))
        assertEquals("amanhã", devotionReturnsWhen(23))
    }
}

class DevotionDoneTest {
    private val doneDay = "2026-10-21"

    @Test fun doneTodayOrLaterCounts() {
        assertTrue(Devotion.isDone(doneDay, LocalDateTime.of(2026, 10, 21, 9, 0)))
        // Fuso mudado para oeste: agora é 20/10 de noite, mas os 7 minutos já foram feitos no 21.
        assertTrue(Devotion.isDone(doneDay, LocalDateTime.of(2026, 10, 20, 22, 0)))
        // Dia seguinte, depois das 4h: precisa fazer de novo.
        assertFalse(Devotion.isDone(doneDay, LocalDateTime.of(2026, 10, 22, 4, 0)))
        assertFalse(Devotion.isDone(null, LocalDateTime.of(2026, 10, 21, 9, 0)))
        assertFalse(Devotion.isDone("lixo", LocalDateTime.of(2026, 10, 21, 9, 0)))
    }
}
