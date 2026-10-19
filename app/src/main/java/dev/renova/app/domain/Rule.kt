package dev.renova.app.domain

import kotlinx.serialization.Serializable

@Serializable
enum class TargetType { APP, YT_SHORTS, WEBSITE }

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
    /** APP: nome do pacote. WEBSITE: domínio com caminho opcional (ex.: "youtube.com/shorts"). YT_SHORTS: vazio. */
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
    val url: String? = null,
)

enum class BlockReason { SCHEDULE, LIMIT, DEVOTION }
