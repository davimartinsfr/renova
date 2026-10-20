# Textos da Play Store

Para copiar no Play Console. Limites da Play entre parênteses.

## Nome do app (30)

```
Renova: menos tela mais Bíblia
```

## Descrição curta (80)

```
Bloqueie Shorts, Reels e apps e libere tempo lendo a Bíblia. Sem anúncios.
```

## Descrição completa (4000)

```
"E não vos conformeis com este século, mas transformai-vos pela renovação da vossa mente." (Romanos 12:2)

O Renova ajuda você a usar menos o celular e a trocar esse tempo pela Palavra de Deus. Você escolhe o que limitar e, quando o tempo acaba, a forma de liberar mais um pouco é ler a Bíblia.

BLOQUEIE O QUE TOMA SEU TEMPO
• Bloqueie qualquer app, como TikTok, Instagram, Reddit ou jogos
• Bloqueie só o YouTube Shorts ou só o Reels do Instagram, deixando o resto do app liberado
• Bloquear um app também bloqueia o site oficial dele no navegador
• Defina um limite diário (ex.: 15 minutos por dia) ou horários de bloqueio (ex.: das 22h às 7h, ou só nos dias de semana)

LIBERE TEMPO LENDO A BÍBLIA
Quando um app estiver bloqueado, escolha quanto tempo quer e leia:
• 1 minuto: um versículo
• 15 minutos: um capítulo
• 1 hora: três capítulos
Os capítulos seguem uma leitura contínua, então, aos poucos, você lê a Bíblia inteira. Comece por Gênesis 1 ou vá para qualquer capítulo nas configurações. A leitura só conta depois de chegar ao fim do capítulo e ficar nele um tempo mínimo.

7 MINUTOS COM O SENHOR
Comece o dia com o Senhor antes das redes sociais. Os apps das suas regras ficam bloqueados até você fazer os 7 minutos guiados: invocar o nome do Senhor, orar, orar-ler a Palavra, confessar, consagrar-se, agradecer e fazer seus pedidos. Cada etapa tem seu tempo, avança sozinha e é anunciada por um toque suave.

DIFÍCIL DE BURLAR
Para afrouxar ou apagar uma regra, você precisa antes ler capítulos da Bíblia. Assim, a decisão que você tomou com a cabeça fria vale também na hora da tentação.

DO SEU JEITO
• Tema claro ou escuro, ou seguindo o celular
• Limite diário escolhido em horas e minutos

PRIVADO DE VERDADE
• Sem anúncios, sem compras e sem cadastro
• Funciona totalmente offline: o app nem acessa a internet
• Nenhum dado sai do seu celular

SOBRE A ACESSIBILIDADE
O Renova usa o serviço de acessibilidade do Android para saber qual app está aberto e reconhecer o Shorts, o Reels e os sites abertos no navegador. Ele usa isso só para aplicar as suas regras e contar o tempo de uso. Nada do que aparece na tela é guardado ou enviado. O app pede sua permissão antes de ativar o serviço, e você pode desativá-lo quando quiser.

Texto bíblico: Almeida Revista e Atualizada, 2ª ed. © Sociedade Bíblica do Brasil, 1993.
```

## Categoria e detalhes

- **Categoria:** Produtividade (alternativa: Estilo de vida)
- **Tags:** bem-estar digital, foco, Bíblia
- **E-mail de contato:** contate.davi.dev@gmail.com
- **Política de privacidade:** https://davimartinsfr.github.io/renova/privacidade.html

## Conteúdo do app (respostas no Console)

**Acesso ao app:** todas as funções estão disponíveis sem login nem restrições.

**Anúncios:** não contém anúncios.

**Classificação do conteúdo (questionário IARC):** categoria "Utilitários, produtividade, comunicação ou
outro"; responder "não" para violência, conteúdo sexual, linguagem imprópria, drogas, jogos de azar, compras
e interação entre usuários. Deve sair Livre / 3+.

**Público-alvo:** 13–15, 16–17 e 18+. Deixar menores de 13 de fora evita as exigências extras do programa
para famílias.

**Segurança dos dados:**
- O app coleta ou compartilha algum dos tipos de dados exigidos? **Não**
- (Com isso as outras perguntas não aparecem. Vale porque a versão da loja não tem permissão de internet:
  nada sai do aparelho.)

**Permissão de acessibilidade (declaração da API):**
- Uso: "Outro" / controle parental e bem-estar digital, *não* ferramenta de acessibilidade (o app não declara
  `isAccessibilityTool`).
- Descrição para o formulário:

```
O Renova é um app de bem-estar digital que ajuda o próprio usuário a limitar o tempo em apps e vídeos curtos. O serviço de acessibilidade é a função principal do app e é usado para: (1) identificar qual app está em primeiro plano, para contar o tempo de uso e aplicar os limites e horários das regras criadas pelo usuário; (2) ler o conteúdo da tela apenas para reconhecer o visualizador do YouTube Shorts e do Reels do Instagram e o endereço do site aberto no navegador; (3) voltar uma tela ou abrir a tela de bloqueio quando uma regra manda bloquear. Nenhum dado lido pelo serviço é armazenado, transmitido ou compartilhado: o app não tem permissão de internet. Antes de levar o usuário às configurações, o app mostra uma divulgação explicando esse uso e só continua com o consentimento explícito ("Concordo, ativar").
```

- **Vídeo (YouTube, pode ser não listado), roteiro:**
  1. Abrir o Renova com o serviço desligado e tocar em "Ativar" no aviso "Ative o Renova na acessibilidade".
  2. Mostrar o diálogo de divulgação e tocar em "Concordo, ativar".
  3. Ativar o Renova nas configurações de acessibilidade.
  4. Criar uma regra (ex.: YouTube Shorts com limite 0 h 00 min).
  5. Abrir o YouTube, entrar no Shorts e mostrar o bloqueio, com o resto do YouTube funcionando.
  6. Tocar em "Ler para liberar", escolher 1 minuto, ler o versículo e mostrar o Shorts liberado.

## Imagens

Prontas em `docs/play/`, geradas pelo teste `StoreImagesTest` (`./gradlew testGithubDebugUnitTest --tests '*StoreImagesTest*'`, saída em `app/build/screenshots/loja`).

- **Ícone 512×512:** `docs/play/icone-512.png`
- **Imagem de destaque 1024×500:** `docs/play/destaque-1024x500.png`
- **Capturas do celular 1080×1920, nesta ordem:** `docs/play/capturas/`
  1. Regras: "Limite Shorts, Reels e apps"
  2. Bloqueio: "Acabou o tempo? O app para."
  3. Leitura: "Libere tempo lendo a Bíblia"
  4. 7 minutos: "Comece o dia com o Senhor"
  5. Editor de regra: "Limite e horários do seu jeito"
  6. Configurações: "Claro ou escuro. Sem anúncios."
