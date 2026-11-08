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

`MainActivity` aplica tema e configura `NavGraph`; a rota inicial é `Home`. `Screen` contém: `Home`, `StickerPack/{packageId}`, `StickerTypeSelection`, `Editor/{stickerUri}?packageId=...&isAnimated=...`, `VideoEditor/{stickerUri}?packageId=...`, `SaveSticker/{stickerUri}?packageId=...&isAnimated=...`, `PackageSelection` com argumentos opcionais, `Settings` e `RestorePreview/{backupUri}`. Os argumentos das rotas são strings/ints/bools do Navigation Compose; preserve encoding correto de URI/texto ao construir rotas.

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

`EditorScreen` e `EditorViewModel` mantêm o estado de edição (`EditorState`, `ImageState`, `TextData`). A implementação oferece crop, texto com formatação, posicionamento/transformação, snap/grid, undo/redo e remoção de fundo por `BackgroundRemover` (ML Kit). O `StickerCanvasBoundary` fica sobre todas as camadas e desenha um contorno duplo sutil, mantendo visível o limite quadrado de 512×512 mesmo quando a mídia ocupa o canvas inteiro; o quadriculado continua indicando transparência. `ImageHelper` oferece redimensionamento. Confirme o formato e dimensões da saída em `SaveStickerViewModel` e nos helpers antes de mexer em compatibilidade/tamanho.

### Vídeo/GIF e WebP animado

`CreatePackageDialog` aceita o tipo animado na Home e respeita o tipo obrigatório quando é aberto a partir da seleção de pacote para uma figurinha. A escolha é propagada por `HomeViewModel` ao campo `StickerPackage.animated`; a seleção filtra pacotes pelo tipo e impede mistura. Dentro de um pacote animado, o botão de adicionar abre `OpenDocument` para vídeo e imagem: vídeo/GIF seguem ao `VideoEditor`, enquanto imagem estática segue ao `Editor` com a saída marcada como animada. `EditorViewModel` carrega esse argumento até `SaveSticker`, onde `SaveStickerViewModel` muxa a imagem em WebP animado de dois quadros. O botão de adicionar ao WhatsApp só conclui quando o pacote tem ao menos três figurinhas; antes disso o app mostra a regra como mensagem.

`VideoEditorScreen` e `VideoEditorViewModel` mantêm preview em loop, seleção de trecho de até 10 segundos, enquadramento, zoom, rotação, texto, grade, undo/redo e seleção do perfil de saída. A timeline aceita ajuste pelos dois puxadores e oferece cortes rápidos de 1, 3, 5 e 10 segundos para validar a exportação com menos quadros. Durante o arraste, a prévia pausa sem executar seeks repetidos; ao soltar, busca o início do trecho e retoma se estava tocando. O padrão de 10 segundos é aplicado uma única vez quando o player inicializa; seeks posteriores que retornam a `STATE_READY` não alteram o intervalo escolhido nem forçam a reprodução. O estado anterior do corte é salvo uma única vez no undo. GIF é lido quadro a quadro por `AnimatedStickerExporter.GifSource` usando o decoder standalone do Glide; preview GIF acompanha o trecho selecionado. Para vídeo, o preview continua em ExoPlayer/Media3. O limite de canvas usa o mesmo `StickerCanvasBoundary` do editor de imagem e é desenhado por cima da mídia e da grade para não desaparecer em imagens/vídeos que preenchem o quadro.

`AnimatedStickerExporter` renderiza quadros 512×512, aplica enquadramento e texto, e mede o tamanho real da saída. Para vídeo, abre uma faixa com `MediaExtractor` e decodifica sequencialmente com `MediaCodec` em buffers `COLOR_FormatYUV420Flexible`; o decoder escolhido e o indicador de aceleração por hardware são registrados. YUV é convertido na CPU considerando strides, crop, matriz e range. O bitmap intermediário fica entre 512 e 1024 px conforme o zoom, limitado ao necessário para o canvas final; não há uma transcodificação/compressão temporária do vídeo. A saída YUV evita os buffers PRIVATE incompatíveis com `ImageReader` observados no Samsung SM-S908E. A exportação roda em `Dispatchers.IO`. Um planejador escolhe FPS/qualidade pela duração e perfil, tenta primeiro a combinação preferida e faz no máximo dois recuos; cada tentativa ainda redecodifica a fonte. A codificação WebP é CPU, com threading do libwebp; perfis usam método 2/3/4 e `minimize_size` permanece desativado porque a API documenta que essa otimização é lenta. O painel de exportação mostra estágio, percentual/quadros, perfil, passe, FPS, qualidade, tempo, progresso relativo ao trecho e tamanho medido do WebP já montado; “dentro do trecho” não representa a posição na timeline original. O tamanho final não pode ser conhecido exatamente antes da codificação e montagem completa, então cada candidato é medido e validado; só há outro passe se o anterior passar de 512.000 bytes. O log `AnimatedStickerExporter` registra FPS, qualidade, bytes e tempo por codificação para comparação no aparelho. `SurfaceTexture`/OpenGL ES ainda é uma alternativa futura para reduzir conversão YUV na CPU; veja [`PLAN_ANIMATED_STICKERS.md`](../PLAN_ANIMATED_STICKERS.md). GIF é lido por `gifdecoder`, também com amostragem ajustada ao zoom. A saída usa `AnimatedWebpNative`/`animated_webp_jni.cpp` e libwebp 1.6.0. `Encoder` é o único proprietário do handle libwebp. O fluxo oferece cancelamento, erros técnicos em debug e timeout de três minutos. HDR PQ/HLG para SDR, VFR, seeks não-chave e variação entre fabricantes ainda precisam de validação. Media3/ExoPlayer continua responsável pela reprodução; Transformer MP4 não é usado para exportar stickers.

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
- Exportação animada: `VideoEditorViewModel` → `AnimatedStickerExporter` (`Dispatchers.IO`, `MediaExtractor`/`MediaCodec` ou `gifdecoder`) → `AnimatedWebpNative` (JNI/libwebp) → validação e persistência em `SaveStickerViewModel`; consulte também o plano atualizado para limitações de desempenho, VFR/HDR e validação WhatsApp ainda pendente.
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

