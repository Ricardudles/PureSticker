# Prompt de Contexto do Projeto: WppSticker - App de Figurinhas para WhatsApp

## 1. Contexto e Objetivo do Projeto

Você é um agente de IA especialista em desenvolvimento Android nativo. Sua missão é continuar o desenvolvimento de um aplicativo chamado **WppSticker**, cujo objetivo é permitir que usuários criem, editem e gerenciem pacotes de figurinhas (stickers) para o WhatsApp. O aplicativo já passou por várias fases de desenvolvimento e refatoração, e agora se encontra em um estado funcional e polido. O fluxo principal do usuário foi redefinido para ser centrado na criação da figurinha.

## 2. Tecnologias e Dependências Principais

O projeto utiliza um stack moderno de Android com as seguintes tecnologias e versões:

- **Linguagem:** Kotlin
- **UI:** Jetpack Compose
- **Arquitetura:** MVVM com princípios de Clean Architecture
- **Navegação:** Navigation Compose (`androidx.navigation:navigation-compose:2.7.7`)
- **Injeção de Dependência:** Hilt (`com.google.dagger:hilt-android:2.51.1`, `androidx.hilt:hilt-navigation-compose:1.2.0`)
- **Banco de Dados:** Room (`androidx.room:room-runtime:2.6.1`, `androidx.room:room-ktx:2.6.1`)
- **Imagens:** Coil (`io.coil-kt:coil-compose:2.6.0`)
- **Permissões:** Accompanist Permissions (`com.google.accompanist:accompanist-permissions:0.34.0`)
- **SDK:** `compileSdk = 36`, `minSdk = 26`

## 3. Arquitetura do Projeto

O código-fonte está organizado em `app/src/main/java/com/example/wppsticker/` com a seguinte estrutura de pacotes:

- `data/`: Contém a implementação do repositório e a camada de acesso a dados locais (Room).
  - `local/`: Entidades do Room, DAOs, `AppDatabase` e `TypeConverters`.
  - `repository/`: Implementação (`StickerRepositoryImpl`) da interface do repositório.
- `di/`: Módulos do Hilt para prover dependências (banco de dados, repositórios).
- `domain/`: Contém a lógica de negócio e as abstrações.
  - `model/`: (Atualmente vazio, mas destinado a modelos de domínio se necessário).
  - `repository/`: Interface do repositório (`StickerRepository`).
  - `usecase/`: Casos de uso para cada ação de negócio (ex: `GetStickerPackagesUseCase`, `AddStickerUseCase`).
- `nav/`: Lógica de navegação, incluindo o `NavGraph` e o enum `Screen`.
- `provider/`: O `StickerContentProvider` para integração com o WhatsApp.
- `ui/`: Contém os Composables (telas) e seus respectivos ViewModels.
  - `editor/`: `EditorScreen` e `EditorViewModel`.
  - `home/`: `HomeScreen` e `HomeViewModel`.
  - `stickerpack/`: `PackageScreen`, `SaveStickerScreen` e seus ViewModels.
- `util/`: Classes utilitárias, como `ImageHelper` e `UiState`.

## 4. Funcionalidades Implementadas e Fluxo Principal

O fluxo de trabalho principal foi **refatorado** para ser centrado na criação da figurinha:

1.  **HomeScreen:**
    - Exibe uma lista de pacotes de figurinhas existentes usando `UiState` para gerenciar os estados de `Loading`, `Success` e `Empty`.
    - Um **FloatingActionButton (+)** inicia o fluxo de criação de uma nova figurinha.
    - Cada item da lista tem um botão para **excluir** o pacote e um botão para **enviar** ao WhatsApp (que só funciona se o pacote tiver no mínimo 3 figurinhas e um ícone de bandeja).

2.  **Fluxo de Criação de Figurinha (Iniciado pelo FAB):**
    - O usuário seleciona uma imagem da galeria (as permissões de armazenamento são solicitadas).
    - Navega para a **EditorScreen**, passando o URI da imagem.

3.  **EditorScreen:**
    - Exibe a imagem selecionada.
    - Permite **manipulação por gestos**: pan, zoom e rotação da imagem principal.
    - **Modo de Corte:** Permite ao usuário definir uma área de corte retangular com alças ajustáveis.
    - **Adição de Texto:** Permite adicionar múltiplos elementos de texto. O texto selecionado pode ser movido, redimensionado e rotacionado. Um seletor de cores é exibido para o texto selecionado.
    - A UI fornece **feedback visual** (borda) para o texto e a cor selecionados.
    - Ao clicar em "Continuar" (`Check` icon), a imagem editada (com corte e textos aplicados) é renderizada em um bitmap de 512x512, salva em um **arquivo temporário** e seu URI é passado para a próxima tela.

4.  **SaveStickerScreen (Nova Tela de Finalização):**
    - Recebe o URI da figurinha finalizada e a exibe.
    - O usuário insere os **emojis** associados.
    - Um **dropdown (`ExposedDropdownMenuBox`)** exibe os pacotes de figurinhas existentes.
    - O usuário pode selecionar um pacote existente ou clicar em "+ Novo Pacote" para abrir um diálogo e criar um novo pacote na hora.
    - Ao clicar em "Salvar", a `SaveStickerViewModel` move o arquivo temporário para o armazenamento permanente, salva a figurinha no banco de dados associada ao pacote escolhido, e define a figurinha como o **ícone da bandeja (redimensionado para 96x96)** se o pacote ainda não tiver um.
    - Após salvar, navega de volta para a `HomeScreen`.

5.  **Integração com WhatsApp:**
    - O `StickerContentProvider` está configurado para responder ao WhatsApp, servindo os metadados dos pacotes e os arquivos de imagem (figurinhas e ícones) a partir do diretório interno do aplicativo.
    - A `Intent` de envio é construída corretamente no `HomeViewModel`, especificando o `sticker_pack_id` e o `sticker_pack_authority`.

## 5. Pontos Críticos de Implementação (Estado Atual)

- **Fluxo Refatorado:** Esteja ciente de que o fluxo foi alterado significativamente. A criação de pacotes agora é um sub-produto do fluxo de salvamento de uma figurinha, o que é mais intuitivo.
- **Exportação da Imagem:** A lógica de renderização em `EditorViewModel` é complexa e crucial. Ela usa um `Canvas` nativo para desenhar o `Bitmap` final, aplicando `clipRect` para o corte e replicando as transformações (`translate`, `scale`, `rotate`) do `graphicsLayer` do Compose para garantir que a saída seja fiel à visualização.
- **Gerenciamento de Arquivos:** Os ícones de bandeja são redimensionados para 96x96. Os caminhos salvos no banco de dados são **apenas os nomes dos arquivos**, não os caminhos absolutos, pois o `ContentProvider` opera a partir do diretório `context.filesDir`.
- **Gerenciamento de Estado da UI:** As telas principais usam um wrapper `UiState` para gerenciar os estados de carregamento e vazio, proporcionando uma experiência de usuário mais limpa.

Sua tarefa é entender este contexto e continuar a desenvolver, polir ou corrigir o aplicativo conforme solicitado.