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
}
