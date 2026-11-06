# Guia de desenvolvimento do PureSticker

> Guia baseado no código encontrado em `D:\DESENVOLVIMENTO\WppSticker` em 06/11/2026. Quando este arquivo divergir do código, confirme a implementação atual antes de alterar; este projeto contém documentos históricos desatualizados.

## 1. Resumo

PureSticker é um aplicativo Android nativo em Kotlin para criar e administrar pacotes de figurinhas do WhatsApp. O app tem um único módulo Gradle, `:app`, usa Jetpack Compose e mantém pacotes, stickers e arquivos de mídia localmente. O applicationId é `com.ricardudles.puresticker`; o namespace e o pacote Kotlin são `com.example.wppsticker`.

Alvos observados: `minSdk 26`, `compileSdk 35`, `targetSdk 35`, Java/Kotlin JVM 17, `versionCode 4`, `versionName 1.3`. O catálogo de versões está em `gradle/libs.versions.toml`; plugins e dependências/repositórios são configurados em `build.gradle.kts`, `settings.gradle.kts` e `app/build.gradle.kts`.

## 2. Arquitetura e dependências

O código está organizado por responsabilidade, sem módulos Gradle separados:

- `ui/`: telas Compose e ViewModels por fluxo (`home`, `editor`, `videoeditor`, `stickerpack`, `settings`); `components`, `theme` e `util` compartilhados.
- `domain/`: interfaces de repositório e casos de uso; regras de aplicação são chamadas pelos ViewModels.
- `data/`: Room (`local`), DTOs de backup (`backup`) e implementações (`repository`).
- `engine/`: não há engine de stickers animados ativa após o descarte do experimento pendente.
- `provider/`: ContentProvider consultado pelo WhatsApp.
- `di/`: módulos Hilt para banco, repositórios, engines, Coil e entry point do provider.
- `nav/`: enumeração de destinos e grafo Compose.
- `util/`: processamento/limpeza de imagem, remoção de fundo e estado genérico de UI.

Stack efetivamente declarada: Kotlin 2.2.10, Compose Material3, Navigation Compose, Hilt 2.60.1 com KSP 2.3.10, Room, Coil, Android Image Cropper, Gson, Media3 1.3.1, ML Kit Selfie Segmentation e `com.github.bumptech.glide:gifdecoder:4.16.0`. A geração animada usa libwebp 1.6.0 vendorizado e JNI sob `app/src/main/cpp`. FFmpegKit não é usado. Repositórios Gradle: Google, Maven Central, JitPack e JCenter (este último é legado e merece atenção em problemas de resolução).

## 3. Persistência e arquivos

Room está definido em `data/local` (`AppDatabase`, versão 7; `StickerDao`, entidades e `Converters`). `StickerPackage` tem chave numérica local e `identifier` UUID estável para integração/exportação; armazena nome, autor, arquivo da imagem de bandeja, versão de dados de imagem, metadados de publicação e `animated`. `Sticker` referencia o pacote por `packageId` com cascade delete e armazena nome do arquivo, SHA-256, emojis, largura, altura e tamanho em KB. `StickerPackageWithStickers` representa a relação 1:N.

`DatabaseModule` usa atualmente `fallbackToDestructiveMigration()`: uma mudança de versão sem migração pode apagar o banco local. Antes de alterar entidades/versão, recuperar schemas de versões distribuídas e implementar/testar migrações; não confiar no fallback para preservar dados.

### Acompanhamentos futuros

- Substituir a migração destrutiva por migrações Room compatíveis, depois de confirmar os schemas já distribuídos e cobrir upgrades com testes de migração.
- Validar no dispositivo Android a criação/edição/salvamento de stickers estáticos, integração com WhatsApp, backup e restauração. O build e lint não comprovam esses fluxos de ponta a ponta.
- Concluir a validação em aparelho da exportação animada; ver limitações e cobertura pendente em [`PLAN_ANIMATED_STICKERS.md`](../PLAN_ANIMATED_STICKERS.md).

Imagens de bandeja e stickers são arquivos privados em `context.filesDir`; registros Room guardam os nomes dos arquivos. `CleanOrphanFilesUseCase`/utilitários comparam os arquivos existentes com os nomes referenciados. Ao adicionar/remover/restaurar dados, considere consistência entre arquivo e banco e remoção de temporários no caminho de erro.

`StickerRepository` é a porta para dados de sticker e verificação da whitelist do WhatsApp. `StickerRepositoryImpl` delega ao DAO e consulta o provider de whitelist de WhatsApp e WhatsApp Business. Casos de uso em `domain/usecase` encapsulam operações de pacote, stickers, whitelist e limpeza.

