# Documentação do Projeto WppSticker

## Visão Geral
WppSticker é um aplicativo Android para criação e gerenciamento de pacotes de figurinhas (stickers) para WhatsApp. O app permite criar pacotes, adicionar figurinhas a partir de imagens da galeria, editar essas imagens (recorte, rotação, adição de texto) e enviá-las para o WhatsApp.

## Tecnologias Utilizadas
- **Linguagem:** Kotlin
- **UI:** Jetpack Compose
- **Injeção de Dependência:** Hilt
- **Navegação:** Jetpack Navigation Compose
- **Persistência de Dados:** Room Database
- **Imagens:** Coil (carregamento), CanHub Image Cropper (recorte inicial), Bitmap/Canvas (edição final)
- **Arquitetura:** MVVM (Model-View-ViewModel)

## Estrutura do Projeto (`:app`)

### Camada de Dados (`data`)
- **Local:**
    - `StickerDatabase`: Banco de dados Room.
    - `StickerDao`: Interface de acesso aos dados.
    - `StickerPackage`: Entidade que representa um pacote de figurinhas.
    - `Sticker`: Entidade que representa uma figurinha individual.
- **Repositório:**
    - `StickerRepository`: Abstração para acesso aos dados (implementado em `StickerRepositoryImpl`).
    - `BackupRepository`: Gerencia backup e restauração (JSON + arquivos).

### Camada de Domínio (`domain`)
- Contém UseCases para regras de negócio (ex: `CreateStickerPackageUseCase`, `AddStickerUseCase`).

### Camada de UI (`ui`)
- **Home (`ui.home`):**
    - `HomeScreen`: Lista os pacotes de figurinhas criados. Permite criar novo pacote, excluir e enviar para o WhatsApp.
    - `HomeViewModel`: Gerencia o estado da lista de pacotes.
- **Editor (`ui.editor`) - *Totalmente Revampado*:**
    - **`EditorScreen`**:
        - Implementa um "Workspace" quadrado com bordas tracejadas (guia de corte).
        - Permite zoom, pan e rotação da imagem de fundo com gestos (pinça).
        - **Snap to Grid Inteligente**: "Imã" configurável (5 níveis de força) que alinha rotação (0/90/180) e bordas da imagem ao quadrado.
        - **Adição de Texto**: Diálogo para adicionar texto com validação (não permite vazio).
        - **Manipulação de Texto**: Textos podem ser movidos, rotacionados e redimensionados (gesto ou slider). Suporte a múltiplas fontes e cores.
        - **Painel Inferior Unificado**: Alterna contextualmente entre controles de texto (quando selecionado) e controles de Snap (quando imagem ativa).
        - **Proteção**: Botão "Voltar" interceptado para evitar perda acidental de edição.
    - **`EditorViewModel`**:
        - Gerencia o estado da edição (`ImageState`, lista de `TextData`).
        - Lógica de "Snap" com separação entre valores brutos e visuais para movimento fluido.
        - **Renderização Final**: Reconstrói a cena em um `Bitmap` 512x512 usando `Canvas` nativo, garantindo fidelidade WYSIWYG (What You See Is What You Get), incluindo fontes personalizadas.
    - `TextData`: Modelo para textos (conteúdo, cor, posição, escala, rotação, fonte).
    - `ImageState`: Modelo para o estado da imagem de fundo.
- **Pacote (`ui.stickerpack`):**
    - `PackageScreen`: Detalhes de um pacote, lista as figurinhas contidas. Permite editar metadados do pacote.
    - `SaveStickerScreen`: Tela final antes de salvar. Permite escolher o pacote de destino e adicionar emojis (obrigatório pelo WhatsApp).
    - `EmojiPickerSheet`: BottomSheet para seleção de emojis categorizados.

### Navegação (`nav`)
- `NavGraph`: Define as rotas e argumentos da navegação.

## Funcionalidades Recentes Implementadas (Histórico de Mudanças)

### 1. Editor de Imagem ("Workspace")
- Substituída a biblioteca de crop simples por um **ambiente de edição manual**.
- O usuário vê um quadrado guia (512x512) e pode ajustar a imagem livremente dentro dele.
- **Performance:** Otimizações com `graphicsLayer` e `clipToBounds` para mover textos e imagens gigantes sem lag.

### 2. Ferramenta de Texto
- Botão dedicado (ícone 'T') na barra superior.
- Diálogo de entrada com validação.
- **Controles:** Slider de tamanho, Paleta de Cores, Seletor de Fontes (Default, Serif, Monospace, Cursive, Bold).
- Textos são objetos independentes na tela, selecionáveis por toque.

### 3. Snap to Grid (Alinhamento Magnético)
- Funcionalidade que "atrai" a imagem para ângulos retos e alinha as bordas ao quadrado de corte.
- **Ajustável:** 5 níveis de força selecionáveis na barra inferior.
- **Lógica Suave:** Implementação que evita que a imagem fique "travada" no imã, permitindo ajuste fino.

### 4. Fluxo de Salvamento
- Geração de imagem final 512x512px em WebP.
- Validação de tamanho (< 100KB) e dimensões estritas exigidas pelo WhatsApp.
- Compressão inteligente (loop de qualidade) para garantir o tamanho do arquivo.
- Criação automática de ícone de bandeja (tray icon) 96x96px.
- Verificação de duplicatas por Hash SHA-256 para evitar figurinhas repetidas no mesmo pacote.

### 5. Interface e UX
- Padronização dos painéis de controle na parte inferior da tela.
- Interceptação do botão "Voltar" durante a edição.
- Ícones intuitivos e feedback visual (toasts, loadings).

## Próximos Passos / Pendências
- Refinar ainda mais a paridade visual das fontes cursivas entre a tela de edição (Compose) e o Canvas de salvamento (Native Paint), garantindo 100% de igualdade em todos os dispositivos.
- Implementar sistema de backup/restore completo (estrutura já iniciada).

---
*Documentação atualizada em: [Data Atual]*
