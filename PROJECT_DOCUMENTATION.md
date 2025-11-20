# Documentação do Projeto WppSticker

Este documento resume o estado atual do desenvolvimento, as decisões técnicas tomadas e as funcionalidades implementadas até o momento. Serve como um ponto de restauração de contexto para futuras sessões de desenvolvimento.

## 1. Visão Geral
O **WppSticker** é um aplicativo Android para criação, gerenciamento e compartilhamento de pacotes de figurinhas para o WhatsApp. Ele permite aos usuários importar imagens, editá-las (cortar, adicionar texto), organizar em pacotes e enviar diretamente para o WhatsApp.

## 2. Stack Tecnológica
-   **Linguagem:** Kotlin
-   **UI Toolkit:** Jetpack Compose
-   **Arquitetura:** MVVM (Model-View-ViewModel) com Clean Architecture
-   **Injeção de Dependência:** Hilt
-   **Banco de Dados:** Room
-   **Navegação:** Jetpack Navigation Compose
-   **Carregamento de Imagens:** Coil
-   **Edição de Imagem:** Canvas API (Nativo) + Android-Image-Cropper (CanHub)
-   **Assincronismo:** Coroutines & Flow

## 3. Funcionalidades Implementadas

### 3.1. Tela Inicial (`HomeScreen`)
-   **Lista de Pacotes:** Exibe os pacotes criados em `Cards` modernos com fundo escuro.
-   **Prévia Inteligente:**
    -   Mostra as primeiras figurinhas do pacote.
    -   **Contador Sobreposto:** Caso haja mais de 6 figurinhas, a última imagem da prévia recebe um overlay escuro com o contador "+N" (onde N é o número de figurinhas restantes), em vez de ocupar um slot vazio.
-   **Ações Rápidas:** Botões para enviar para o WhatsApp e excluir o pacote.
-   **Design:** Tema escuro (`#121212`), sem ícone de bandeja redundante, foco no conteúdo.

### 3.2. Tela de Detalhes do Pacote (`PackageScreen`)
-   **Grid de Figurinhas:** Exibição limpa das figurinhas em 3 colunas.
-   **Botões de Ação (FABs):**
    -   **Add Sticker:** Botão flutuante roxo (padrão) no canto inferior direito.
    -   **Add to WhatsApp:** Botão "Extended FAB" verde (pílula) posicionado ao lado do botão de adicionar, facilitando a exportação rápida.
    -   Ambos os botões respeitam as margens de navegação do sistema (`navigationBarsPadding`).
-   **Seleção Múltipla:** Toque longo ativa o modo de seleção para excluir várias figurinhas de uma vez.
-   **Edição de Metadados:** Permite editar nome, autor, email, site, política de privacidade e licença do pacote com validações robustas.
-   **Feedback:** Uso de `Toast` para informar sucesso ou erros de validação.

### 3.3. Editor de Figurinhas (`EditorScreen`)
-   **WYSIWYG:** O que você vê na tela é exatamente o que será salvo (fontes e proporções consistentes).
-   **Ferramentas:**
    -   **Corte:** Integração com CropImage.
    -   **Texto:** Adição de textos com múltiplas fontes, cores e redimensionamento/rotação.
    -   **Imã (Snap):** Sistema inteligente de alinhamento (snap) para centralizar e alinhar elementos.
-   **Interface:** Dock flutuante na parte inferior para ferramentas, maximizando a área de trabalho.

### 3.4. Salvamento (`SaveStickerScreen`)
-   **Seleção de Pacote:** Escolha fácil do pacote de destino ou criação de um novo.
-   **Emojis:** Seletor de emojis completo e categorizado para metadados da figurinha (exigência do WhatsApp).
-   **Validações:** Verifica limites de tamanho (KB) e dimensões (512x512px) automaticamente.

### 3.5. Configurações e Backup (`SettingsScreen` / `RestorePreviewScreen`)
-   **Backup:** Exporta todos os pacotes e imagens para um arquivo ZIP.
-   **Restauração:** Importa backups ZIP, verificando duplicatas antes de restaurar.
-   **Limpeza:** Ferramenta para remover imagens órfãs (não usadas em nenhum pacote) e liberar espaço.

## 4. Design System
-   **Tema:** Dark Mode forçado para consistência com ferramentas de edição profissionais.
-   **Cores:**
    -   Fundo: `#121212` (Almost Black)
    -   Superfícies: `#1E1E1E` (Dark Grey)
    -   Primária: `#BB86FC` (Soft Purple)
    -   Secundária: `#03DAC6` (Teal) e `#25D366` (WhatsApp Green)
-   **Transições:** Animações suaves de slide e fade entre todas as telas.
-   **Componentes:** Uso extensivo de `Card` com `RoundedCornerShape(16.dp)` e `Elevation`.

## 5. Melhorias Recentes (Sessão Atual)
-   **Flash Branco Eliminado:** Configurado `android:windowBackground` para `#121212` no tema XML.
-   **Transições Suaves:** Implementadas animações globais no `NavHost`.
-   **Concorrência Resolvida:** Corrigido bug no `PackageViewModel` que sobrescrevia edições ao salvar.
-   **Validações de Pacote:** Adicionadas validações de nome, autor e URL na edição de pacotes.
-   **Layout de Botões:** Ajustado `PackageScreen` para ter botões "Add Sticker" e "Add to WhatsApp" lado a lado.
-   **Prévia Melhorada:** Implementada lógica de overlay "+N" na última imagem da prévia na Home.

## 6. Próximos Passos (Backlog Imediato)
-   **Correção de Contagem:** Verificar se a contagem "+N" na `HomeScreen` está exibindo o valor correto (atualmente parece travada ou incorreta).
-   **Testes Finais:** Validar fluxo de ponta a ponta.

---
*Gerado automaticamente pela IA Assistente em 27/05/2024.*
