package dev.renova.app.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** Uma etapa dos "7 minutos com o Senhor" (método CPPCCTP). */
data class DevotionStep(
    val title: String,
    val seconds: Int,
    val instruction: String,
    val references: String,
)

/** Trecho para orar-ler: livro (base 0), capítulo e versículos (base 1, inclusivos). */
data class VerseRef(val book: Int, val chapter: Int, val fromVerse: Int, val toVerse: Int)

object Devotion {
    /** O dia da devoção vira às 4h: de madrugada ainda vale o dia anterior. */
    const val DAY_STARTS_AT_HOUR = 4L

    /** Etapa em que aparecem os versículos do dia. */
    const val PRAY_READ_STEP = 2

    val STEPS = listOf(
        DevotionStep(
            "Invocar", 30,
            "Invoque o nome do Senhor para voltar a mente para o espírito: \"Ó Senhor Jesus!\"",
            "1Co 12:3; Rm 8:6; 10:12-13",
        ),
        DevotionStep(
            "Orar", 60,
            "Abra e amoleça o coração, esvazie-se diante dEle. Diga ao Senhor que você O ama.",
            "2Co 3:16; Sl 62:8",
        ),
        DevotionStep(
            "Orar-ler", 150,
            "Ore sobre um ou dois versículos com o espírito exercitado, transformando as palavras em oração pessoal.",
            "Ef 6:17-18; 2Tm 3:16",
        ),
        DevotionStep(
            "Confissão", 60,
            "Confesse os pecados e ofensas que estão na consciência e tire todo bloqueio à comunhão. Peça perdão e purificação.",
            "1Jo 1:7, 9; Sl 66:18; Is 59:1-2",
        ),
        DevotionStep(
            "Consagração", 30,
            "Apresente-se ao Senhor de novo, dando a Ele todo o terreno em você.",
            "Rm 12:1-2; 6:13, 19; Mc 12:30",
        ),
        DevotionStep(
            "Ação de graças", 30,
            "Dê graças por todas as coisas: pelas pessoas, situações e coisas da sua vida. Louve ao Senhor.",
            "Ef 5:20; 1Ts 5:18",
        ),
        DevotionStep(
            "Petição", 60,
            "Peça ao Senhor pelas necessidades, pelo crescimento e pelas pessoas que precisam de salvação.",
            "1Tm 2:1; Ef 6:18; Mt 7:7; Sl 143:8",
        ),
    )

    val TOTAL_SECONDS = STEPS.sumOf { it.seconds }

    fun day(now: LocalDateTime): LocalDate = now.minusHours(DAY_STARTS_AT_HOUR).toLocalDate()

    fun dayKey(now: LocalDateTime): String = day(now).toString()

    fun isDone(doneDay: String?, now: LocalDateTime): Boolean = doneDay == dayKey(now)

    /** Etapa atual e segundos já passados nela, dado o tempo total decorrido; null ao terminar. */
    fun stepAt(elapsedSeconds: Int): Pair<Int, Int>? {
        var rest = elapsedSeconds
        STEPS.forEachIndexed { i, step ->
            if (rest < step.seconds) return i to rest
            rest -= step.seconds
        }
        return null
    }

    /** Versículo do dia: percorre a lista em ordem, um por dia, recomeçando ao fim. */
    fun <T> pickForDay(list: List<T>, day: LocalDate): T? =
        if (list.isEmpty()) null else list[Math.floorMod(day.toEpochDay(), list.size.toLong()).toInt()]
}
