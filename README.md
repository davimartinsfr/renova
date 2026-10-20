# Renova

> "E não vos conformeis com este século, mas transformai-vos pela renovação da vossa mente" (Romanos 12:2)

Bloqueador de apps, YouTube Shorts e Instagram Reels para Android. Cada regra tem **limite diário** e/ou
**horários de bloqueio**. Para liberar os apps bloqueados, escolha quanto tempo e leia na Bíblia
(Almeida Revista e Atualizada, offline):

| Liberação | Leitura |
|---|---|
| 1 minuto | 1 versículo aleatório |
| 15 minutos | 1 capítulo |
| 1 hora | 3 capítulos |

Os capítulos seguem um plano contínuo a partir de Gênesis 1. Cada um só conta depois de rolar até o fim e
permanecer o tempo mínimo (~200 palavras/min, mínimo 30 s; 10 s para o versículo). Afrouxar ou apagar uma
regra exige uma liberação por capítulos — o versículo avulso não vale para isso.

## 7 minutos com o Senhor

Todos os apps das regras ativas ficam bloqueados todo dia até você fazer os **7 minutos com o Senhor**
(o dia recomeça às 4h), mesmo fora do horário de bloqueio ou dentro do limite. As liberações por leitura não
valem para isso. São sete etapas guiadas, cada
uma com seu tempo, que avançam sozinhas com um aviso sonoro (o tempo pausa fora do app):

| Etapa | Tempo |
|---|---|
| Invocar | 30 s |
| Orar | 1 min |
| Orar-ler | 2 min 30 s |
| Confissão | 1 min |
| Consagração | 30 s |
| Ação de graças | 30 s |
| Petição | 1 min |

Os trechos para orar-ler vêm de `app/src/main/assets/devotion/orar-ler.txt` (um por linha, um por dia, em
rodízio).

## Instalar no celular
1. No celular, baixe o `.apk` da [última release](https://github.com/davimartinsfr/renova/releases/latest) e abra (permita "instalar apps desconhecidos"). Depois disso o próprio app avisa quando houver versão nova.
2. **Android 13+**: Configurações › Apps › Renova › ⋮ › **Permitir configurações restritas**
   (sem isso o Android não deixa ativar a acessibilidade em apps instalados por APK).
3. Abra o app e siga os cartões: ativar acessibilidade, liberar bateria, permitir notificações.
4. Crie as regras (ex.: Shorts com limite 0 = sempre bloqueado; Reddit 15 min/dia; reddit.com 22:00–07:00).

## Compilar
```
./gradlew test             # testes de regras, horários e leitura
./gradlew assembleRelease  # APK assinado com keystore/brainrot.jks (keystore.properties)
```
Guarde `keystore/` e `keystore.properties`: sem eles, futuras versões não instalam por cima (teria que desinstalar e perder progresso).

## Limitações
- Ainda é possível desativar a acessibilidade ou desinstalar o app pelas configurações do Android — o app avisa na tela inicial quando o serviço está desligado.
- A detecção do Shorts e da barra de endereço usa IDs internos dos apps (`service/TargetDetector.kt`); se uma atualização do YouTube/navegador quebrar a detecção, ajuste os IDs lá.

## Créditos

- Fontes Fraunces e Inter (SIL Open Font License).
- Ampulheta da tela de bloqueio: "Hourglass" de Rahmad romadoni, [Noun Project](https://thenounproject.com/icon/hourglass-8314367/) (CC BY 3.0).
