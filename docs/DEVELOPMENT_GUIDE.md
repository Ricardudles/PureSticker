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

Stack efetivamente declarada: Kotlin, Compose Material3, Navigation Compose, Hilt com KSP, Room com KSP, Coil, Android Image Cropper, Gson, Media3 e ML Kit Selfie Segmentation. FFmpegKit e os arquivos experimentais de engine foram removidos da árvore de trabalho. Repositórios Gradle: Google, Maven Central, JitPack e JCenter (este último é legado e merece atenção em problemas de resolução).

## 3. Persistência e arquivos

Room está definido em `data/local` (`AppDatabase`, versão 7; `StickerDao`, entidades e `Converters`). `StickerPackage` tem chave numérica local e `identifier` UUID estável para integração/exportação; armazena nome, autor, arquivo da imagem de bandeja, versão de dados de imagem, metadados de publicação e `animated`. `Sticker` referencia o pacote por `packageId` com cascade delete e armazena nome do arquivo, SHA-256, emojis, largura, altura e tamanho em KB. `StickerPackageWithStickers` representa a relação 1:N.

Imagens de bandeja e stickers são arquivos privados em `context.filesDir`; registros Room guardam os nomes dos arquivos. `CleanOrphanFilesUseCase`/utilitários comparam os arquivos existentes com os nomes referenciados. Ao adicionar/remover/restaurar dados, considere consistência entre arquivo e banco e remoção de temporários no caminho de erro.

`StickerRepository` é a porta para dados de sticker e verificação da whitelist do WhatsApp. `StickerRepositoryImpl` delega ao DAO e consulta o provider de whitelist de WhatsApp e WhatsApp Business. Casos de uso em `domain/usecase` encapsulam operações de pacote, stickers, whitelist e limpeza.

## 4. Navegação e fluxos

`MainActivity` aplica tema e configura `NavGraph`; a rota inicial é `Home`. `Screen` contém: `Home`, `StickerPack/{packageId}`, `StickerTypeSelection`, `Editor/{stickerUri}?packageId=...`, `VideoEditor/{stickerUri}?packageId=...`, `SaveSticker/{stickerUri}?packageId=...&isAnimated=...`, `PackageSelection` com argumentos opcionais, `Settings` e `RestorePreview/{backupUri}`. Os argumentos das rotas são strings/ints/bools do Navigation Compose; preserve encoding correto de URI/texto ao construir rotas.

Fluxos principais observados:

1. Home lista pacotes e permite criar, apagar, abrir ou enviar pacote ao WhatsApp.
2. Seleção de tipo inicia seleção de mídia; imagem segue para editor estático e vídeo para editor de vídeo.
3. Editores recebem URI e, opcionalmente, `packageId` para adicionar a pacote existente. Após editar, navegam à etapa de salvar.
4. Salvar sticker permite escolher/criar pacote, emojis e persistir a mídia. `SaveStickerViewModel` trata duplicidade pelo hash e eventos de sucesso/erro.
5. Detalhes do pacote editam metadados, removem stickers e enviam o pacote ao WhatsApp.
6. Settings exporta backup e limpa órfãos; fluxo de restauração inspeciona ZIP, mostra prévia e restaura somente pacotes selecionados.

Para entender uma ação de UI, siga: Composable → ViewModel → UseCase/Repository → DAO ou engine/arquivos → estado/evento de volta à tela.

## 5. Edição e mídia

### Imagem estática

`EditorScreen` e `EditorViewModel` mantêm o estado de edição (`EditorState`, `ImageState`, `TextData`). A implementação oferece crop, texto com formatação, posicionamento/transformação, snap/grid, undo/redo e remoção de fundo por `BackgroundRemover` (ML Kit). `ImageHelper` oferece redimensionamento. Confirme o formato e dimensões da saída em `SaveStickerViewModel` e nos helpers antes de mexer em compatibilidade/tamanho.

### Vídeo/animado

`VideoEditorScreen` e `VideoEditorViewModel` mantêm trim, crop, reprodução, texto, grade, histórico e estado de gravação. O código atual do editor de vídeo usa o fluxo que já existia no commit base: Media3 Transformer exporta um arquivo de vídeo MP4 para a etapa de salvar. Isso não equivale a um sticker animado WebP aceito pelo WhatsApp. A tentativa experimental de FFmpeg foi removida sem validação; planeje essa capacidade como um bloco futuro independente.

Media3 permanece nas dependências e no fluxo de edição/exportação de vídeo existente.

## 6. WhatsApp ContentProvider

`StickerContentProvider` declara autoridade `${applicationId}.provider`, exportada e protegida por `com.whatsapp.sticker.READ`. O manifesto declara metadata `com.whatsapp.sticker.provider=true` e queries específicas para WhatsApp e WhatsApp Business, sem permissão ampla de inventário de apps.

O provider atende `metadata`, `metadata/{identifier}`, `stickers/{identifier}` e `stickers_asset/{identifier}/{filename}`; consultas montam cursores com o schema esperado pelo WhatsApp e buscam dados via entry point Hilt. `openFile` serve arquivos de `filesDir` em somente leitura e rejeita nomes com `..` ou `/`. Ao alterar a integração, mantenha consistentes autoridade, manifest, rotas, colunas, nomes dos arquivos e identificador UUID. O envio do intent e verificação de whitelist também aparecem nos repositórios/ViewModels.

## 7. Backup e restauração

`BackupRepositoryImpl` grava um ZIP com `backup_metadata.json` (DTOs Gson: versão do app, data, pacotes e stickers) e mídia sob `images/`. A restauração inspeciona os metadados, filtra pelos identifiers selecionados, extrai apenas arquivos referenciados e recria os dados locais. Ao alterar o formato, preserve compatibilidade com backups anteriores, tratamento de nomes/duplicidades e coerência do hash e dos nomes físicos.

## 8. Injeção de dependência e ciclo de vida

`WppStickerApplication` é a aplicação Hilt. `AppModule`, `DatabaseModule`, `RepositoryModule`, `CoilModule` e `StickerProviderEntryPoint` conectam dependências. O provider é criado pelo Android fora da árvore Compose e acessa dependências pelo entry point; não o converta para injeção de construtor convencional sem rever esse ciclo de vida.

ViewModels anotados com `@HiltViewModel` recebem dependências por construtor e expõem estado reativo. Mantenha I/O e processamento pesado fora da thread principal, use `viewModelScope`/coroutines e represente carregamento/erro explicitamente.

## 9. Como investigar mudanças

- Navegação/argumento incorreto: `nav/Screen.kt`, `nav/NavGraph.kt`, chamada de navegação e leitura de `SavedStateHandle` no ViewModel.
- Dados que não atualizam: entidade/DAO → interface de repositório → implementação → módulo Hilt → UseCase → coleta de Flow na tela.
- Arquivo faltando/corrompido: gravação no ViewModel/UseCase, `filesDir`, registro Room, limpeza de órfãos e restauração.
- WhatsApp não reconhece pacote: manifesto, `StickerContentProvider`, autoridade, schema dos cursores, nomes dos assets, whitelist e intent.
- Exportação de vídeo: `VideoEditorViewModel`, Transformer do Media3, URI de origem e arquivo gerado. Isso não confirma geração de sticker WebP animado.
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
