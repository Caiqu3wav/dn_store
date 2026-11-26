# 🛒 DN Store — Desafio Natureza

Plataforma de e-commerce voltada para a venda de roupas e acessórios de ciclismo (mountain bike), com integração de eventos esportivos e sistema de entrega com rastreamento.

---

## 📌 Visão Geral

A **DN Store (Desafio Natureza)** é uma aplicação moderna de e-commerce que tem como objetivo:

- Comercializar produtos da marca (camisetas, jerseys, bonés, meias)
- Promover eventos de mountain bike
- Oferecer experiência de compra fluida e responsiva
- Integrar cálculo de frete e rastreamento de pedidos via Correios

---

## 🎯 Objetivos do Projeto

- Criar um sistema escalável de e-commerce
- Proporcionar boa experiência do usuário (UI/UX)
- Automatizar processos de compra, pagamento e entrega
- Integrar serviços externos (frete e rastreio)
- Servir como base para futuras implementações com IA

---

## 🧱 Stack Tecnológica

### Frontend
- Next.js (React)
- TailwindCSS
- Framer Motion (animações)

### Backend
- Java + Spring Boot
- REST API

### Banco de Dados
- MySQL

### Integrações
- API dos Correios (frete e rastreamento)
- Possível integração com gateways de pagamento

---

## 🏗️ Arquitetura

O sistema segue arquitetura em camadas:

- **Frontend (Next.js)** → Interface do usuário
- **Backend (Spring Boot)** → Regras de negócio
- **Banco de Dados (MySQL)** → Persistência
- **APIs externas** → Frete e rastreio

---

## 👤 Casos de Uso

### 🧍 Cliente

- Visualizar produtos
- Filtrar produtos por categoria
- Adicionar produtos ao carrinho
- Gerenciar carrinho (editar/remover itens)
- Inserir endereço de entrega
- Calcular frete via CEP
- Finalizar compra
- Acompanhar status do pedido
- Visualizar eventos
- Acessar página "Sobre"

---

### 🛠️ Administrador

- Gerenciar produtos
- Gerenciar estoque
- Visualizar pedidos
- Atualizar status de pedidos
- Gerenciar eventos
- Gerar relatórios

---

## 📋 Requisitos Funcionais (RF)

### 🛍️ E-commerce

- **RF01**: O sistema deve permitir cadastro de produtos  
- **RF02**: O sistema deve exibir lista de produtos  
- **RF03**: O sistema deve permitir adicionar produtos ao carrinho  
- **RF04**: O sistema deve permitir atualizar/remover itens do carrinho  
- **RF05**: O sistema deve calcular o valor total do pedido  
- **RF06**: O sistema deve permitir finalização de compra  

---

### 🚚 Frete e Entrega

- **RF07**: O sistema deve calcular frete com base no CEP  
- **RF08**: O sistema deve integrar com API dos Correios  
- **RF09**: O sistema deve armazenar código de rastreamento  
- **RF10**: O sistema deve exibir status da entrega  

---

### 👤 Usuário

- **RF11**: O sistema deve permitir cadastro de usuários  
- **RF12**: O sistema deve permitir login/autenticação  
- **RF13**: O sistema deve permitir múltiplos endereços  

---

### 📦 Pedido

- **RF14**: O sistema deve registrar pedidos  
- **RF15**: O sistema deve armazenar itens do pedido  
- **RF16**: O sistema deve associar pedido ao usuário  
- **RF17**: O sistema deve registrar pagamento  

---

### 📅 Eventos

- **RF18**: O sistema deve exibir eventos  
- **RF19**: O sistema deve exibir informações do próximo evento  
- **RF20**: O sistema deve exibir localização do evento (mapa)  

---

## ⚙️ Requisitos Não Funcionais (RNF)

### 🚀 Performance
- **RNF01**: O sistema deve responder requisições em até 2 segundos  
- **RNF02**: O frontend deve carregar rapidamente (SSR/SSG)  

---

### 🔒 Segurança
- **RNF03**: Senhas devem ser armazenadas com hash  
- **RNF04**: Comunicação deve ser segura (HTTPS)  
- **RNF05**: Validação de dados no backend  

---

### 📱 Usabilidade
- **RNF06**: Interface responsiva (mobile-first)  
- **RNF07**: Navegação intuitiva  
- **RNF08**: Feedback visual em ações do usuário  

---

### 🧩 Escalabilidade
- **RNF09**: Sistema deve suportar aumento de usuários  
- **RNF10**: Arquitetura desacoplada (frontend/backend)  
- **RNF11**: Uso de APIs externas desacopladas  

---

### 🔄 Manutenibilidade
- **RNF12**: Código organizado em camadas  
- **RNF13**: Uso de boas práticas de POO  
- **RNF14**: Componentização no frontend  

---

## 📊 Modelagem

O banco de dados inclui:

- Usuários e endereços  
- Produtos, variantes e imagens  
- Carrinho e itens  
- Pedidos e pagamentos  
- Entregas e rastreamento  
- Eventos  

---

