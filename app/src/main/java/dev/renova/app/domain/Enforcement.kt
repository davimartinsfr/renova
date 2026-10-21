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

/** Decisões do serviço de bloqueio, sem nada de Android, para dar para testar. */
object Enforcement {

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

    /** Alvos que contam tempo: com o aviso por cima de [overlayPackage], o app embaixo dele não está sendo usado. */
    fun counted(targets: List<ActiveTarget>, overlayPackage: String?): List<ActiveTarget> =
        targets.filterNot { it.packageName == overlayPackage }

    /**
     * Se a tela de bloqueio pedida em [requestedAt] chegou a abrir (ela grava [lastResumedAt] ao aparecer).
     * Só quando não abriu o sistema barrou o pedido e vale o aviso por cima; olhar só se ela está na frente
     * agora mostraria o aviso para quem já saiu dela pelo Voltar.
     */
    fun blockScreenOpened(lastResumedAt: Long, requestedAt: Long): Boolean = lastResumedAt >= requestedAt
}
