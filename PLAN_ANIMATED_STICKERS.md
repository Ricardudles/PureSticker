# Plano de Implementação: Suporte a Figurinhas Animadas (PureSticker)

Este documento detalha o roteiro técnico para adicionar suporte robusto a figurinhas animadas no projeto **PureSticker**.

**Status:** 🏁 CONCLUÍDO (Maio/2024)

**Objetivo:** Permitir a criação de pacotes animados com ferramentas de edição profissionais: corte de tempo (trim), recorte visual (crop), e adição de texto, gerando arquivos WebP 100% compatíveis com o WhatsApp.

---

## Fase 1: Camada de Dados (Database & Entity) - ✅ CONCLUÍDO
*   [x] Adicionar `animated` em `StickerPackage`.
*   [x] Migração de Banco de Dados (v5 -> v6).
*   [x] Expor coluna `animated_sticker_pack` no `StickerContentProvider`.

---

## Fase 2: Ponto de Entrada e UI de Pacotes - ✅ CONCLUÍDO
*   [x] **Dialog de Criação:** Adicionado switch/checkbox "Pacote Animado".
*   [x] **Listagem:** Adicionado badge "ANIM" nos cards da tela inicial.
*   [x] **Validação:** Implementado `Safe Typing` para impedir mistura de tipos (Estático/Animado).

---

## Fase 3: Infraestrutura de Vídeo (O "Motor") - ✅ CONCLUÍDO (COM MUDANÇAS)
*Originalmente planejado com FFmpeg, mas migrado para AndroidX Media3 por questões de estabilidade e licenciamento.*

1.  **Integração AndroidX Media3 Transformer**
    *   Substituiu o FFmpeg-Kit.
    *   Permite: Transcodificação, Clipping (Trim), Crop e Overlays (Texto/Bitmap).
2.  **Integração Media3 ExoPlayer**
    *   Player otimizado para preview na tela de edição.

---

## Fase 4: Editor de Figurinhas Animadas (VideoEditor) - ✅ CONCLUÍDO
Tela: `VideoEditorScreen`

### 4.1. Seleção e Validação
*   [x] **Galeria:** Lançador específico para Vídeo/GIF.
*   [x] **Player:** ExoPlayer com loop para visualização constante.

### 4.2. Ferramentas de Edição
*   [x] **Trim (Cortar Tempo):** RangeSlider para definir início e fim.
*   [x] **Crop (Recorte Espacial):** Gestos de Pan/Zoom para enquadrar o vídeo no canvas 512x512.
*   [x] **Texto:** Adição de texto sobreposto (renderizado via Overlay no Media3).
*   [x] **Snap-to-Grid:** Auxílio de alinhamento.

---

## Fase 5: Pipeline de Processamento - ✅ CONCLUÍDO
O processamento ocorre via `VideoEditorViewModel` utilizando `Transformer`.

1.  **Input:** URI do vídeo original.
2.  **Transformações:**
    *   Trim (MediaItem configuration).
    *   Crop/Scale (MatrixTransformation).
    *   Overlays (TextOverlay/BitmapOverlay).
3.  **Output:**
    *   Formato: WebP Animado (MimeType: `image/webp`).
    *   Restrições: 512x512px, < 500KB.
    *   Fallback: Em alguns casos, gera GIF/MP4 que é convertido internamente pelo Coil/ImageDecoder se necessário, mas o foco é WebP nativo.

---

## Fase 6: Validação Final e Testes - ✅ CONCLUÍDO
*   [x] Testar envio para o WhatsApp (Integration Flow).
*   [x] Limpeza de arquivos temporários.
*   [x] Undo/Redo implementado para ações de edição.
