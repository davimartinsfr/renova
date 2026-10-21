package dev.renova.app.domain

/** Tela do Renova que está na frente, para o serviço saber se pode tirar a pessoa dela. */
enum class OwnScreen {
    /** Leitura para liberar ou 7 minutos: puxar outro app interromperia o que vai liberá-lo. */
    READING,
    /** Qualquer outra: tela inicial, editor, configurações, tela de bloqueio. */
    OTHER,
}

/** O que fazer com um alvo bloqueado que continua visível fora da janela ativa (PiP ou tela dividida). */
enum class OffscreenAction { PULL, PAUSE, NONE }

/** Uma janela de aplicativo na tela (ou em picture-in-picture), como o serviço de acessibilidade a vê. */
data class AppWindow(
    val packageName: String?,
    val isPip: Boolean,
    val isActive: Boolean,
    val isFocused: Boolean,
    val area: Long,
)

/** Última URL lida na barra do navegador que está na frente. */
data class LastUrl(val packageName: String, val url: String)

/** Decisões do serviço de bloqueio, sem nada de Android, para dar para testar. */
object Enforcement {
    /** Diferença entre o relógio e o tempo real a partir da qual é a pessoa (ou a rede) mudando a hora. */
    const val CLOCK_JUMP_MS = 5_000L

    /** Mais que isto entre dois tiques é pausa (tela apagada, sistema segurando o serviço): não conta. */
    const val MAX_TICK_MS = 2_000L

    /**
     * [blockedOutside]: algum alvo bloqueado está visível fora da janela ativa. [ownScreen]: a tela do
     * Renova na frente, ou null se a frente é de outro app. Na leitura e nos 7 minutos só pausa a mídia;
     * no resto, traz o app para a frente para o bloqueio normal agir.
     */
    fun offscreenAction(blockedOutside: Boolean, ownScreen: OwnScreen?): OffscreenAction = when {
        !blockedOutside -> OffscreenAction.NONE
        ownScreen == OwnScreen.READING -> OffscreenAction.PAUSE
        else -> OffscreenAction.PULL
    }

    /**
     * App da frente: a janela de aplicativo ativa ou focada que não está em picture-in-picture. Sem nenhuma
     * ativa (o aviso por cima tem o foco, por exemplo), a maior janela de aplicativo. Um PiP nunca é a frente.
     */
    fun frontApp(windows: List<AppWindow>): String? {
        val candidates = windows.filter { !it.isPip && it.packageName != null }
        return (candidates.firstOrNull { it.isActive || it.isFocused } ?: candidates.maxByOrNull { it.area })?.packageName
    }

    /**
     * Se o aviso por cima de [blockedPackage] continua aberto. O aviso do app da frente ([followsFront]) fecha
     * quando outro app vem para a frente; o de um app em PiP que o sistema não deixou puxar fica enquanto ele
     * estiver visível. Os dois fecham quando a tela de bloqueio de verdade abre.
     */
    fun keepOverlay(
        blockedPackage: String?,
        followsFront: Boolean,
        blockScreenShowing: Boolean,
        front: String?,
        visible: Collection<String>,
    ): Boolean = when {
        blockScreenShowing || blockedPackage == null -> false
        followsFront -> front == null || front == blockedPackage
        else -> blockedPackage in visible
    }

    /**
     * Alvos que o aviso por cima de [overlayPackage] não cobre. O app embaixo do aviso não está sendo usado
     * (não conta tempo) e já está parado por ele: puxá-lo e pausar a mídia de novo a cada volta só pausaria
     * outro player, como uma música de fundo.
     */
    fun uncovered(targets: List<ActiveTarget>, overlayPackage: String?): List<ActiveTarget> =
        targets.filterNot { it.packageName == overlayPackage }

    /**
     * Se a tela de bloqueio pedida em [requestedAt] chegou a abrir (ela grava [lastResumedAt] ao aparecer).
     * Só quando não abriu o sistema barrou o pedido e vale o aviso por cima; olhar só se ela está na frente
     * agora mostraria o aviso para quem já saiu dela pelo Voltar.
     */
    fun blockScreenOpened(lastResumedAt: Long, requestedAt: Long): Boolean = lastResumedAt >= requestedAt

    /**
     * Se o bloqueio de [rule] em [target] tira a pessoa só de uma parte do app (Voltar global e a tela de
     * bloqueio só fecha), em vez do app inteiro. Vale para o Shorts e o Reels, e para a regra de app que casou
     * pelo site oficial aberto num navegador: mandar para a tela inicial deixaria a aba no site, e o navegador
     * inteiro ficaria bloqueado.
     */
    fun blocksOnlySection(rule: Rule, target: ActiveTarget): Boolean =
        rule.type.isSection || target.packageName != rule.target

    /**
     * Salto do relógio entre duas amostras: quanto [System.currentTimeMillis] andou além do tempo real
     * ([elapsedRealtime][android.os.SystemClock.elapsedRealtime], que não muda com a hora do aparelho).
     * Negativo quando a hora foi atrasada; zero se a diferença é pequena (atraso normal do laço).
     */
    fun clockJump(prevWallMs: Long, prevElapsedMs: Long, wallMs: Long, elapsedMs: Long): Long {
        val jump = (wallMs - prevWallMs) - (elapsedMs - prevElapsedMs)
        return if (kotlin.math.abs(jump) > CLOCK_JUMP_MS) jump else 0L
    }

    /**
     * A barra do navegador some no vídeo em tela cheia e ao rolar a página, e aí [target] chega sem URL.
     * Enquanto o mesmo navegador continua na frente, vale a última URL lida; outro app na frente esquece ela.
     * Devolve o alvo a usar e o que guardar para a próxima leitura.
     */
    fun withLastUrl(target: ActiveTarget, isBrowser: Boolean, last: LastUrl?): Pair<ActiveTarget, LastUrl?> = when {
        !isBrowser -> target to null
        target.url != null -> target to LastUrl(target.packageName, target.url)
        last?.packageName == target.packageName -> target.copy(url = last.url) to last
        else -> target to null
    }

    /**
     * Tempo de uso de um tique: o tempo real desde o anterior ([prevTickAt] 0 = não houve), até [MAX_TICK_MS].
     * Contar 1 s fixo por volta do laço deixava de fora o trabalho de cada volta: um limite de 5 min só
     * bloqueava com uns 308 s reais.
     */
    fun tickMs(prevTickAt: Long, now: Long): Long =
        if (prevTickAt == 0L) 1_000L else (now - prevTickAt).coerceIn(0L, MAX_TICK_MS)

    /**
     * App da frente que conta tempo neste tique. Sem conseguir ler a janela ativa ([frontRead] falso), [active]
     * é o de antes e pode já ter saído: não conta agora. Ele não é zerado, para não piscar nas transições.
     */
    fun frontToCount(active: ActiveTarget?, frontRead: Boolean): ActiveTarget? = active.takeIf { frontRead }

    /**
     * Põe na frente a regra que conduz o bloqueio: a do app inteiro antes da de seção. Com o Shorts e o app
     * YouTube bloqueados, a seção vinha primeiro: a tela "YouTube Shorts" fechava no Voltar e logo vinha
     * outra, "YouTube". O caso do site no navegador fica com [blocksOnlySection].
     */
    fun leadingFirst(blocking: List<Pair<Rule, BlockReason>>): List<Pair<Rule, BlockReason>> =
        blocking.sortedBy { it.first.type.isSection }
}
