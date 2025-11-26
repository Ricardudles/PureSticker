# Plano de Melhorias - WppSticker v2.0

Este documento registra as melhorias planejadas e executadas no aplicativo, focando em estabilidade, experiência do usuário e novas funcionalidades.

---

### Fase 1: Estabilidade e UX Crítica

**1. Tratamento Robusto de Permissões**
   - **Status:** Concluído.
   - **Problema:** O botão de adicionar figurinha (imagem/vídeo) falhava silenciosamente se a permissão de acesso à galeria fosse negada permanentemente.
   - **Solução Aplicada:** Foi implementado um diálogo educativo no `PackageScreen.kt` que:
     - Verifica o status da permissão antes de tentar pedi-la.
     - Se a permissão foi negada permanentemente, exibe um alerta explicando a necessidade do acesso.
     - Oferece um atalho que leva o usuário **direto para a tela de configurações do app**, facilitando a liberação manual da permissão.

---

### Fase 2: Funcionalidades de Edição Avançada

**2. Sistema de Desfazer/Refazer (Undo/Redo) no Editor**
   - **Status:** Concluído (Imagem e Vídeo).
   - **Problema:** A edição é destrutiva. Se o usuário muda uma cor, move um texto ou altera a imagem, não pode voltar atrás facilmente.
   - **Solução Aplicada:**
     1.  **ViewModel:** Implementado pilhas (Stack) para armazenar os "estados" do editor a cada modificação significativa (ex: `VideoEditorState`, `EditorState`).
     2.  **Ação de Desfazer (Undo):** Ação que remove o estado mais recente da pilha principal e o move para uma segunda pilha (a pilha de "refazer"), aplicando o estado anterior ao editor.
     3.  **Ação de Refazer (Redo):** Ação que move o estado do topo da pilha "refazer" de volta para a pilha principal, reaplicando a mudança.
     4.  **UI:** Adicionados botões de "Desfazer" e "Refazer" na barra de ferramentas dos editores de imagem e vídeo. Os botões ficam habilitados/desabilitados conforme o estado das pilhas.
     5.  **Otimização de Gestos:** Implementado um sistema para salvar o estado apenas no início de gestos contínuos (arrastar/pinçar) para evitar sobrecarga da memória e da pilha de histórico com micro-movimentos.

---

### Fase 3: Refinamento do Fluxo de Criação de Figurinhas & Suporte a Vídeo

**3.1. Estrutura e Seleção de Tipo**
   - **Status:** Concluído.
   - **Objetivo:** Criar a tela intermediária `StickerTypeSelectionScreen` e ajustar a navegação inicial da Home.
   - **Solução Aplicada:**
     - Criada a `StickerTypeSelectionScreen` com opções de "Static" e "Animated".
     - Ajustado `NavGraph` para incluir a nova rota.
     - Modificado FAB da `HomeScreen` para navegar para esta nova tela.

**3.2. Integração de Mídia e Navegação para Editor**
   - **Status:** Concluído.
   - **Objetivo:** Conectar a tela de seleção com a galeria e passar os dados corretos para os editores.
   - **Solução Aplicada:**
     - Implementados lançadores de permissão e seleção de arquivos (Imagem/Vídeo) na nova tela.
     - Navegação direta para `EditorScreen` ou `VideoEditorScreen` passando a URI selecionada.

**3.3. Tela de Seleção de Pacote (Destino)**
   - **Status:** Concluído.
   - **Objetivo:** Permitir que o usuário escolha onde salvar a figurinha *após* a edição.
   - **Solução Aplicada:**
     - Criada `PackageSelectionScreen` que lista os pacotes disponíveis.
     - No `SaveStickerScreen`, o campo de pacote agora é clicável e leva para esta tela de seleção, retornando o ID escolhido via `SavedStateHandle`.

**3.4. Criação de Pacote In-Flow**
   - **Status:** Concluído.
   - **Objetivo:** Permitir criar um novo pacote diretamente na tela de seleção de destino.
   - **Solução Aplicada:**
     - Adicionado botão flutuante (+) na `PackageSelectionScreen` que abre o diálogo de criação de pacote.
     - Integrado com `HomeViewModel` para criar o pacote e atualizar a lista instantaneamente.

**3.5. Infraestrutura de Vídeo (FFmpeg e WebP Animado)**
   - **Status:** Concluído.
   - **Objetivo:** Integrar engine de processamento de vídeo para criar stickers animados compatíveis com WhatsApp.
   - **Solução Aplicada:**
     - Adicionada biblioteca `ffmpeg-kit-full`.
     - Implementado `VideoEditorViewModel` com pipeline FFmpeg: Cortar tempo (Trim), Recortar área (Crop), Redimensionar, Aplicar Texto (DrawText) e Converter para WebP Animado.
     - Configurados parâmetros para output < 500KB (qualidade 75, loop infinito, sem áudio).

**3.6. Validação de Tipos (Safe Typing)**
   - **Status:** Concluído.
   - **Objetivo:** Impedir que usuários salvem figurinhas animadas em pacotes estáticos (e vice-versa).
   - **Solução Aplicada:**
     - `SaveStickerScreen` recebe argumento `isAnimated` baseado no editor usado.
     - `PackageSelectionScreen` filtra a lista de pacotes para mostrar apenas compatíveis.
     - `CreatePackageDialog` força a criação do tipo correto de pacote quando aberto durante o fluxo de salvamento.

---

### Fase 4: Polimento e Finalização

**4.1. Internacionalização Completa**
   - **Status:** Concluído.
   - **Problema:** Múltiplas telas continham texto fixo em inglês, impedindo a tradução do app.
   - **Solução Aplicada:**
     - Centralizei todas as strings de UI no arquivo `strings.xml`.
     - Substituí o texto fixo nas telas `EditorScreen`, `VideoEditorScreen`, `SettingsScreen`, `RestorePreviewScreen`, `StickerTypeSelectionScreen` e `PackageSelectionScreen` para que usem as novas referências do arquivo de strings.

**4.2. Correção de Layout (Safe Area)**
   - **Status:** Concluído.
   - **Problema:** O botão inferior na tela de restauração de backup (`RestorePreviewScreen`) sobrepunha a barra de navegação do sistema.
   - **Solução Aplicada:**
     - Adicionado modificador `navigationBarsPadding()` ao container do botão na `RestorePreviewScreen`.

**4.3. Padronização de UI (Botões)**
   - **Status:** Concluído.
   - **Objetivo:** Garantir consistência com os padrões de design do Android (Material Design).
   - **Solução Aplicada:**
     - Inversão da ordem dos botões em telas de confirmação e diálogos.
     - **Padrão Adotado:** Ações Afirmativas/Primárias (Confirm, Save, Next) posicionadas à **Direita**. Ações Negativas/Secundárias (Cancel, Back) posicionadas à **Esquerda**.

**4.4. Fluxo de Restauração Aprimorado**
   - **Status:** Concluído.
   - **Objetivo:** Evitar restauração cega de todos os pacotes de um backup.
   - **Solução Aplicada:**
     - Implementação da `RestorePreviewScreen`.
     - O arquivo ZIP é lido previamente, listando os pacotes contidos com metadados (autor, qtd stickers).
     - Usuário seleciona quais pacotes deseja importar antes do processamento final.