## 🚀 Futuras Implementações

- Sistema de recomendação com IA  
- Chatbot de atendimento  
- Sistema de cupons e promoções  
- Dashboard analítico  
- Integração com marketplace  

---

## 📌 Status do Projeto

🚧 Em desenvolvimento  

## 📦 Estoque: notas de entrada, livro e reconciliação

**Modelo.** Saída = venda, entrada = nota de fornecedor, manutenção = ajuste manual. Todo saldo de variação muda por um único
caminho, o *livro* (`stock_movements`, via `StockLedger`): cada linha guarda `stock_before`, `delta`, `stock_after`, tipo,
quem fez e a referência (nota ou pedido), e **nunca é alterada nem apagada**. Se a gravação do livro falhar, a transação inteira
falha (não existe venda sem livro). Tipos: `SALE`, `CANCELLATION`, `REACTIVATION`, `ENTRY`, `ADJUSTMENT`, `INITIAL`, `OPENING`.

**Entrada por nota** (`POST /api/admin/stock/entries`): fornecedor + série/número + data + várias linhas (variação, quantidade,
custo opcional). Uma transação só, tudo ou nada, variações travadas em ordem de id. A nota é imutável; erro se corrige com
ajuste (categoria `CORRECTION`). Duplicidade: `UNIQUE(fornecedor, série, número)` e `idempotencyKey` (UUID gerado uma vez por
abertura do formulário). Mesma chave e mesmo conteúdo devolve a nota existente (200, `replayed=true`); nota repetida com outra
chave devolve 409 com `existingEntryId`.

**Ajuste** (`PUT /api/admin/stock/adjust`): motivo obrigatório, categoria, `delta` ou `countedQuantity` (contagem; o delta é
calculado sob lock) e `expectedBalance` (recusa com 409 + `currentBalance` se o saldo mudou desde que a tela foi aberta).

**Reconciliação** (`GET /api/admin/stock/reconciliation`): compara `product_variants.stock` com o último movimento do livro.
`OK`, `DIVERGENT` (saldo mudou fora do livro) ou `NO_LEDGER` (variação que já tinha estoque antes do livro). Para `NO_LEDGER`,
"adotar saldo" grava um marcador `OPENING` com `stock_before = stock_after = N` e `delta = 0` (não inventa entrada). O detalhe por
variação lista quebras de cadeia (`stock_before` ≠ `stock_after` da linha anterior, por `seq`).

**Janela de deploy.** A V30 não bloqueia a versão antiga, que continua inserindo movimentos sem `stock_before`. Logo após o
deploy podem aparecer poucas divergências pequenas ou linhas sem `stock_before`: são movimentos da instância antiga e somem da
conta de quebra de cadeia (só se comparam linhas novas). Rode a reconciliação depois do deploy e confira.

**Atenção ao deploy.** O DDL do MySQL **não é transacional**. Por isso a V29 usa `CREATE TABLE IF NOT EXISTS` (pode ser repetida
se o Flyway marcar falha) e a V30 é uma única instrução `ALTER` (atômica no MySQL 8). O `seq` das linhas anteriores à V30 foi
preenchido na ordem física da tabela, não cronológica entre elas; novas linhas têm `seq` monotônico.

**Testes.** `StockWriteGuardTest` falha se surgir `setStock(` fora da lista de serviços que passam pelo livro. Os ITs de MySQL
(`StockLedgerMySqlIT`, `StockNoteMySqlIT`) não rodam no `./mvnw test`; rode com `-Dtest=StockNoteMySqlIT` contra o MySQL local.

## Executar o backend com Docker

O backend no Docker conecta ao banco MySQL do Aiven; nenhum servidor MySQL local é iniciado. No PowerShell, entre na pasta `backendJava` e configure `.env` com os dados do Aiven. Se ainda não houver um `.env`, copie o exemplo:

```powershell
cd backendJava
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Edite `.env` com a URL, o usuário e a senha do Aiven. Configure também `JWT_SECRET` e `AUTH_CHALLENGE_SECRET` com valores próprios. Depois, com o Docker Desktop aberto, inicie o backend:

```powershell
$env:COMPOSE_DISABLE_ENV_FILE = "1"
docker compose up --build
```

O Compose passa `.env` ao container como variáveis sem interpretar caracteres especiais como `$`. Mantenha esse comando na mesma janela do PowerShell para os comandos seguintes. O Docker compila o backend com Java 17, então não é necessário instalar ou trocar a versão do Java no Windows. A API fica disponível em `http://localhost:8080`. Na primeira inicialização, o Docker baixa as imagens e compila o projeto, o que pode levar alguns minutos.

Para encerrar, pressione `Ctrl+C`. Para iniciar em segundo plano, use `docker compose up --build -d`; para acompanhar os logs, use `docker compose logs -f backend`.

As credenciais e chaves de `.env` são locais e não devem ser commitadas nem reutilizadas em produção.

---

## 🤝 Contribuição

Projeto acadêmico — desenvolvimento em equipe  

---

## 📎 Licença

Uso educacional