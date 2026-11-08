# PureSticker — estado atual e referência técnica

A referência técnica ativa é [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md). Os documentos antigos de planos foram reduzidos a avisos para não manter especificações obsoletas concorrentes.

## Revisão atual (08/11/2026)

- O fluxo de GIF/vídeo para WebP animado está ativo. O usuário confirmou no celular que um trecho de 1 segundo foi processado e chegou à tela de salvar figurinha.
- Corrigida a criação de pacotes animados: o seletor de tipo funciona na Home, e no fluxo de salvar a criação respeita o tipo animado obrigatório em vez de enviar `false`.
- O botão de adicionar figurinha em pacotes animados agora aceita vídeo, GIF e imagem. Vídeo/GIF seguem pelo editor de animação; imagem estática segue pelo editor de imagem com `isAnimated=true` até a etapa de salvar, que a muxa como WebP animado compatível.
- Pacotes continuam separados por tipo na seleção e na persistência; as strings novas e revisadas estão em `values` e `values-pt`.
- `:app:testDebugUnitTest`, `:app:assembleDebug` e `:app:lintDebug` passaram em 08/11/2026. O lint verifica as traduções; permanecem avisos de depreciação do AGP/Compose.
- Falta validar no aparelho o fluxo completo de criar pacote animado, salvar figurinha, adicionar outras duas e enviar ao WhatsApp. O envio do pacote exige pelo menos três figurinhas.
- `DatabaseModule` ainda usa `fallbackToDestructiveMigration()`, que pode apagar dados quando o schema mudar. Não foi alterado porque precisamos confirmar os schemas históricos para criar migrações seguras.

As alterações descritas foram verificadas com build, testes unitários e lint. A exportação de 1 segundo já foi executada pelo usuário no aparelho; criação de pacote animado e integração final com WhatsApp ainda aguardam validação manual.


## Emojis opcionais na criação

A documentação Android oficial e `StickerPackValidator` exigem pelo menos 1 emoji e permitem até 3 por figurinha. A tela agora apresenta a seleção como opcional e explica o fallback; `StickerEmojiPolicy` preenche `🙂` quando a lista está vazia antes da persistência. Build, testes unitários (incluindo fallback e preservação da seleção) e lint passaram em 06/11/2026.
