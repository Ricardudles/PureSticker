# Figurinhas animadas WebP — estado de implementação

Atualizado em 06/11/2026. Este documento descreve o código atual e as validações feitas nesta implementação. O recurso ainda precisa de testes em aparelhos e WhatsApp reais antes de ser considerado pronto para lançamento.

## Implementado

- A seleção de mídia aceita vídeos e GIFs e encaminha ambos ao editor animado. O editor permite trecho de até 10 segundos, preview em loop, enquadramento/zoom/rotação, texto, grade, desfazer/refazer e perfis **Mais fluida**, **Equilibrada** e **Mais nítida**.
- GIF é lido sequencialmente com o módulo isolado `gifdecoder`. Vídeo é lido sequencialmente por `MediaExtractor`/`MediaCodec` em modo de buffers com `COLOR_FormatYUV420Flexible`; cada `Image` expõe três planos YUV que são convertidos considerando crop, row/pixel stride, BT.601/BT.709/BT.2020 e range full/limited. A imagem intermediária é limitada a 1024 px por lado (2× o canvas de saída) com média de pixels para controlar memória sem reduzir o canvas WebP de 512×512. A saída não depende de `Surface`/`ImageReader`, pois o decoder Samsung testado entregou um formato PRIVATE incompatível com o RGBA solicitado. Media3/ExoPlayer continua responsável pela reprodução e Transformer não é usado para criar a figurinha.
- A exportação ocorre em `Dispatchers.IO`, fora da thread de UI. O decoder usa o worker do codec; quadros são convertidos, renderizados e entregues ao encoder nativo em ordem e um por vez. Cada imagem YUV é fechada e seu buffer do codec liberado em `finally`; a codificação WebP permanece serial para preservar timestamps e segurança do encoder. Cada candidato ainda reabre e decodifica a fonte, portanto o custo cresce com o número de tentativas.
- O diálogo de exportação mostra etapa, tentativa/perfil, progresso de quadros, posição de origem e tempo decorrido. Cancelamento e timeout de três minutos são cooperativos; erros são registrados e exibidos, incluindo stack trace apenas em build debug.
- O handle JNI do encoder tem um único proprietário: o destrutor `Encoder` libera o `WebPAnimEncoder` ao cancelar uma tentativa. `AnimatedWebpNative.abort()` não pode liberar o encoder separadamente.
- Os quadros são renderizados em canvas 512×512 com texto e transformações. A saída é codificada pelo `WebPAnimEncoder` do libwebp JNI 1.6.0. A busca tenta combinações de FPS e qualidade, mede cada arquivo produzido e mantém a primeira combinação que passa na validação e cabe em 512.000 bytes.
- A prioridade dos perfis é: FPS e depois qualidade; qualidade e depois FPS; e produto normalizado FPS×qualidade, respectivamente. Os FPS tentados partem da taxa estimada da fonte, até 125 FPS (limite decorrente de 8 ms por quadro), sem limite arbitrário de 30 FPS.
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
3. A busca de qualidade ainda redecodifica a fonte para cada candidato (até 35 combinações atualmente). O timeout evita espera ilimitada, mas medir duração, memória e bateria em aparelhos modestos; depois otimizar número de tentativas ou reutilizar quadros em cache limitado sem substituir tamanho medido por estimativa.
4. O perfil mostra progresso durante a busca e tamanho/FPS ao concluir, mas ainda não existe etapa dedicada para comparar a prévia final antes de ir para seleção do pacote.
5. Os testes instrumentados `AnimatedWebpNativeTest` executaram e passaram no AVD API 36.1: `WebPMux` conservou dois quadros iguais; `WebPAnimEncoder` codificou dois quadros diferentes; a validação aceitou 8 ms e 10 s exatos, rejeitou 7 ms e 10 s + 1 ms, e o decoder leu o primeiro quadro.
6. Ainda não foram feitos testes em Android API 26, aparelho físico moderno com esta versão do decoder, kernel/ambiente configurado com páginas de 16 KiB ou WhatsApp real (adicionar pacote, enviar, conferir loop e limites). O alinhamento ELF/APK foi verificado, mas não substitui o teste em sistema com página de 16 KiB.
7. A conversão YUV interpreta matrizes e faixa conforme metadados do decoder. Tone mapping PQ/HLG de HDR para SDR ainda não está implementado/validado neste caminho; clipes HDR devem ser tratados como pendência antes do lançamento.
7. Em 06/11/2026, o Samsung apresentou `SIGABRT` ao abortar uma tentativa: `AnimatedWebpNative.abort()` liberava o `WebPAnimEncoder` e depois `Encoder::~Encoder()` liberava o mesmo ponteiro novamente. O Scudo identificou estado de chunk inválido. A chamada duplicada foi removida; os caminhos de abort vazio, com um quadro e com dois quadros agora passam no AVD API 36. A nova versão ainda não foi instalada no Samsung porque a sessão LLDB da falha está pausada nele.
8. No checkout aberto, Gradle 9.1 falhou ao registrar a saída CMake porque o arquivo temporário `libanimated_webp.so934e1d56.tmp` desapareceu durante o cálculo MD5. O mesmo código compilou para as três ABIs e passou nos testes/lint numa cópia isolada; investigar a interação do output CMake/IDE para voltar a validar builds diretamente no checkout aberto.

## Verificações locais executadas

- `:app:assembleDebug` — passou.
- `:app:testDebugUnitTest` — passou.
- `:app:compileDebugAndroidTestKotlin` — passou.
- `:app:connectedDebugAndroidTest` — passou no AVD `Medium_Phone_API_36.1` (7 testes: muxagem, encode/decode, limites e abort de encoder vazio/parcial).
- `:app:lintDebug` — passou.
- `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug` — passaram após implementar o decoder sequencial, feedback, cancelamento e timeout (06/11/2026).
- `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug` — passaram numa cópia de validação isolada com a correção de ownership JNI; os 7 testes instrumentados passaram em `Medium_Phone_API_36.1` (06/11/2026).
- `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — passaram após substituir a saída Surface/ImageReader por buffers YUV flexíveis e adicionar cinco testes do conversor (06/11/2026). `:app:connectedDebugAndroidTest` também passou 7/7 no AVD API 36.1 e no SM-S908E; esses testes cobrem o encoder/muxer WebP, não o decode do vídeo do usuário. A validação ponta a ponta no Samsung com o vídeo que falhou continua pendente.
- Build no checkout aberto — `:app:buildCMakeDebug[arm64-v8a]` ainda falha ao calcular o MD5 de `.so934e1d56.tmp` inexistente; este erro não ocorreu na cópia isolada usada para a validação acima.
- `:app:externalNativeBuildDebug` — passou para arm64-v8a, armeabi-v7a e x86_64.
- Verificação ELF via `llvm-readelf`: segmentos de 64 bits com alinhamento 16 KiB.
- `zipalign -c -P 16 4 app-debug.apk` — passou.

`versionCode`/`versionName` permanecem em 4/1.3; não foram incrementados porque a exportação ainda tem validações abertas antes do lançamento. A versão do app deve ser incrementada quando o recurso estiver aprovado após os testes em aparelhos e WhatsApp reais.
