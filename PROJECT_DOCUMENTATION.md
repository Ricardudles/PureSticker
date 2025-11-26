# PureSticker - Documentação Técnica Completa

Bem-vindo à documentação técnica unificada do **PureSticker**. Este documento serve como guia definitivo sobre a arquitetura, fluxos de dados, decisões técnicas e estrutura do projeto.

---

## 📱 Visão Geral do Produto

O PureSticker é um aplicativo Android nativo desenvolvido para criação e gerenciamento de pacotes de figurinhas para o WhatsApp. Ele suporta tanto figurinhas estáticas (imagens) quanto animadas (WebP/GIF), com ferramentas de edição integradas.

### Principais Funcionalidades
1.  **Gerenciamento de Pacotes:** Criação, edição e exclusão de pacotes de figurinhas.
2.  **Editor de Imagens:** Ferramentas para adicionar texto, cortar, remover fundo e posicionar elementos com sistema Undo/Redo.
3.  **Editor de Vídeo:** Pipeline de conversão e edição de vídeos para o formato WebP animado compatível com WhatsApp, utilizando Media3.
4.  **Integração com WhatsApp:** Uso de ContentProvider para exportar pacotes diretamente para o aplicativo de mensagens.
5.  **Backup e Restauração:** Persistência externa de pacotes via arquivos ZIP com pré-visualização seletiva.
6.  **Internacionalização:** Suporte completo a Inglês (en) e Português (pt-BR).

---

## 🏗️ Arquitetura e Stack Tecnológica

O projeto segue os princípios de **Modern Android Development (MAD)** e **Clean Architecture** simplificada.

| Camada | Tecnologias Principais | Descrição |
| :--- | :--- | :--- |
| **UI (Presentation)** | Jetpack Compose (Material3), Navigation Compose, ViewModels | Interface declarativa reativa, tema Material You. |
| **Domain** | Kotlin UseCases, Models | Regras de negócio puras, agnósticas de framework UI. |
| **Data** | Room, DataStore, File System, ContentProviders | Persistência local, acesso a arquivos e integração com outros apps. |
| **DI** | Hilt (Dagger) | Injeção de dependência para desacoplamento. |
| **Processamento** | Coroutines, Media3 Transformer, Coil | Processamento assíncrono e manipulação de mídia. |

### Estrutura de Diretórios (`com.example.wppsticker`)

*   `data`: Implementação de repositórios, fontes de dados (Room) e modelos de dados (Entities).
    *   `local`: Definições do Room (`Sticker`, `StickerPackage`, `StickerDao`, `AppDatabase`).
*   `di`: Módulos Hilt para injeção de dependências (`AppModule`, `DatabaseModule`).
*   `domain`: Contratos de repositório (`StickerRepository`) e Casos de Uso (`UseCases`).
*   `nav`: Definição do grafo de navegação (`NavGraph`, `Screen`).
*   `provider`: Implementação do `StickerContentProvider` exigido pelo WhatsApp.
*   `ui`: Telas (Composables) e seus respectivos ViewModels.
    *   `home`: Tela principal e listagem.
    *   `editor`: Editor de imagens estáticas.
    *   `videoeditor`: Editor de vídeos animados.
    *   `stickerpack`: Detalhes do pacote e fluxo de salvamento.
    *   `settings`: Configurações, backup e restauração.
*   `util`: Classes utilitárias, Extensions e Estados de UI (`UiState`).

---

## 🎨 Diretrizes de UI/UX

Para manter a consistência visual e de uso, o projeto segue padrões estritos:

1.  **Tema Escuro:** O app é otimizado para Dark Mode, com background `#121212` e cores de destaque primárias.
2.  **Padrão de Botões (Diálogos e Confirmações):**
    *   **Ação Positiva/Confirmação** (Ex: Save, Confirm, Delete, Add): Posicionada sempre à **DIREITA**.
    *   **Ação Negativa/Cancelamento** (Ex: Cancel, Back): Posicionada sempre à **ESQUERDA**.
    *   *Motivo:* Segue o padrão nativo do Android e Material Design 3.
