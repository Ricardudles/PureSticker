# Plano de Melhorias do App de Figurinhas (Sticker App)

Este documento lista as melhorias planejadas e sugestões para evoluir o aplicativo, focando em experiência do usuário (UX), novas funcionalidades e robustez técnica.

## 1. Experiência do Usuário (UX) e Interface (UI)
- [x] **Edição de Pacotes Existentes:** Melhorar o fluxo para editar nome, autor e outros metadados de um pacote já criado.
- [x] **Visualização de Pacotes:** Modernizar a tela inicial com cards que mostram o ícone da bandeja e uma prévia das figurinhas.
- [x] **Feedback de Carregamento:** Melhorar os indicadores de progresso durante a compressão de imagem (que pode demorar em aparelhos mais antigos).
- [x] **Fluxo Inteligente:** Pré-seleção automática do pacote ao iniciar criação de dentro de um pack.
- [x] **Segurança no Editor:** Botão de voltar com diálogo de confirmação para evitar perda de trabalho acidental (substituindo título estático).

## 2. Editor de Imagem Avançado
- [x] **Recorte (Crop) e Rotação:** Integrar uma biblioteca (como uCrop/CanHub) para permitir recortar a imagem antes de salvar. Isso ajuda a focar no objeto principal.
- [x] **Adicionar Texto e Desenho:** Permitir que o usuário escreva textos ou desenhe sobre a imagem antes de transformá-la em figurinha.
- [ ] **Remoção de Fundo:** (Avançado) Integrar uma API ou biblioteca local (ML Kit) para tentar remover o fundo da imagem automaticamente.

## 3. Funcionalidades Avançadas
- [ ] **Figurinhas Animadas:** Adicionar suporte para importar GIFs ou WebPs animados. (Requer validação extra: < 500KB, duração < 10s).
- [x] **Seletor de Emojis Visual:** Substituir o campo de texto de emojis por um "Grid" de emojis clicáveis, facilitando a seleção sem depender do teclado do usuário.

## 4. Melhorias Técnicas e Performance
- [ ] **Processamento em Background:** Usar `WorkManager` para processar a compressão de imagens, evitando travar a UI se o usuário adicionar muitas figurinhas seguidas.
- [x] **Cache e Otimização:** Verificar se o Coil/Glide está limpando o cache adequadamente para não lotar a memória do dispositivo.
- [ ] **Testes Automatizados:** Criar testes unitários para os ViewModels e testes de UI para garantir que as validações (tamanho, limites) nunca parem de funcionar.

## 5. Integração e Compatibilidade
- [x] **Exportar/Importar Backup:** Permitir salvar os pacotes em um arquivo `.zip` (ou formato próprio) para o usuário não perder as figurinhas se trocar de celular.

## 6. Validações Extras (Segurança)
- [x] **Verificação de Duplicidade:** Avisar para o usuário que ele está adicionando exatamente a mesma imagem duas vezes no mesmo pacote e se ele quer prosseguir com a ação.
- [x] **Limpeza de Arquivos Órfãos:** Criar uma rotina que verifica se existem arquivos de imagem na pasta do app que não estão vinculados a nenhum pacote no banco de dados (lixo) e apagá-los.

## 7. Branding e Localização
- [x] **Identidade Visual:** Renomeação do app para **PureSticker** (foco em simplicidade "No BS").
- [x] **Internacionalização (i18n):** Refatoração completa para uso de `strings.xml`, com suporte nativo a Inglês (padrão) e Português (PT-BR).