## Estado da revisão (08/11/2026)

Em 08/11/2026, após otimizar exportação animada, passaram `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` e `lintDebug`; seis testes unitários cobrem o conversor YUV, incluindo downsampling estratificado. As três ABIs nativas compilaram e os sete testes instrumentados WebP passaram no AVD API 36.1. Esses testes validam a assinatura JNI e o encoder/muxer, não o processamento completo do vídeo do usuário. A busca foi limitada a dez candidatos antes da medição real no aparelho; a atualização descrita abaixo reduz o máximo para três combinações planejadas. A exportação de um trecho de um segundo já foi concluída no celular, mas o vídeo longo que atingiu timeout ainda precisa ser comparado. Persistem pendências de Android API 26, HDR/VFR, WhatsApp real e sistema com páginas de 16 KiB. Há avisos existentes de APIs/opções depreciadas no projeto.

Em 08/11/2026, a seleção de trecho foi ajustada para pausar a prévia durante o gesto e fazer seek só ao soltar, evitando disputa entre playback e RangeSlider. O undo guarda o estado anterior uma vez por gesto. A tela mostra início/fim/duração e atalhos de 1, 3, 5 e 10 segundos (limitados à duração da origem), úteis para uma exportação curta de teste. Após essa alteração passaram `testDebugUnitTest`, `assembleDebug` e `lintDebug`; o lint manteve os avisos existentes. O comportamento de seleção e exportação ainda precisa ser confirmado com o vídeo de teste no aparelho físico.

Em 08/11/2026, o painel de renderização foi reorganizado para explicar que o relógio de origem é relativo ao trecho selecionado, mostrar progresso em percentual/quadros, perfil, passe, FPS, qualidade e tamanho real codificado; quando um passe excede o limite, informa que o ajuste será refeito. O relógio do trecho mostra milissegundos para que a atividade fique visível mesmo em clipes de um segundo. As strings foram atualizadas em inglês e português. A estimativa continua sendo uma escolha inicial por perfil/duração: o tamanho exato só é conhecido após montar o WebP animado, e uma nova tentativa só é necessária quando o resultado real ultrapassa 512.000 bytes. Nesta alteração passaram `:app:testDebugUnitTest`, `:app:assembleDebug` e `:app:lintDebug`.

Após relato de que os atalhos e o arraste piscavam e voltavam para 10 segundos, foi identificado que cada retorno do ExoPlayer a `STATE_READY` reinicializava o corte e forçava `playWhenReady=true`. A inicialização agora ocorre uma vez, e a transição de estado não reativa a reprodução nem troca a seleção. Nesta correção passaram `testDebugUnitTest`, `assembleDebug` e `lintDebug`; falta validar a interação com o vídeo original no aparelho físico.

Em 08/11/2026 foi removido o bloqueio residual de pacotes animados: `CreatePackageDialog` agora oferece o tipo na Home e fixa o tipo correto ao criar pacote durante o salvamento. O botão de adicionar em pacote animado aceita vídeos, GIFs e imagens estáticas, propagando `isAnimated` até `SaveSticker`; imagem estática é muxada em dois quadros WebP. As strings adicionadas/alteradas foram traduzidas em `values` e `values-pt`. Passaram `testDebugUnitTest`, `assembleDebug` e `lintDebug`. A ativação no WhatsApp continua exigindo pelo menos três figurinhas por pacote.

O diagnóstico posterior da exportação mostrou que a busca binária executava cada codificação completa desde a fonte e podia fazer até dez passes. A implementação agora parte de FPS/qualidade escolhidos por perfil e duração e limita os recuos a dois, com teste unitário do planejador. O YUV é reduzido a 512 px no zoom padrão e até 1024 px quando o zoom pede mais detalhe; não foi adicionada transcodificação temporária da fonte. Build, testes e lint foram repetidos após a mudança; tempos e tamanhos reais no celular ainda precisam ser coletados.