3.  **Feedback Visual:** Todas as operações longas (salvamento, conversão de vídeo) devem exibir indicadores de progresso (Loading) e feedback final (Toast ou Overlay de Sucesso).

---

## 🔄 Fluxos de Usuário e Navegação

O aplicativo utiliza um grafo de navegação único (`NavGraph`) com as seguintes rotas principais:

1.  **Home (`Screen.Home`)**: Ponto de entrada. Lista pacotes existentes.
2.  **Seleção de Tipo (`Screen.StickerTypeSelection`)**: Escolha entre criar figurinha Estática ou Animada.
3.  **Editores**:
    *   `Screen.Editor`: Para imagens estáticas. Recebe URI da imagem.
    *   `Screen.VideoEditor`: Para vídeos. Recebe URI do vídeo. Processa e converte para WebP.
4.  **Salvar (`Screen.SaveSticker`)**: Tela de pré-visualização final, adição de emojis e seleção de pacote.
    *   *Fluxo Otimizado:* Se nenhum pacote for pré-selecionado, navega para `PackageSelection` para salvar e finalizar em um passo.
5.  **Seleção de Pacote (`Screen.PackageSelection`)**: Tela para escolher ou criar um pacote. Usada tanto para salvar stickers quanto para filtrar na Home.
6.  **Detalhes do Pacote (`Screen.StickerPack`)**: Visualização do conteúdo do pacote e envio para o WhatsApp.
7.  **Settings (`Screen.Settings`)**: Gerenciamento de backup e limpeza.
8.  **Restore Preview (`Screen.RestorePreview`)**: Seleção de pacotes a serem restaurados de um backup.

---

## 💾 Persistência e Dados

### Banco de Dados (Room)
O aplicativo utiliza duas tabelas principais com relação 1:N.
*   **StickerPackage**: `id`, `name`, `author`, `identifier` (UUID), `trayImageFile`, `animated` (bool).
*   **Sticker**: `id`, `packageId` (FK), `imageFile` (nome do arquivo interno), `emojis`.

### Armazenamento de Arquivos
*   Todas as imagens e vídeos processados são salvos no armazenamento interno do aplicativo (`context.filesDir`).
*   O `StickerContentProvider` expõe esses arquivos para o WhatsApp via `ParcelFileDescriptor` (modo `READ_ONLY`).
*   *Limpeza:* O sistema de "Clean Orphan Files" remove arquivos físicos que não possuem registro correspondente no banco de dados.

---

## 🎬 Processamento de Mídia

### Imagens Estáticas
*   Utiliza `BitmapFactory` e Canvas nativo para composição.
*   Recorte via `android-image-cropper`.
*   Compressão final para WebP (Lossy, 512x512px, < 100KB) para conformidade com WhatsApp.
*   **Undo/Redo:** Pilha de estados mantida em memória durante a edição.

### Figurinhas Animadas
*   **AndroidX Media3 Transformer** é o motor central (substituindo o antigo FFmpeg-Kit).
*   Pipeline de conversão:
    1.  Análise e Trim (Corte de tempo).
    2.  Crop e Scale (512x512).
    3.  Aplicação de efeitos (Texto).
    4.  Conversão para WebP Animado.
    5.  Controle de qualidade para garantir tamanho < 500KB (limite rígido do WhatsApp).

---

## 🌍 Integração com WhatsApp

A integração segue estritamente a documentação oficial de stickers do WhatsApp.
*   **ContentProvider:** `StickerContentProvider` responde a queries sobre metadados e serve os arquivos (streams).
*   **Intent:** Dispara `com.whatsapp.intent.action.ENABLE_STICKER_PACK` com permissões de leitura de URI (`FLAG_GRANT_READ_URI_PERMISSION`).
*   **Permissões:** O app concede permissões temporárias de leitura para `com.whatsapp` e `com.whatsapp.w4b` (Business) nas URIs específicas do provider.

---

*Documentação atualizada em Maio/2024.*
