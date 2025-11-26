# PureSticker - WhatsApp Sticker Maker (v2.5 - Pro Edition)

Bem-vindo à documentação do **PureSticker**, um aplicativo Android profissional para criar figurinhas estáticas e animadas para o WhatsApp. Este projeto atingiu um alto nível de maturidade visual e técnica, utilizando as tecnologias mais recentes do ecossistema Android.

---

## 📱 Visão Geral

O PureSticker permite aos usuários importar imagens e vídeos, editá-los com ferramentas avançadas (corte, texto, remoção de fundo) e organizá-los em pacotes para exportação direta ao WhatsApp. O foco recente foi em **consistência de design**, **experiência de usuário (UX)** e **performance**.

### Destaques Principais
*   **Suporte Híbrido:** Criação de pacotes **Estáticos** (Imagens) e **Animados** (Vídeos/GIFs) com validação rigorosa.
*   **Edição Profissional (Paridade de Recursos):**
    *   **Imagens:** Workspace livre com pan/zoom, remoção de fundo (ML Kit), adição de texto, exclusão de elementos e alinhamento magnético (Snap).
    *   **Vídeo:** Linha do tempo para corte (Trim), recorte espacial (Crop), adição de texto sobre vídeo e conversão otimizada para WebP via **Media3 Transformer**.
*   **Undo/Redo Robusto:** Sistema completo de desfazer/refazer para todas as edições (texto, transformações, cores).
*   **Design System Coeso:** Interface "Pro Dark" (`#121212`), padronização de bordas (12dp), cores semânticas e componentes Material 3 personalizados.
*   **Backup & Restore:** Sistema de backup completo dos pacotes em arquivo ZIP com pré-visualização seletiva.
*   **UX Polida:** Tratamento de "Safe Areas" (navegação por gestos), foco automático em campos de texto, feedback tátil (Ripple) refinado e validações em tempo real.

---

## 🛠️ Stack Tecnológica

| Categoria | Tecnologias |
| :--- | :--- |
| **Linguagem** | Kotlin 2.0 |
| **UI Toolkit** | Jetpack Compose (Material3) |
| **Arquitetura** | MVVM + Clean Architecture (Simplificada) |
| **Injeção de Dep.** | Hilt (Dagger) |
| **Banco de Dados** | Room (SQLite) |
| **Navegação** | Jetpack Navigation Compose (Type-safe args) |
| **Mídia & Imagem** | Coil (Imagem/GIF/Vídeo/Cache) |
| **Processamento de Vídeo** | **AndroidX Media3 Transformer** (Conversão/Edição) & **ExoPlayer** |
| **Machine Learning** | ML Kit (Selfie Segmentation para remoção de fundo) |
| **Assincronismo** | Coroutines & Flow |

---

## 🚀 Funcionalidades Detalhadas

### 1. Tela Inicial (`HomeScreen`)
*   Listagem de pacotes com prévia inteligente (grid + contador "+N").
*   **Empty States** ricos com iconografia consistente.
*   Diferenciação visual entre pacotes estáticos e animados (Badge "ANIM").
*   Atalho rápido para "Adicionar ao WhatsApp" com ícone oficial da marca.

### 2. Criação & Tipagem (`StickerTypeSelection` & `CreatePackage`)
*   Fluxo claro de seleção: Estático ou Animado.
*   Diálogos de criação com **Foco Automático** e suporte a teclado (ImeAction).
*   Validação de metadados (Autor, Site, Licença) com feedback visual.

### 3. Editor de Imagem (`EditorScreen`)
*   **WYSIWYG:** O que você vê é o que é salvo.
*   **Ferramentas:** Texto (fontes, cores, redimensionamento, exclusão), Ímã (Snap-to-grid), Remoção de Fundo.
*   **Gestos:** Manipulação livre com dois dedos (Zoom/Rotate/Pan) com limites visuais (Guide Border).
*   **Visual:** Fundo Checkerboard para indicar transparência.

### 4. Editor de Vídeo (`VideoEditorScreen`)
*   **Engine:** Processamento via **Media3 Transformer**.
*   **Trim:** Slider preciso para cortar a duração do vídeo com geração de thumbnails.
*   **Crop & Scale:** Ajuste do vídeo dentro do canvas quadrado (512x512) com as mesmas guias do editor de imagem.
*   **Texto:** Sobreposição de texto renderizada sobre o vídeo final.
*   **Conversão:** Exportação otimizada para WebP (Compatibilidade WhatsApp).

### 5. Configurações (`SettingsScreen`)
*   **Backup:** Exportação/Importação de pacotes via ZIP.
*   **Restore Preview:** Visualização seletiva de pacotes antes da restauração com detecção de duplicatas.
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
├── di               # Módulos Hilt (AppModule)
├── nav              # NavGraph e Definição de Rotas
├── provider         # ContentProvider para o WhatsApp
├── ui               # Telas (Composables) e ViewModels
│   ├── components   # UI Reutilizável (Cards, Dialogs, EmptyStates)
│   ├── editor       # Editor Estático & Lógica Compartilhada
│   ├── videoeditor  # Editor Animado
│   ├── home         # Tela Inicial
│   ├── stickerpack  # Detalhes e Salvamento
│   ├── theme        # Sistema de Design (Color, Type, Theme)
│   └── util         # Helpers de UI
└── util             # Extensions e Classes Utilitárias
```

---

## ⚙️ Configuração e Build

1.  **Requisitos:** Android Studio (Versão recente com suporte a Kotlin 2.0).
2.  **Dependências Chave:**
    *   O projeto usa `libs.versions.toml` (Version Catalogs) para gerenciamento de versões.
    *   Sincronize o Gradle para baixar as bibliotecas do Media3, Coil e ML Kit.
3.  **Execução:**
    *   Conecte um dispositivo físico (recomendado para testes de vídeo/câmera).
    *   Execute o comando `./gradlew installDebug` ou use o botão "Run" do Android Studio.

---

## ✅ Status de Desenvolvimento

*   [x] Infraestrutura Base (Room, Hilt, Nav)
*   [x] CRUD de Pacotes e Stickers
*   [x] Integração com WhatsApp (ContentProvider)
*   [x] Editor de Imagem Completo (Undo/Redo/Snap/Delete)
*   [x] Editor de Vídeo Completo (Media3 Pipeline + Text Overlay)
*   [x] Suporte a GIF/WebP Animado na UI (Coil)
*   [x] Internacionalização (EN/PT)
*   [x] Backup e Restauração (Com Preview)
*   [x] **Polimento Visual e UX Finalizado**

---

*Documentação atualizada.*
