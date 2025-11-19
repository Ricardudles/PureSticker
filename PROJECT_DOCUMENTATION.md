# Documentação Técnica: WppSticker

**Versão:** 1.0 (Pós-Refatoração Final)

## 1. Visão Geral

O WppSticker é um aplicativo Android nativo, desenvolvido em Kotlin e Jetpack Compose, que permite aos usuários criar, editar e gerenciar pacotes de figurinhas (stickers) para o WhatsApp. O projeto segue uma arquitetura moderna e limpa (MVVM+) e foi projetado para fornecer um fluxo de usuário intuitivo, desde a seleção de uma imagem até a exportação final e envio para o WhatsApp.

## 2. Estrutura do Projeto e Arquitetura

O aplicativo utiliza uma variação da Clean Architecture, separando as responsabilidades em três camadas principais: `data`, `domain`, e `ui`.

### Diagrama de Arquitetura (Fluxo de Dados)

```
   [UI Layer (Compose Screens)]
             |
             v
[ViewModels (Android ViewModel)]
             |
             v
   [Use Cases (Domain Layer)]
             |
             v
 [Repository Interface (Domain Layer)]
             |
             v
[Repository Implementation (Data Layer)]
             |
             v
   [Room DAO (Data Layer)]
             |
             v
      [SQLite Database]
```

### Detalhamento dos Pacotes

- **`data/`**: Camada de dados. É a fonte da verdade para todos os dados do aplicativo.
  - `local/`: Contém as definições do Room, incluindo:
    - `Sticker.kt`, `StickerPackage.kt`: Entidades do banco de dados.
    - `StickerDao.kt`: Data Access Object que define as operações CRUD.
    - `AppDatabase.kt`: A classe principal do banco de dados Room.
  - `repository/`: Implementação concreta do repositório.
    - `StickerRepositoryImpl.kt`: Implementa a interface `StickerRepository`, usando o `StickerDao` para executar as operações.

- **`domain/`**: Camada de domínio (ou negócio). Não tem dependências do Android.
  - `repository/`: Define as abstrações (interfaces) para os repositórios. `StickerRepository.kt`.
  - `usecase/`: Classes que encapsulam uma única ação de negócio. Exemplos: `AddStickerUseCase`, `GetStickerPackagesUseCase`.

- **`ui/`**: Camada de UI. Contém todas as telas (Composables) e seus ViewModels.
  - `home/`: Tela principal que lista os pacotes (`HomeScreen`).
  - `stickerpack/`: Telas relacionadas ao gerenciamento de pacotes e salvamento de figurinhas (`PackageScreen`, `SaveStickerScreen`).
  - `editor/`: A tela de edição de imagens (`EditorScreen`).

- **`di/`**: Módulos do Hilt para injeção de dependência.
  - `DatabaseModule.kt`: Prova o `AppDatabase` e o `StickerDao`.
  - `RepositoryModule.kt`: Prova a implementação `StickerRepositoryImpl` para a interface `StickerRepository`.

- **`nav/`**: Orquestra a navegação com o Navigation Compose (`NavGraph.kt`, `Screen.kt`).

- **`provider/`**: Contém o `StickerContentProvider`, essencial para a integração com o WhatsApp.

- **`util/`**: Classes utilitárias reutilizáveis.
  - `UiState.kt`: Sealed class para gerenciamento de estado da UI (Loading, Success, Empty).
  - `ImageHelper.kt`: Funções auxiliares para manipulação de bitmaps (ex: redimensionamento).

## 3. Funcionalidades Detalhadas

### 3.1. Fluxo Principal: Criação de Figurinha

O fluxo de trabalho foi projetado para ser centrado no usuário, começando com a intenção de criar uma figurinha.

1.  **Início (`HomeScreen`):** O usuário clica no FloatingActionButton (+).
2.  **Seleção:** O seletor de imagens da galeria é aberto.
3.  **Edição (`EditorScreen`):** A imagem selecionada é carregada na tela de edição.
    - **Manipulação de Gestos:** A imagem de fundo pode ser movida (pan), redimensionada (zoom) e rotacionada com gestos `detectTransformGestures`.
    - **Adição de Texto:** Múltiplos textos podem ser adicionados. Cada texto pode ser selecionado e manipulado individualmente. A UI fornece feedback visual (borda amarela) para o texto selecionado.
    - **Seletor de Cores:** Quando um texto é selecionado, um seletor de cores aparece, indicando a cor atual e permitindo a troca.
    - **Corte:** O usuário pode ativar um modo de corte que exibe um retângulo com alças ajustáveis.
