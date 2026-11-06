# Contexto do projeto PureSticker

Consulte [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md) como fonte única de arquitetura e fluxos.

Antes de alterar código, examine `git status` e preserve mudanças existentes. Não trate conteúdo staged como concluído. O bloco experimental de stickers animados foi retirado; não há implementação FFmpeg ativa na árvore atual. O projeto mantém o fluxo de vídeo anterior baseado no código versionado, mas suporte efetivo a sticker animado não está validado.

Stack atual inclui Kotlin, Compose Material3, Navigation, Hilt/KSP, Room/KSP, Coil, Media3, ML Kit, Android Image Cropper e Gson. ApplicationId `com.ricardudles.puresticker`; namespace Kotlin `com.example.wppsticker`.
