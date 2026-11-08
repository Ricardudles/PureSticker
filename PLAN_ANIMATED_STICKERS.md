# Figurinhas animadas WebP — estado de implementação

Atualizado em 08/11/2026. Este documento descreve o código atual e as validações feitas nesta implementação. O recurso ainda precisa de testes ponta a ponta com vídeos reais em aparelhos e WhatsApp antes de ser considerado pronto para lançamento.

## Implementado

- A seleção de mídia aceita vídeos e GIFs e encaminha ambos ao editor animado. O editor permite trecho de até 10 segundos, preview em loop, enquadramento/zoom/rotação, texto, grade, desfazer/refazer e perfis **Mais fluida**, **Equilibrada** e **Mais nítida**.
- GIF é lido sequencialmente com o módulo isolado `gifdecoder`. Vídeo é lido sequencialmente por `MediaExtractor`/`MediaCodec` em modo de buffers com `COLOR_FormatYUV420Flexible`; cada `Image` expõe três planos YUV convertidos considerando crop, row/pixel stride, BT.601/BT.709/BT.2020 e range full/limited. A imagem intermediária varia de 512 a 1024 px por lado conforme o zoom (uma vez a duas vezes o canvas), por redução estratificada, mantendo a saída 512×512 e diminuindo o custo quando não há zoom. A saída não depende de `Surface`/`ImageReader`, pois o decoder Samsung testado entregou um formato PRIVATE incompatível com o RGBA solicitado. Media3/ExoPlayer continua responsável pela reprodução e Transformer não é usado para criar a figurinha.
- A exportação ocorre em `Dispatchers.IO`, fora da thread de UI. `MediaCodec.createDecoderByType()` escolhe o decoder preferido pelo aparelho; o app registra nome e indicador `isHardwareAccelerated` a partir da API 29, mas não força um componente específico. A saída atual é YUV em buffers, então leitura/conversão ARGB acontece na CPU mesmo quando a decodificação é feita pelo hardware. Cada imagem YUV é fechada e seu buffer do codec liberado em `finally`; a codificação WebP permanece serial para preservar timestamps e segurança do encoder. O planejador agora inicia com um perfil FPS/qualidade baseado na duração e faz no máximo dois recuos; cada recuo ainda reabre e decodifica a fonte inteira.
- O diálogo de exportação mostra etapa, tentativa/perfil, progresso de quadros, posição de origem e tempo decorrido. Cancelamento e timeout de três minutos são cooperativos; erros são registrados e exibidos, incluindo stack trace apenas em build debug.
- O handle JNI do encoder tem um único proprietário: o destrutor `Encoder` libera o `WebPAnimEncoder` ao cancelar uma tentativa. `AnimatedWebpNative.abort()` não pode liberar o encoder separadamente.
- Os quadros são renderizados em canvas 512×512 com texto e transformações. A saída é codificada pelo `WebPAnimEncoder` do libwebp JNI 1.6.0. O encoder usa método 2/3/4 para Mais fluida/Equilibrada/Mais nítida; `minimize_size` fica desativado. Cada WebP candidato passa por medição real de bytes e validação completa; `AnimatedStickerExporter` registra FPS, qualidade, bytes e tempo de cada passe.
- O decoder com `SurfaceTexture` e leitura via OpenGL ES é uma otimização futura a avaliar: a documentação Android indica que buffers Surface não precisam ser mapeados/copied para ByteBuffers e que `SurfaceTexture` recebe saída de `MediaCodec`. Isso pode remover a conversão YUV por pixel na CPU, mas ainda precisa de fallback, medição em fabricantes diferentes e testes do preview/cor antes de substituir o caminho atual. A codificação final WebP/libwebp permanece em CPU.
- Os perfis agora definem qualidade inicial e orçamento aproximado de quadros: Mais fluida 76/180, Equilibrada 82/125 e Mais nítida 90/100. O FPS inicial é o menor entre a fonte e o orçamento dividido pela duração; clipes curtos podem manter a taxa integral da fonte, sem limite artificial de 30 FPS. Há até três candidatos em ordem decrescente de custo: o primeiro perfil e dois recuos menores de FPS/qualidade. O tamanho de cada arquivo real continua sendo o critério final; não se estima nem se pressupõe o tamanho.
- Antes de salvar, o libwebp demuxer verifica RIFF/WebP, canvas 512×512, sinalizador de animação, mais de um quadro, duração mínima de 8 ms por quadro e duração total máxima de 10 s. Pacotes continuam homogêneos: fontes animadas são recusadas em pacotes estáticos.
- Imagem estática destinada a pacote animado é codificada e montada com `WebPMux` em dois quadros explícitos, evitando a deduplicação que o `WebPAnimEncoder` faz em quadros iguais. O ícone do pacote usa o primeiro quadro.
- O arquivo final é copiado para temporário, revalidado e renomeado antes da persistência. Inserção do sticker e atualização do pacote/versão ocorrem na mesma transação Room. Falhas e cancelamentos removem os arquivos temporários. A limpeza no início do app remove temporários antigos.
- O app mantém `minSdk 26`, `compileSdk/targetSdk 35`, AGP 9.0.0, Gradle 9.1, Java 17 e Media3 1.3.1. Kotlin/Compose plugin 2.2.10, KSP 2.3.10 e Hilt 2.60.1 compilaram juntos.
- GIF de entrada é limitado a 50 MiB, 4096 px por lado, 16 megapixels e 5000 quadros para manter o consumo do decoder controlado.

