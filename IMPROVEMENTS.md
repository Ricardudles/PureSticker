# Plano de Melhorias do App de Figurinhas (Sticker App)

Este documento lista as melhorias planejadas e sugestões para evoluir o aplicativo, focando em experiência do usuário (UX), novas funcionalidades e robustez técnica.

## 1. Experiência do Usuário (UX) e Interface (UI)
- [ ] **Reordenação de Figurinhas:** Permitir que o usuário arraste e solte (drag & drop) as figurinhas dentro de um pacote para mudar a ordem.
- [ ] **Edição de Pacotes Existentes:** Melhorar o fluxo para editar nome, autor e outros metadados de um pacote já criado.
- [ ] **Preview do Pacote:** Criar uma visualização de "como ficará no WhatsApp" antes de enviar.
- [ ] **Feedback de Carregamento:** Melhorar os indicadores de progresso durante a compressão de imagem (que pode demorar em aparelhos mais antigos).
- [ ] **Tutorial / Onboarding:** Adicionar um guia rápido na primeira abertura explicando os passos: Criar Pacote -> Adicionar Figurinhas -> Enviar.

## 2. Editor de Imagem Avançado
- [ ] **Recorte (Crop) e Rotação:** Integrar uma biblioteca (como uCrop) para permitir recortar a imagem antes de salvar. Isso ajuda a focar no objeto principal.
- [ ] **Adicionar Texto e Desenho:** Permitir que o usuário escreva textos ou desenhe sobre a imagem antes de transformá-la em figurinha.
- [ ] **Remoção de Fundo:** (Avançado) Integrar uma API ou biblioteca local (ML Kit) para tentar remover o fundo da imagem automaticamente.

## 3. Funcionalidades Avançadas
- [ ] **Figurinhas Animadas:** Adicionar suporte para importar GIFs ou WebPs animados. (Requer validação extra: < 500KB, duração < 10s).
- [ ] **Seletor de Emojis Visual:** Substituir o campo de texto de emojis por um "Grid" de emojis clicáveis, facilitando a seleção sem depender do teclado do usuário.
- [ ] **Importação em Lote:** Permitir selecionar múltiplas imagens da galeria de uma vez para criar várias figurinhas rapidamente.

## 4. Melhorias Técnicas e Performance
- [ ] **Processamento em Background:** Usar `WorkManager` para processar a compressão de imagens, evitando travar a UI se o usuário adicionar muitas figurinhas seguidas.
- [ ] **Cache e Otimização:** Verificar se o Coil/Glide está limpando o cache adequadamente para não lotar a memória do dispositivo.
- [ ] **Testes Automatizados:** Criar testes unitários para os ViewModels e testes de UI para garantir que as validações (tamanho, limites) nunca parem de funcionar.

## 5. Integração e Compatibilidade
- [ ] **Suporte Explícito ao WhatsApp Business:** Melhorar o diálogo de envio para perguntar ao usuário se ele quer enviar para o WhatsApp ou WhatsApp Business (caso tenha os dois).
- [ ] **Exportar/Importar Backup:** Permitir salvar os pacotes em um arquivo `.zip` (ou formato próprio) para o usuário não perder as figurinhas se trocar de celular.

## 6. Validações Extras (Segurança)
- [ ] **Verificação de Duplicidade:** Impedir que o usuário adicione exatamente a mesma imagem duas vezes no mesmo pacote.
- [ ] **Limpeza de Arquivos Órfãos:** Criar uma rotina que verifica se existem arquivos de imagem na pasta do app que não estão vinculados a nenhum pacote no banco de dados (lixo) e apagá-los.
