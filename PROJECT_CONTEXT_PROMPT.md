# Contexto do projeto PureSticker

Consulte [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md) como fonte única de arquitetura e fluxos.

Antes de alterar código, examine `git status` e preserve mudanças existentes. Não trate conteúdo staged como concluído. Há um fluxo inicial de GIF/vídeo para WebP animado em `ui/videoeditor`, codificado pelo libwebp 1.6.0 via JNI e validado antes de salvar. Ele compila e os testes unitários/lint passam, mas ainda não foi validado em aparelho nem WhatsApp. As limitações atuais de VFR/HDR, desempenho e cobertura estão em [`PLAN_ANIMATED_STICKERS.md`](PLAN_ANIMATED_STICKERS.md).

Stack atual: Kotlin 2.2.10, Compose Material3, Hilt 2.60.1/KSP 2.3.10, Room, Coil, Media3 1.3.1, `gifdecoder` 4.16.0 e libwebp 1.6.0 JNI. `gifdecoder` 5.0.9 foi incompatível com compileSdk 35 (exige SDK 37). ApplicationId `com.ricardudles.puresticker`; namespace Kotlin `com.example.wppsticker`.