## Versões e compatibilidade observadas

- libwebp 1.6.0 foi incorporado a partir do upstream oficial. O commit de origem é `4fa21912338357f89e4fd51cf2368325b59e9bd9`; licença e aviso de patentes permanecem em `app/src/main/cpp/libwebp/COPYING` e `PATENTS`.
- O plano indicava `com.github.bumptech.glide:gifdecoder:5.0.9`. O AAR dessa versão exige compileSdk 37, enquanto este projeto usa compileSdk 35 e AGP 9.0.0. O build rejeitou a combinação. Foi usado `gifdecoder:4.16.0`, que compilou com a base existente e fornece a API necessária.
- A máquina possui NDK `29.0.14206865`, não o NDK r30 previsto inicialmente. O projeto fixa o NDK instalado para builds reproduzíveis e habilita páginas flexíveis. Os segmentos ELF LOAD das bibliotecas `arm64-v8a` e `x86_64` foram conferidos como alinhados a `0x4000` (16 KiB); `armeabi-v7a` foi compilado para compatibilidade 32-bit.
- ABIs configuradas: `arm64-v8a`, `armeabi-v7a` e `x86_64`. Media3 Transformer foi removido do bundle porque não participa mais da exportação.

## Limitações ainda abertas

1. A saída YUV flexível remove a incompatibilidade Surface/ImageReader observada num Samsung SM-S908E, e os testes unitários agora cobrem strides, chroma intercalado, crop e matrizes/range de cor. Ainda é necessário concluir o fluxo completo num aparelho físico com o vídeo que reproduziu a falha e ensaiar VFR, seek para início não-chave, orientação, codecs indisponíveis e URIs revogadas.
2. Conversão HDR→SDR não está explicitamente controlada nem garantida idêntica ao preview. Validar fontes HDR em aparelhos diferentes e implementar tone mapping consistente.
3. Cada candidato ainda redecodifica a fonte. A busca binária anterior podia repetir o decode/encode até dez vezes; foi trocada por um primeiro candidato conservador baseado em duração/perfil e até dois recuos. Essa estratégia deve ser medida com o mesmo clipe em aparelhos modestos; se um caso falhar nos três candidatos, registrar tamanho/tempo de cada passe antes de ampliar o fallback ou introduzir cache limitado de quadros.
4. O perfil mostra progresso durante a busca e tamanho/FPS ao concluir, mas ainda não existe etapa dedicada para comparar a prévia final antes de ir para seleção do pacote.
5. Os testes instrumentados `AnimatedWebpNativeTest` executaram e passaram no AVD API 36.1: `WebPMux` conservou dois quadros iguais; `WebPAnimEncoder` codificou dois quadros diferentes; a validação aceitou 8 ms e 10 s exatos, rejeitou 7 ms e 10 s + 1 ms, e o decoder leu o primeiro quadro.
6. Ainda não foram feitos testes em Android API 26, aparelho físico moderno com esta versão do decoder, kernel/ambiente configurado com páginas de 16 KiB ou WhatsApp real (adicionar pacote, enviar, conferir loop e limites). O alinhamento ELF/APK foi verificado, mas não substitui o teste em sistema com página de 16 KiB.
7. A conversão YUV interpreta matrizes e faixa conforme metadados do decoder. Tone mapping PQ/HLG de HDR para SDR ainda não está implementado/validado neste caminho; clipes HDR devem ser tratados como pendência antes do lançamento.
8. O decoder escolhido e seu estado de aceleração são registrados em log; a saída YUV atual continua com conversão CPU. Medir o mesmo vídeo no SM-S908E e num aparelho intermediário antes de adotar o render para `SurfaceTexture`/OpenGL ES; não presumir aceleração de WebP por hardware.
7. Em 06/11/2026, o Samsung apresentou `SIGABRT` ao abortar uma tentativa: `AnimatedWebpNative.abort()` liberava o `WebPAnimEncoder` e depois `Encoder::~Encoder()` liberava o mesmo ponteiro novamente. O Scudo identificou estado de chunk inválido. A chamada duplicada foi removida; os caminhos de abort vazio, com um quadro e com dois quadros passam nos testes instrumentados.

