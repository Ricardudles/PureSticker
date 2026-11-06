# PureSticker — estado atual e referência técnica

A referência técnica ativa é [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md). Os documentos antigos de planos foram reduzidos a avisos para não manter especificações obsoletas concorrentes.

## Git e bloco de figurinhas animadas

A revisão iniciou no commit `193fd9c` (`master`, alinhado com `origin/master`). O trabalho pendente para ativar stickers animados foi removido da árvore: seleção de vídeo, engine FFmpeg, dependência/fork, binding Hilt e arquivos de diagnóstico. O código restante volta ao comportamento versionado nesse commit para o fluxo de vídeo. As alterações locais mantidas são documentação atualizada e uma melhoria de contorno de texto no editor estático; elas ainda não foram commitadas.

A compilação não foi validada: a tentativa de `:app:assembleDebug` não iniciou porque Java/JAVA_HOME não está disponível neste ambiente. Não descartar ou publicar alegando validação de build até executar em ambiente Android configurado.
