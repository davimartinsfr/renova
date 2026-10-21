package dev.renova.app.domain

/**
 * Uso dos últimos dias, em segundos por id de regra, por data ("2026-10-21"). Guardar mais de um dia
 * impede que mudar a data para frente e voltar zere o uso de hoje: a data de volta recupera o dela.
 */
object UsageHistory {
    /** Hoje e os 7 dias anteriores, numa semana normal de uso. */
    const val KEEP_DAYS = 8

    /**
     * Grava [seconds] como o uso de [day] e põe o dia por último. A ordem é a de visita, não a do
     * calendário: assim uma data muito à frente não apaga a de hoje, que só sai depois de [KEEP_DAYS]
     * outras datas visitadas.
     */
    fun record(history: Map<String, Map<String, Long>>, day: String, seconds: Map<String, Long>): Map<String, Map<String, Long>> {
        val updated = LinkedHashMap(history)
        updated.remove(day)
        updated[day] = seconds
        while (updated.size > KEEP_DAYS) updated.remove(updated.keys.first())
        return updated
    }

    /**
     * Virada de [leaving] para [entering]: guarda o uso do dia que sai e devolve o histórico novo com o
     * uso do dia que entra (vazio se a data é nova).
     */
    fun rollOver(
        history: Map<String, Map<String, Long>>,
        leaving: String,
        leavingSeconds: Map<String, Long>,
        entering: String,
    ): Pair<Map<String, Map<String, Long>>, Map<String, Long>> {
        val saved = record(history, leaving, leavingSeconds)
        val seconds = saved[entering].orEmpty()
        return record(saved, entering, seconds) to seconds
    }
}