## Verificações locais executadas

- `:app:assembleDebug` — passou.
- `:app:testDebugUnitTest` — passou.
- `:app:compileDebugAndroidTestKotlin` — passou.
- `:app:connectedDebugAndroidTest` — passou no AVD `Medium_Phone_API_36.1` (7 testes: muxagem, encode/decode, limites e abort de encoder vazio/parcial).
- `:app:lintDebug` — passou.
- `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug` — passaram após implementar o decoder sequencial, feedback, cancelamento e timeout (06/11/2026).
- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` — passaram numa cópia de validação isolada com a correção de ownership JNI; os 7 testes instrumentados passaram em `Medium_Phone_API_36.1` (06/11/2026).
- `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` — passaram em 08/11/2026 após limitar as tentativas, acelerar o encoder e reduzir o custo do conversor YUV; seis testes unitários cobrem cor, strides, crop, buffers truncados e downsampling.
- Após planejar candidatos por duração e limitar o processamento a três passes, `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passaram; três testes novos cobrem taxa integral em clipe curto, orçamento em clipe longo e ausência de upsampling.
- `:app:externalNativeBuildDebug` — passou para arm64-v8a, armeabi-v7a e x86_64 em 08/11/2026.
- Os sete testes instrumentados `AnimatedWebpNativeTest` passaram no AVD API 36.1 após reconstruir e instalar os APKs atuais; validaram a assinatura JNI com método WebP configurável e os limites/muxagem nativos.
- A reprodução ponta a ponta com o vídeo que expirou no telefone ainda está pendente; os testes instrumentados não executam `AnimatedStickerExporter` com esse vídeo.

Referências de desempenho: [saída Surface vs ByteBuffer no MediaCodec](https://developer.android.com/reference/android/media/MediaCodec) e [SurfaceTexture como destino de vídeo decodificado](https://developer.android.com/reference/android/graphics/SurfaceTexture).
- `:app:externalNativeBuildDebug` — passou para arm64-v8a, armeabi-v7a e x86_64.
- Verificação ELF via `llvm-readelf`: segmentos de 64 bits com alinhamento 16 KiB.
- `zipalign -c -P 16 4 app-debug.apk` — passou.

`versionCode`/`versionName` permanecem em 4/1.3; não foram incrementados porque a exportação ainda tem validações abertas antes do lançamento. A versão do app deve ser incrementada quando o recurso estiver aprovado após os testes em aparelhos e WhatsApp reais.