## 4. Navegação e fluxos

`MainActivity` aplica tema e configura `NavGraph`; a rota inicial é `Home`. `Screen` contém: `Home`, `StickerPack/{packageId}`, `StickerTypeSelection`, `Editor/{stickerUri}?packageId=...`, `VideoEditor/{stickerUri}?packageId=...`, `SaveSticker/{stickerUri}?packageId=...&isAnimated=...`, `PackageSelection` com argumentos opcionais, `Settings` e `RestorePreview/{backupUri}`. Os argumentos das rotas são strings/ints/bools do Navigation Compose; preserve encoding correto de URI/texto ao construir rotas.

Fluxos principais observados:

1. Home lista pacotes e permite criar, apagar, abrir ou enviar pacote ao WhatsApp.
2. Seleção de tipo inicia seleção de mídia; imagem segue para editor estático e vídeo/GIF segue para editor animado.
3. Editores recebem URI e, opcionalmente, `packageId` para adicionar a pacote existente. Após editar, navegam à etapa de salvar.
4. Salvar sticker permite escolher/criar pacote e persistir a mídia. Emojis são opcionais na tela; sem escolha, `StickerEmojiPolicy` aplica 🙂 ao salvar, pois o validador Android oficial do WhatsApp exige pelo menos 1 e aceita até 3 emojis por sticker. O limite de três continua aplicado.
5. Detalhes do pacote editam metadados, removem stickers e enviam o pacote ao WhatsApp.
6. Settings exporta backup e limpa órfãos; fluxo de restauração inspeciona ZIP, mostra prévia e restaura somente pacotes selecionados.

Para entender uma ação de UI, siga: Composable → ViewModel → UseCase/Repository → DAO ou engine/arquivos → estado/evento de volta à tela.

## 5. Edição e mídia

### Imagem estática

`EditorScreen` e `EditorViewModel` mantêm o estado de edição (`EditorState`, `ImageState`, `TextData`). A implementação oferece crop, texto com formatação, posicionamento/transformação, snap/grid, undo/redo e remoção de fundo por `BackgroundRemover` (ML Kit). `ImageHelper` oferece redimensionamento. Confirme o formato e dimensões da saída em `SaveStickerViewModel` e nos helpers antes de mexer em compatibilidade/tamanho.

### Vídeo/GIF e WebP animado

`VideoEditorScreen` e `VideoEditorViewModel` mantêm preview em loop, seleção de trecho de até 10 segundos, enquadramento, zoom, rotação, texto, grade, undo/redo e seleção do perfil de saída. GIF é lido quadro a quadro por `AnimatedStickerExporter.GifSource` usando o decoder standalone do Glide; preview GIF acompanha o trecho selecionado. Para vídeo, o preview continua em ExoPlayer/Media3.

`AnimatedStickerExporter` renderiza quadros 512×512, aplica enquadramento e texto e tenta combinações de FPS/qualidade. O critério de tamanho usa cada WebP realmente codificado, com limite de 512.000 bytes. A codificação de animação e muxagem usam `AnimatedWebpNative`/`animated_webp_jni.cpp` e libwebp 1.6.0. `MediaMetadataRetriever` ainda é usado para buscar quadros de vídeo; sua precisão para VFR e HDR precisa ser substituída/validada antes do lançamento. Transformer MP4 não é usado na saída. Consulte [`PLAN_ANIMATED_STICKERS.md`](../PLAN_ANIMATED_STICKERS.md) para versões, validações concluídas e bloqueios de qualidade restantes.

Ao salvar em pacote animado, `SaveStickerViewModel` valida novamente RIFF/WebP, 512×512, tipo animado, contagem/duração dos quadros e limite de bytes via libwebp demuxer. Uma imagem estática é muxada em dois quadros iguais (`WebPMux`), sem depender do `WebPAnimEncoder` que pode mesclá-los. Arquivo final e atualização Room são ordenados para evitar pacote com registro parcial. A transação Room não altera o schema atual.

## 6. WhatsApp ContentProvider

`StickerContentProvider` declara autoridade `${applicationId}.provider`, exportada e protegida por `com.whatsapp.sticker.READ`. O manifesto declara metadata `com.whatsapp.sticker.provider=true` e queries específicas para WhatsApp e WhatsApp Business, sem permissão ampla de inventário de apps.

