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
        val limit = rule.dailyLimitMin
        // Limite 0 bloqueia sempre, com ou sem horário: a tela diz isso em vez de "limite atingido".
        if (limit == 0) return BlockReason.ALWAYS
        if (rule.schedules.any { it.isActive(now) }) return BlockReason.SCHEDULE
        if (limit != null && usedSecondsToday >= limit * 60L) return BlockReason.LIMIT
        return null
    }

    /** Se [rule] está liberada por leitura em [nowMs]; [unlocks] guarda até quando, por id de regra. */
    fun isUnlocked(rule: Rule, unlocks: Map<String, Long>, nowMs: Long): Boolean = (unlocks[rule.id] ?: 0L) > nowMs

    /**
     * Regras que bloqueiam [target] agora, com o motivo. Enquanto os 7 minutos estão pendentes
     * ([devotionPending]), todo alvo das regras fica bloqueado por eles e as liberações por leitura não valem;
     * fora isso, cada regra só cede à própria liberação.
     */
    fun blocking(
        rules: List<Rule>,
        target: ActiveTarget,
        usedSeconds: Map<String, Long>,
        now: LocalDateTime,
        nowMs: Long,
        unlocks: Map<String, Long>,
        devotionPending: Boolean,
    ): List<Pair<Rule, BlockReason>> {
        val matched = rules.filter { matches(it, target) }
        if (devotionPending) return matched.map { it to BlockReason.DEVOTION }
        return matched.mapNotNull { rule ->
            blockReason(rule, usedSeconds[rule.id] ?: 0L, now, isUnlocked(rule, unlocks, nowMs))?.let { rule to it }
        }
    }

    /**
     * Regras em uso neste segundo: as que casam com algum alvo na tela (o app em primeiro plano,
     * um vídeo em picture-in-picture, a outra metade da tela dividida). Cada regra conta uma vez só.
     */
    fun inUse(rules: List<Rule>, targets: Collection<ActiveTarget>): List<Rule> =
        rules.filter { rule -> targets.any { matches(rule, it) } }

    fun matches(rule: Rule, active: ActiveTarget): Boolean {
        if (!rule.enabled) return false
        return when (rule.type) {
            TargetType.APP -> active.packageName == rule.target ||
                OfficialSites.of(rule.target).any { site -> active.url?.let { urlMatches(it, site) } == true }
            TargetType.YT_SHORTS -> active.isShorts ||
                (active.url != null && urlMatches(active.url, "youtube.com/shorts"))
            // "instagram.com/reel" cobre /reel/<id> e /reels/.
            TargetType.IG_REELS -> active.isReels ||
                (active.url != null && urlMatches(active.url, "instagram.com/reel"))
            TargetType.WEBSITE -> false
        }
    }

    /** Compara o texto da barra de endereço (com ou sem esquema/www) com "dominio.com[/caminho]". */
    fun urlMatches(url: String, pattern: String): Boolean {
        val (host, path) = splitUrl(url) ?: return false
        val (pHost, pPath) = splitUrl(pattern) ?: return false
        val hostOk = host == pHost || host.endsWith(".$pHost")
        return hostOk && path.startsWith(pPath)
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
     * Id da regra salva no editor. Trocar o tipo ou o app faz outra regra: com o id antigo, ela herdaria
     * o uso de hoje e a liberação do alvo anterior. A exceção é trocar uma seção pelo app inteiro dela
     * (Reels → Instagram): o uso de hoje continua contando, senão a troca dava o limite de novo sem leitura.
     * A liberação da seção não passa para o app ([AppStore.saveRule][dev.renova.app.data.AppStore.saveRule]
     * apaga). [newId] é sorteado uma vez por tela, para dois toques em Salvar numa regra nova não criarem duas.
     */
    fun idFor(original: Rule?, type: TargetType, target: String, newId: String): String =
        if (original != null && coversTarget(type, target, original)) original.id else newId

    /**
     * Uma mudança "afrouxa" o bloqueio quando algum minuto da semana deixa de ser bloqueado,
     * o limite diário aumenta/some, a regra é desativada/apagada ou o alvo muda.
     * Afrouxar exige leitura; apertar é livre.
     */
    fun isLoosening(old: Rule?, new: Rule?): Boolean {
        if (old == null || !old.enabled) return false
        if (new == null || !new.enabled) return true
        if ((old.type != new.type || old.target != new.target) && !coversTarget(new, old)) return true
        val newMask = blockedMask(new)
        // Bloqueada a semana inteira (limite 0 ou horário o tempo todo): não há como ser mais solta que antes.
        if (newMask.cardinality() == MINUTES_PER_WEEK) return false
        val oldLimit = old.dailyLimitMin
        val newLimit = new.dailyLimitMin
        if (oldLimit != null && (newLimit == null || newLimit > oldLimit)) return true
        val oldMask = blockedMask(old)
        oldMask.andNot(newMask)
        return !oldMask.isEmpty
    }

    /** Pacote do app de cada seção: trocar o Shorts pelo app YouTube inteiro aperta o bloqueio. */
    private val SECTION_APPS = mapOf(
        TargetType.YT_SHORTS to "com.google.android.youtube",
        TargetType.IG_REELS to "com.instagram.android",
    )

    /** Se o alvo de [new] contém o de [old]: o mesmo, ou o app inteiro no lugar de uma seção dele. */
    private fun coversTarget(new: Rule, old: Rule): Boolean = coversTarget(new.type, new.target, old)

    private fun coversTarget(type: TargetType, target: String, old: Rule): Boolean =
        (type == old.type && target == old.target) ||
            (type == TargetType.APP && target == SECTION_APPS[old.type])

    /** Minutos da semana em que [rule] bloqueia sem depender do uso: com limite 0, todos. */
    private fun blockedMask(rule: Rule): java.util.BitSet =
        if (rule.dailyLimitMin == 0) java.util.BitSet(MINUTES_PER_WEEK).apply { set(0, MINUTES_PER_WEEK) } else weekMask(rule.schedules)

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
