# Figurinhas animadas WebP — estado de implementação

Atualizado em 06/11/2026. Este documento descreve o código atual e as validações feitas nesta implementação. O recurso ainda precisa de testes em aparelhos e WhatsApp reais antes de ser considerado pronto para lançamento.

## Implementado

- A seleção de mídia aceita vídeos e GIFs e encaminha ambos ao editor animado. O editor permite trecho de até 10 segundos, preview em loop, enquadramento/zoom/rotação, texto, grade, desfazer/refazer e perfis **Mais fluida**, **Equilibrada** e **Mais nítida**.
- GIF é lido sequencialmente com o módulo isolado `gifdecoder`. Vídeo é amostrado por tempo usando `MediaMetadataRetriever`; Media3/ExoPlayer continua responsável pela reprodução e Transformer não é mais usado para criar a figurinha.
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

1. O vídeo usa `MediaMetadataRetriever` com busca por timestamp. Isso não entrega uma leitura sequencial de PTS com precisão garantida para VFR. Migrar para `MediaExtractor`/`MediaCodec`, tratar PTS por quadro e verificar codecs/strides/colorimetria.
2. Conversão HDR→SDR não está explicitamente controlada nem garantida idêntica ao preview. Validar fontes HDR em aparelhos diferentes e implementar tone mapping consistente.
3. A orientação é deixada ao decoder/plataforma; faltam testes específicos para metadados de rotação e proporção de pixel.
4. A busca de qualidade pode recodificar os mesmos quadros muitas vezes. Medir duração, memória e bateria em aparelhos modestos e limitar/adaptar o número de tentativas sem trocar medição real por estimativa.
5. O perfil mostra progresso durante a busca e tamanho/FPS ao concluir, mas ainda não existe etapa dedicada para comparar a prévia final antes de ir para seleção do pacote.
6. Os testes instrumentados `AnimatedWebpNativeTest` executaram e passaram no AVD API 36.1: `WebPMux` conservou dois quadros iguais; `WebPAnimEncoder` codificou dois quadros diferentes; a validação aceitou 8 ms e 10 s exatos, rejeitou 7 ms e 10 s + 1 ms, e o decoder leu o primeiro quadro.
7. Ainda não foram feitos testes em Android API 26, aparelho físico moderno, kernel/ambiente configurado com páginas de 16 KiB ou WhatsApp real (adicionar pacote, enviar, conferir loop e limites). O alinhamento ELF/APK foi verificado, mas não substitui o teste em sistema com página de 16 KiB.

## Verificações locais executadas

- `:app:assembleDebug` — passou.
- `:app:testDebugUnitTest` — passou.
- `:app:compileDebugAndroidTestKotlin` — passou.
- `:app:connectedDebugAndroidTest` — passou no AVD `Medium_Phone_API_36.1` (4 testes, incluindo muxagem de quadros duplicados, encoder e limites de quadro/duração).
- `:app:lintDebug` — passou.
- `:app:externalNativeBuildDebug` — passou para arm64-v8a, armeabi-v7a e x86_64.
- Verificação ELF via `llvm-readelf`: segmentos de 64 bits com alinhamento 16 KiB.
- `zipalign -c -P 16 4 app-debug.apk` — passou.

`versionCode`/`versionName` permanecem em 4/1.3; não foram incrementados porque a exportação ainda tem validações abertas antes do lançamento. A versão do app deve ser incrementada quando o recurso estiver aprovado após os testes em aparelhos e WhatsApp reais.
