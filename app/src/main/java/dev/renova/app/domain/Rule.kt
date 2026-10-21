package dev.renova.app.domain

import kotlinx.serialization.Serializable

/** WEBSITE é legado: regras de site saíram (o app bloqueia o próprio site) e são descartadas ao carregar. */
@Serializable
enum class TargetType {
    APP, YT_SHORTS, IG_REELS, WEBSITE;

    /** Bloqueia só uma área de vídeos curtos dentro do app; o resto do app continua liberado. */
    val isSection: Boolean get() = this == YT_SHORTS || this == IG_REELS
}

/**
 * Janela de bloqueio. [days] usa 1 = segunda ... 7 = domingo (java.time.DayOfWeek).
 * Minutos contados a partir da meia-noite. Se [startMin] > [endMin] a janela vira a
 * meia-noite (ex.: 23:00–06:00) e pertence ao dia em que começa. Se forem iguais, o dia todo.
 */
@Serializable
data class Schedule(
    val days: Set<Int> = (1..7).toSet(),
    val startMin: Int,
    val endMin: Int,
)

@Serializable
data class Rule(
    val id: String,
    val type: TargetType,
    /** APP: nome do pacote. YT_SHORTS e IG_REELS: vazio. */
    val target: String = "",
    val label: String,
    val dailyLimitMin: Int? = null,
    val schedules: List<Schedule> = emptyList(),
    val enabled: Boolean = true,
)

/** O que está na tela agora, segundo o serviço de acessibilidade. */
data class ActiveTarget(
    val packageName: String,
    val isShorts: Boolean = false,
    val isReels: Boolean = false,
    val url: String? = null,
)

/** ALWAYS: limite diário 0, a regra bloqueia o dia todo. */
enum class BlockReason { SCHEDULE, LIMIT, ALWAYS, DEVOTION }
