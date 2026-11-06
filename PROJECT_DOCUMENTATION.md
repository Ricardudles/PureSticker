# PureSticker — estado atual e referência técnica

A referência técnica ativa é [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md). Os documentos antigos de planos foram reduzidos a avisos para não manter especificações obsoletas concorrentes.

## Revisão atual (06/11/2026)

- O bloco experimental de stickers animados foi removido no commit `1229242`; permanece fora do escopo até novo planejamento.
- Corrigida a API do painel de texto para que o toggle de contorno seja opcional no editor de vídeo.
- Corrigida compressão WebP no editor para usar `WEBP_LOSSY` somente em API 30+ e o formato legado em API 26–29.
- Anotado opt-in da API instável do Media3 no ViewModel.
- Endurecida a restauração de backup contra nomes de arquivo maliciosos, colisões e mídias ausentes.
- `:app:assembleDebug`, `:app:testDebugUnitTest` e `:app:lintDebug` passaram. Lint ainda informa 114 warnings e há warnings AGP/Gradle de opções depreciadas. Sem teste manual de dispositivo/WhatsApp.
- `DatabaseModule` ainda usa `fallbackToDestructiveMigration()`, que pode apagar dados quando o schema mudar. Não foi alterado porque precisamos confirmar os schemas históricos para criar migrações seguras.

As mudanças desta revisão foram verificadas com build, testes unitários e lint; ainda dependem de validação manual dos fluxos em dispositivo.


## Emojis opcionais na criação

A documentação Android oficial e `StickerPackValidator` exigem pelo menos 1 emoji e permitem até 3 por figurinha. A tela agora apresenta a seleção como opcional e explica o fallback; `StickerEmojiPolicy` preenche `🙂` quando a lista está vazia antes da persistência. Build, testes unitários (incluindo fallback e preservação da seleção) e lint passaram em 06/11/2026.
