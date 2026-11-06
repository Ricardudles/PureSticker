# PureSticker

Aplicativo Android nativo para criação e gerenciamento de pacotes de figurinhas do WhatsApp. O projeto está em `D:\DESENVOLVIMENTO\WppSticker`.

## Documentação

- [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md) — referência técnica principal, baseada no código atual; arquitetura, navegação, persistência, mídia, provider e investigação de problemas.
- [`PROJECT_CONTEXT_PROMPT.md`](PROJECT_CONTEXT_PROMPT.md) — resumo curto para contexto de assistência.
- `PROJECT_DOCUMENTATION.md`, `BUILD_FIX_PLAN.md`, `IMPROVEMENTS.md` e `PLAN_ANIMATED_STICKERS.md` — registros e planos históricos. Podem conter decisões já superadas; confirmar sempre no código e no guia atual.

## Projeto

- Android nativo, Kotlin, Jetpack Compose e módulo Gradle `:app`.
- `applicationId`: `com.ricardudles.puresticker`; `namespace`: `com.example.wppsticker`.
- Dados locais em Room e mídia privada em `filesDir`.
- Integração com WhatsApp via `StickerContentProvider`.
- Dependências, versões e alvos estão nos arquivos Gradle; consulte o guia antes de presumir comportamento de conversão ou compatibilidade.

## Desenvolvimento (Windows)

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Rode as verificações relevantes à mudança e registre seus resultados. O estado atual do Git pode conter alterações locais: inspecione `git status` antes de editar ou preparar commits.