4.  **Finalização (`SaveStickerScreen`):**
    - Após a edição, o bitmap final (512x512) é gerado e passado para esta tela.
    - O usuário pode atribuir emojis e escolher um pacote existente em um dropdown ou criar um novo pacote através de um diálogo.
    - Ao salvar, a `SaveStickerViewModel` move o arquivo para o armazenamento permanente e atualiza o banco de dados.

### 3.2. Integração com WhatsApp

Esta é a funcionalidade mais complexa e crucial.

- **`StickerContentProvider`:** Atua como a ponte segura entre nosso aplicativo e o WhatsApp. Ele é registrado no `AndroidManifest.xml` com uma autoridade única (`${applicationId}.provider`).
- **Método `query()`:** Não é utilizado no fluxo de envio, portanto, foi simplificado para lançar uma exceção. O WhatsApp obtém as informações necessárias através de `extras` na `Intent`.
- **Método `openFile()`:** É o responsável por servir os arquivos de imagem (PNG). Ele recebe uma URI do WhatsApp, extrai o nome do arquivo e o localiza no diretório interno do aplicativo (`context.filesDir`).
- **`Intent` de Envio:** No `HomeViewModel`, a função `sendStickerPack` constrói a `Intent` `com.whatsapp.intent.action.ENABLE_STICKER_PACK`. Os `extras` mais importantes são:
  - `sticker_pack_id`: O ID único do pacote.
  - `sticker_pack_authority`: A autoridade do nosso `ContentProvider`.
  - `sticker_pack_name`: O nome do pacote.

## 4. Gerenciamento de Estado

As telas que exibem dados assíncronos (`HomeScreen` e `PackageScreen`) usam uma `sealed class UiState<T>` para modelar o estado da UI. Isso permite que a UI reaja de forma declarativa, exibindo:

- Um `CircularProgressIndicator` durante o `UiState.Loading`.
- A lista de dados no `UiState.Success`.
- Uma mensagem informativa durante o `UiState.Empty`.

## 5. Pontos a Melhorar (O que falta fazer)

Embora o aplicativo esteja funcional, há várias áreas para melhorias futuras e para elevar ainda mais a qualidade:

- **Testes:** O projeto não possui uma suíte de testes. Seria crucial adicionar:
  - **Testes Unitários:** Para ViewModels, UseCases e Repositórios, validando a lógica de negócio e o fluxo de dados.
  - **Testes de UI (Compose):** Para verificar se as telas reagem corretamente às mudanças de estado.
  - **Testes de Instrumentação:** Para validar a lógica do banco de dados (DAO) e do `ContentProvider`.

- **Editor de Imagem Avançado:**
  - **Fontes de Texto:** Permitir que o usuário escolha entre diferentes fontes para o texto.
  - **Seleção de Cor:** Substituir a lista de cores pré-definidas por um seletor de cores completo (color picker).
  - **Desfazer/Refazer:** Implementar uma pilha de ações para permitir que o usuário desfaça e refaça operações de edição.

- **Performance:**
  - A manipulação de bitmaps na UI thread, especialmente durante a exportação, pode causar congelamentos. A renderização do `Bitmap` final deveria ser movida para uma corrotina em um dispatcher de background (`Dispatchers.Default`).

- **UX e Polimento Visual:**
  - **Ícone da Bandeja:** Permitir que o usuário escolha qualquer figurinha do pacote para ser o ícone da bandeja, não apenas a primeira.
  - **Animações:** Adicionar transições de tela e animações de UI para uma experiência mais fluida.

## 6. Guia de Build e Setup

1.  Clone o repositório.
2.  Abra o projeto no Android Studio.
3.  Aguarde o Gradle Sync ser concluído. As dependências estão listadas nos arquivos `build.gradle.kts`.
4.  Compile e execute o aplicativo em um emulador ou dispositivo físico.
5.  **Requisito:** Para testar a funcionalidade de envio, o WhatsApp (versão de consumidor ou Business) precisa estar instalado no dispositivo de teste.