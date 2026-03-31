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

---

## 🤝 Contribuição

Projeto acadêmico — desenvolvimento em equipe  

---

## 📎 Licença

Uso educacional