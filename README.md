# PureSticker

Aplicativo Android nativo para criação e gerenciamento de pacotes de figurinhas (stickers) para WhatsApp. Desenvolvido em Kotlin com Jetpack Compose.

## Funcionalidades Atuais

*   **Criação de Pacotes:** Suporte para pacotes do tipo Estático (imagens) e Animado (vídeos/GIFs).
*   **Integração WhatsApp:** Exportação direta dos pacotes para o aplicativo do WhatsApp via ContentProvider.
*   **Editor de Imagens:**
    *   Adição e manipulação de textos.
    *   Remoção de fundo automática (Google ML Kit).
    *   Ferramentas de corte e alinhamento (snap-to-grid).
*   **Editor de Vídeo:**
    *   Corte temporal (Trim) e espacial (Crop).
    *   Conversão e compressão de vídeo para formato WebP (compatível com WhatsApp) usando Media3 Transformer.
*   **Armazenamento Local:** Todo o gerenciamento de dados é feito localmente usando Room Database.
*   **Backup e Restauração:** Exportação e importação de pacotes completos via arquivos ZIP.
*   **Limpeza:** Ferramenta para identificar e remover arquivos de mídia não utilizados no armazenamento interno.

## Stack Tecnológica

*   **Linguagem:** Kotlin 2.0
*   **UI:** Jetpack Compose (Material3)
*   **Arquitetura:** MVVM
*   **Injeção de Dependência:** Hilt
*   **Persistência:** Room Database
*   **Processamento de Mídia:** AndroidX Media3 (Transformer) & ExoPlayer
*   **Processamento de Imagem:** ML Kit (Selfie Segmentation) & Coil

## Estrutura do Projeto

```
com.example.wppsticker
├── data             # Definições de banco de dados (Room) e Modelos
├── di               # Configuração de injeção de dependência
├── nav              # Gráfico de navegação do Compose
├── provider         # ContentProvider exposto para o WhatsApp
├── ui               # Telas e ViewModels
│   ├── editor       # Lógica de edição de imagens estáticas
│   ├── videoeditor  # Lógica de processamento de vídeo
│   ├── home         # Listagem de pacotes
│   ├── settings     # Configurações e Backup
│   └── stickerpack  # Visualização e gerenciamento de stickers
└── util             # Classes utilitárias
```

## Privacidade e Permissões

O aplicativo opera offline.
*   **Permissões:** Utiliza `READ_MEDIA_IMAGES` e `READ_MEDIA_VIDEO` para seleção de arquivos.
*   **Integração:** Verifica a instalação do WhatsApp através de queries específicas no manifesto (`com.whatsapp`), sem uso da permissão ampla `QUERY_ALL_PACKAGES`.

## Execução

Requer Android Studio com suporte a Kotlin 2.0. As dependências de bibliotecas (Media3, ML Kit) são baixadas automaticamente via Gradle ao sincronizar o projeto.
