# 🛒 Backend E-commerce Premium (Java)
Guia de Apresentação — Projeto Integrador  
Este documento serve como **roteiro técnico** para o vídeo da equipe.  
Ele explica **como cada parte do backend representa conceitos de POO e Engenharia de Software**.

---

## 🗺️ Mapa Conceitual do Código

### | Arquivo / Classe | Conceito | Explicação para o Vídeo |
|----------------------|------------------------------|---------------------------|
| **Product.java** | **Abstração** | Classe base genérica. Ninguém compra um “Produto” em si; compra algo Físico ou Digital. |
| **PhysicalProduct.java** | **Herança & Polimorfismo** | Estende `Product`. Sobrescreve `getShippingWeight()` pois tem peso real. |
| **DeliveryStrategy.java** | **Interface / Contrato** | Define *o que* é “calcular frete”, sem dizer *como*. Essencial para extensibilidade. |
| **SedexStrategy.java** | **Strategy Concreta** | Uma implementação real de cálculo de frete (rápido/caro). |
| **DeliveryService.java** | **Service / Integração** | Usa ViaCEP real + escolhe estratégia dinamicamente (Polimorfismo). |
| **OrderService.java** | **Orquestração** | “Gerente” do processo. Valida carrinho, calcula frete, cria pedido. |
| **BackendConfig.java** | **Injeção de Dependência** | O Spring monta tudo aqui: CORS, Beans e componentes reutilizáveis. |

---

## 🏗️ Estrutura do Projeto (Packages)

### ### 1. `model` — **O Domínio**
Onde vivem as entidades e regras de dados.  

🟣 Destaque recomendado no vídeo:  
- **Cart.java**  
  - Possui regras de negócio reais (ex: `removeItem()`, `updateItem()`).  
  - Não é apenas um "saco de dados".  
  - Mostra encapsulamento e proteção do estado.

---

### 2. `service` — **A Lógica**
O cérebro do sistema.

#### **2.1 Strategy (`service.strategy`)**
- Mostre no vídeo como adicionar uma transportadora nova (“JadlogStrategy”)  
  é tão simples quanto criar uma classe nova.  
- Nada no código existente precisa ser modificado → **SOLID: Open/Closed Principle**.

#### **2.2 Integração**
- **DeliveryService** chama o ViaCEP real.  
- Traz realismo e mostra o sistema consumindo serviços externos.

---

### 3. `controller` — **A API REST**
A porta de entrada do sistema.

- `ProductController` → Catálogo de produtos.  
- `CartController` → Estado do carrinho.  
- `OrderController` → Finaliza o pedido.

📝 Todos os endpoints têm **Javadoc**, facilitando leitura e documentação.

---

## 🚀 Como Rodar e Testar

### ▶️ **1. Iniciar o servidor**
Execute:

Dica Pro: Mencione que o sistema está preparado para Escalabilidade por usar Interfaces e Injeção de Dependência, permitindo trocar componentes (como Banco de Dados ou API de Frete) facilmente.
