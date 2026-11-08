# Contexto do projeto PureSticker

Consulte [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md) como fonte única de arquitetura e fluxos.

Antes de alterar código, examine `git status` e preserve mudanças existentes. Não trate conteúdo staged como concluído. O fluxo de GIF/vídeo para WebP animado está ativo em `ui/videoeditor`, codificado pelo libwebp 1.6.0 via JNI e validado antes de salvar; o usuário já confirmou no celular uma exportação de 1 segundo até a tela de salvar. Pacotes animados podem ser criados pela Home ou durante a seleção para salvar; em pacote animado, vídeo/GIF usam o editor animado e imagem estática carrega `isAnimated=true` até a conversão em WebP de dois quadros. `testDebugUnitTest`, `assembleDebug` e `lintDebug` passaram em 08/11/2026. Falta validar criação/salvamento no aparelho e envio real ao WhatsApp; são exigidas três figurinhas para enviar um pacote. Limitações atuais de VFR/HDR, desempenho e cobertura estão em [`PLAN_ANIMATED_STICKERS.md`](PLAN_ANIMATED_STICKERS.md) e [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md).

Stack atual: Kotlin 2.2.10, Compose Material3, Hilt 2.60.1/KSP 2.3.10, Room, Coil, Media3 1.3.1, `gifdecoder` 4.16.0 e libwebp 1.6.0 JNI. `gifdecoder` 5.0.9 foi incompatível com compileSdk 35 (exige SDK 37). ApplicationId `com.ricardudles.puresticker`; namespace Kotlin `com.example.wppsticker`.