O provider atende `metadata`, `metadata/{identifier}`, `stickers/{identifier}` e `stickers_asset/{identifier}/{filename}`; consultas montam cursores com o schema esperado pelo WhatsApp e buscam dados via entry point Hilt. `openFile` serve arquivos de `filesDir` em somente leitura e rejeita nomes com `..` ou `/`. Ao alterar a integração, mantenha consistentes autoridade, manifest, rotas, colunas, nomes dos arquivos e identificador UUID. O envio do intent e verificação de whitelist também aparecem nos repositórios/ViewModels.

## 7. Backup e restauração

`BackupRepositoryImpl` grava um ZIP com `backup_metadata.json` (DTOs Gson: versão do app, data, pacotes e stickers) e mídia sob `images/`. A restauração inspeciona os metadados, filtra pelos identifiers selecionados, extrai apenas arquivos referenciados e recria os dados locais. Ao alterar o formato, preserve compatibilidade com backups anteriores, tratamento de nomes/duplicidades e coerência do hash e dos nomes físicos.

Os nomes de mídia do backup precisam ser nomes simples de arquivo. A restauração rejeita separadores de caminho, colisões com arquivos existentes e backups que não contêm todos os arquivos requeridos pelos pacotes selecionados. Isso protege `filesDir` contra caminhos maliciosos no ZIP e evita registrar mídia ausente.

Regras consultadas para emojis: [README Android do repositório oficial WhatsApp/stickers](https://github.com/WhatsApp/stickers/blob/main/Android/README.md) e [validador Android](https://github.com/WhatsApp/stickers/blob/main/Android/app/src/main/java/com/example/samplestickerapp/StickerPackValidator.java). A tela explica o fallback ao usuário em português e inglês.

## 8. Injeção de dependência e ciclo de vida

`WppStickerApplication` é a aplicação Hilt. `AppModule`, `DatabaseModule`, `RepositoryModule`, `CoilModule` e `StickerProviderEntryPoint` conectam dependências. O provider é criado pelo Android fora da árvore Compose e acessa dependências pelo entry point; não o converta para injeção de construtor convencional sem rever esse ciclo de vida.

ViewModels anotados com `@HiltViewModel` recebem dependências por construtor e expõem estado reativo. Mantenha I/O e processamento pesado fora da thread principal, use `viewModelScope`/coroutines e represente carregamento/erro explicitamente.

## 9. Como investigar mudanças

- Navegação/argumento incorreto: `nav/Screen.kt`, `nav/NavGraph.kt`, chamada de navegação e leitura de `SavedStateHandle` no ViewModel.
- Dados que não atualizam: entidade/DAO → interface de repositório → implementação → módulo Hilt → UseCase → coleta de Flow na tela.
- Arquivo faltando/corrompido: gravação no ViewModel/UseCase, `filesDir`, registro Room, limpeza de órfãos e restauração.
- WhatsApp não reconhece pacote: manifesto, `StickerContentProvider`, autoridade, schema dos cursores, nomes dos assets, whitelist e intent.
- Exportação animada: `VideoEditorViewModel` → `AnimatedStickerExporter` → `AnimatedWebpNative` (JNI/libwebp) → validação e persistência em `SaveStickerViewModel`; consulte também o plano atualizado para limitações de VFR/HDR e validação WhatsApp ainda pendente.
- Migração Room: versão atual 7, entidades, `Converters` e configuração de migrações em `DatabaseModule`; preserve dados de instalações existentes.
- Dependências falham: catálogo de versões, repositórios em `settings.gradle.kts` e JitPack/JCenter.

## 11. Comandos úteis

Na raiz do projeto (Windows):

```powershell
.\gradlew.bat tasks
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Execute apenas os comandos necessários à tarefa. Os comandos acima são referências; este guia não implica que o build ou testes tenham sido executados nesta atualização.

## Estado da revisão (06/11/2026)

Em 06/11/2026, após integrar a geração WebP animada, passaram `assembleDebug`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin`, `lintDebug` e `externalNativeBuildDebug` para três ABIs. `connectedDebugAndroidTest` passou no AVD API 36.1, incluindo testes de muxagem de quadros duplicados e codificação de dois quadros diferentes. `llvm-readelf` confirmou segmentos LOAD com alinhamento 16 KiB nas bibliotecas de 64 bits; `zipalign -c -P 16 4 app-debug.apk` passou. Ainda não foi validado em Android API 26, aparelho físico, WhatsApp, HDR/VFR ou sistema efetivamente configurado com páginas de 16 KiB. Há avisos existentes de APIs/opções depreciadas no projeto.

