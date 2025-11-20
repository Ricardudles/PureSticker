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
-   **Prévia de Figurinhas:** Mostra as primeiras 6 figurinhas de cada pacote diretamente no card.
-   **Ações Rápidas:** Botões para enviar para o WhatsApp e excluir o pacote.
-   **Design:** Tema escuro (`#121212`), sem ícone de bandeja redundante, foco no conteúdo.

### 3.2. Tela de Detalhes do Pacote (`PackageScreen`)
-   **Grid de Figurinhas:** Exibição limpa das figurinhas em 3 colunas.
-   **Seleção Múltipla:** Toque longo ativa o modo de seleção para excluir várias figurinhas de uma vez.
-   **Feedback Visual:** Figurinhas selecionadas recebem overlay e ícone de check.
-   **Edição de Metadados:** Permite editar nome, autor e links do pacote.

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
    -   Secundária: `#03DAC6` (Teal)
-   **Componentes:** Uso extensivo de `Card` com `RoundedCornerShape(16.dp)` e `Elevation`.

## 5. Pontos de Atenção Recentes
-   **Correção de Navegação:** Resolvido loop infinito ao voltar da tela de salvamento para edição.
-   **Correção de Layout:** Removido fundo cinza das miniaturas para suportar transparência corretamente.
-   **Otimização de Visibilidade:** Ajustada a opacidade dos ícones de ação sobre imagens.

## 6. Próximos Passos Sugeridos
-   Testes finais de fluxo completo (Instalação limpa -> Criar -> Backup -> Restaurar).
-   Preparação para publicação (Assinatura, Ícone, Screenshots).
-   Implementação de filtros de imagem ou bordas automáticas.

---
*Gerado automaticamente pela IA Assistente em 27/05/2024.*
