package dev.renova.app.domain

import java.time.LocalDateTime

object RuleEngine {

    private const val MINUTES_PER_DAY = 24 * 60
    private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

    fun Schedule.isActive(now: LocalDateTime): Boolean {
        val day = now.dayOfWeek.value
        val yesterday = if (day == 1) 7 else day - 1
        val minute = now.hour * 60 + now.minute
        return when {
            startMin == endMin -> day in days
            startMin < endMin -> day in days && minute >= startMin && minute < endMin
            else -> (day in days && minute >= startMin) || (yesterday in days && minute < endMin)
        }
    }

    fun blockReason(
        rule: Rule,
        usedSecondsToday: Long,
        now: LocalDateTime,
        unlocked: Boolean,
    ): BlockReason? {
        if (!rule.enabled || unlocked) return null
        if (rule.schedules.any { it.isActive(now) }) return BlockReason.SCHEDULE
        val limit = rule.dailyLimitMin
        if (limit != null && usedSecondsToday >= limit * 60L) return BlockReason.LIMIT
        return null
    }

    fun matches(rule: Rule, active: ActiveTarget): Boolean {
        if (!rule.enabled) return false
        return when (rule.type) {
            TargetType.APP -> active.packageName == rule.target
            TargetType.YT_SHORTS -> active.isShorts ||
                (active.url != null && urlMatches(active.url, "youtube.com/shorts"))
            TargetType.WEBSITE -> active.url != null && urlMatches(active.url, rule.target)
        }
    }

    /** Compara o texto da barra de endereço (com ou sem esquema/www) com "dominio.com[/caminho]". */
    fun urlMatches(url: String, pattern: String): Boolean {
        val (host, path) = splitUrl(url) ?: return false
        val (pHost, pPath) = splitUrl(pattern) ?: return false
        val hostOk = host == pHost || host.endsWith(".$pHost")
        return hostOk && path.startsWith(pPath)
    }

    fun normalizeDomain(input: String): String {
        val (host, path) = splitUrl(input) ?: return ""
        return host + path.trimEnd('/')
    }

    private fun splitUrl(raw: String): Pair<String, String>? {
        var s = raw.trim().lowercase()
        if (s.isEmpty() || s.contains(' ')) return null
        s = s.substringAfter("://")
        val slash = s.indexOf('/')
        var host = if (slash >= 0) s.substring(0, slash) else s
        val path = if (slash >= 0) s.substring(slash) else ""
        host = host.substringBefore(':').removePrefix("www.").removePrefix("m.")
        if (!host.contains('.')) return null
        return host to path
    }

    /**
     * Uma mudança "afrouxa" o bloqueio quando algum minuto da semana deixa de ser bloqueado,
     * o limite diário aumenta/some, a regra é desativada/apagada ou o alvo muda.
     * Afrouxar exige leitura; apertar é livre.
     */
    fun isLoosening(old: Rule?, new: Rule?): Boolean {
        if (old == null || !old.enabled) return false
        if (new == null || !new.enabled) return true
        if (old.type != new.type || old.target != new.target) return true
        val oldLimit = old.dailyLimitMin
        val newLimit = new.dailyLimitMin
        if (oldLimit != null && (newLimit == null || newLimit > oldLimit)) return true
        val oldMask = weekMask(old.schedules)
        val newMask = weekMask(new.schedules)
        oldMask.andNot(newMask)
        return !oldMask.isEmpty
    }

    private fun weekMask(schedules: List<Schedule>): java.util.BitSet {
        val mask = java.util.BitSet(MINUTES_PER_WEEK)
        for (s in schedules) for (day in s.days) {
            val base = (day - 1) * MINUTES_PER_DAY
            val length = when {
                s.startMin == s.endMin -> MINUTES_PER_DAY
                s.startMin < s.endMin -> s.endMin - s.startMin
                else -> MINUTES_PER_DAY - s.startMin + s.endMin
            }
            val start = if (s.startMin == s.endMin) 0 else s.startMin
            for (m in 0 until length) mask.set((base + start + m) % MINUTES_PER_WEEK)
        }
        return mask
    }
}
