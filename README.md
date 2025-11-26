# PureSticker - WhatsApp Sticker Maker (v2.0)

Bem-vindo à documentação do **PureSticker**, um aplicativo Android profissional para criar figurinhas estáticas e animadas para o WhatsApp. Este projeto utiliza as tecnologias mais recentes do ecossistema Android, incluindo Jetpack Compose, Kotlin Coroutines, Hilt, Room e AndroidX Media3.

---

## 📱 Visão Geral

O PureSticker permite aos usuários importar imagens e vídeos, editá-los com ferramentas avançadas (corte, texto, remoção de fundo) e organizá-los em pacotes para exportação direta ao WhatsApp.

### Destaques
*   **Suporte Híbrido:** Criação de pacotes **Estáticos** (Imagens) e **Animados** (Vídeos/GIFs).
*   **Edição Profissional:** 
    *   **Imagens:** Workspace livre com pan/zoom, remoção de fundo (ML Kit), adição de texto e alinhamento magnético (Snap).
    *   **Vídeo:** Linha do tempo para corte (Trim), recorte espacial (Crop), adição de texto sobre vídeo e conversão otimizada para WebP.
*   **Undo/Redo:** Sistema completo de desfazer/refazer para todas as edições.
*   **Design Moderno:** Interface Dark Mode (`#121212`), transições suaves e feedback visual rico.
*   **Backup & Restore:** Sistema de backup completo dos pacotes em arquivo ZIP com pré-visualização.
*   **Internacionalização:** Suporte completo para Inglês 🇺🇸 e Português 🇧🇷.

---

## 🛠️ Stack Tecnológica

| Categoria | Tecnologias |
| :--- | :--- |
| **Linguagem** | Kotlin 2.0 |
| **UI Toolkit** | Jetpack Compose (Material3) |
| **Arquitetura** | MVVM + Clean Architecture (Simplificada) |
| **Injeção de Dep.** | Hilt (Dagger) |
| **Banco de Dados** | Room (SQLite) |
| **Navegação** | Jetpack Navigation Compose |
| **Mídia & Imagem** | Coil (Imagem/GIF/Vídeo), Android-Image-Cropper |
| **Processamento de Vídeo** | **AndroidX Media3 Transformer** (Conversão/Edição), **Media3/ExoPlayer** (Playback) |
| **Machine Learning** | ML Kit (Selfie Segmentation para remoção de fundo) |
| **Assincronismo** | Coroutines & Flow |

---

## 🚀 Funcionalidades Detalhadas

### 1. Tela Inicial (`HomeScreen`)
*   Listagem de pacotes com prévia inteligente (grid + contador "+N").
*   Diferenciação visual entre pacotes estáticos e animados (Badge "ANIM").
*   Atalho rápido para "Adicionar ao WhatsApp" e compartilhamento.

### 2. Criação & Tipagem (`StickerTypeSelection` & `CreatePackage`)
*   Fluxo claro de seleção: Estático ou Animado.
*   Validação rigorosa: Impede misturar stickers animados em pacotes estáticos (e vice-versa).
*   Criação de pacotes com metadados completos (Autor, Site, Licença).

### 3. Editor de Imagem (`EditorScreen`)
*   **WYSIWYG:** O que você vê é o que é salvo.
*   **Ferramentas:** Texto (com fontes variadas), Ímã (Snap-to-grid), Remoção de Fundo.
*   **Gestos:** Manipulação livre com dois dedos (Zoom/Rotate/Pan).

### 4. Editor de Vídeo (`VideoEditorScreen`)
*   **Engine:** Processamento via **Media3 Transformer** para garantir performance e estabilidade.
*   **Trim:** Slider para cortar a duração do vídeo.
*   **Crop & Scale:** Ajuste do vídeo dentro do canvas quadrado (512x512).
*   **Texto:** Sobreposição de texto renderizada sobre o vídeo.
*   **Conversão:** Exportação otimizada para WebP (Compatibilidade WhatsApp).

### 5. Configurações (`SettingsScreen`)
*   **Backup:** Exportação/Importação de pacotes via ZIP.
*   **Restore Preview:** Visualização seletiva de pacotes antes da restauração.
*   **Limpeza:** Remoção de arquivos "órfãos" para liberar espaço.
*   **Permissões:** Tratamento robusto de permissões de mídia (Android 13+).

---

## 📂 Estrutura do Projeto

O projeto segue uma estrutura modular por features dentro do pacote principal:

```
com.example.wppsticker
├── data             # Repositories, Room DAO, Models
│   ├── local        # Entidades do BD
│   └── repository   # Implementações
├── di               # Módulos Hilt (AppModule, CoilModule)
├── domain           # UseCases e Interfaces de Repository
├── nav              # NavGraph e Definição de Rotas
├── provider         # ContentProvider para o WhatsApp
├── ui               # Telas (Composables) e ViewModels
│   ├── editor       # Editor Estático
│   ├── videoeditor  # Editor Animado
│   ├── home         # Tela Inicial
│   ├── stickerpack  # Detalhes e Salvamento
│   └── ...
└── util             # Extensions e Classes Utilitárias
```

---

## ⚙️ Configuração e Build

1.  **Requisitos:** Android Studio Koala ou superior (suporte a Kotlin 2.0).
2.  **Dependências Chave:**
    *   O projeto usa `libs.versions.toml` (Version Catalogs) para gerenciamento de versões.
    *   Certifique-se de sincronizar o Gradle para baixar as bibliotecas do Media3 e Coil.
3.  **Execução:**
    *   Conecte um dispositivo físico (recomendado para testes de vídeo/câmera).
    *   Execute o comando `./gradlew installDebug` ou use o botão "Run" do Android Studio.

---

## ✅ Status de Desenvolvimento

*   [x] Infraestrutura Base (Room, Hilt, Nav)
*   [x] CRUD de Pacotes e Stickers
*   [x] Integração com WhatsApp (ContentProvider)
*   [x] Editor de Imagem Completo (Undo/Redo/Snap)
*   [x] Editor de Vídeo (Media3 Pipeline)
*   [x] Suporte a GIF/WebP Animado na UI (Coil)
*   [x] Internacionalização (EN/PT)
*   [x] Backup e Restauração (Com Preview)

---

*Documentação atualizada em Maio/2024.*
