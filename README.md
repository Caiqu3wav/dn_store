Guia de Apresentação: Backend E-commerce Premium (Java)
Este documento serve como Roteiro Técnico para o vídeo. Ele mapeia cada parte do código a um conceito de Engenharia de Software e POO.

🗺️ Mapa Conceitual do Código
Arquivo / Classe	Conceito POO / Pattern	Explicação para o Vídeo
Product.java
Abstração	Classe base genérica. Ninguém compra um "Produto", compra um "Físico" ou "Digital".
PhysicalProduct.java
Herança & Polimorfismo	Estende 
Product
. Sobrescreve 
getShippingWeight()
 pois tem peso físico.
DeliveryStrategy.java
Interface / Contrato	Define O QUE é calcular frete, sem saber COMO. Essencial para extensibilidade.
SedexStrategy.java
Concrete Strategy	Uma forma específica de calcular (Rápido/Caro). Implementa a Interface.
DeliveryService.java
Service / Integration	Usa o ViaCEP real. Escolhe a estratégia dinamicamente (Polimorfismo).
OrderService.java
Orquestração	"Gerente" do processo. Valida carrinho, calcula frete, fecha pedido.
BackendConfig.java
Injeção de Dependência	O Spring "monta" o sistema aqui. Configura CORS e Beans globais.
🏗️ Estrutura do Projeto (Packages)
1. model (O Domínio)
Onde vivem as regras de dados.

Destaque: Mostre o 
Cart.java
 e seus métodos de negócio (
removeItem
, 
updateItem
). Não é apenas um balde de dados, tem lógica de proteção.
2. service (A Lógica)
O cérebro da aplicação.

Strategy (service.strategy): Dê ênfase aqui! Mostre como é fácil adicionar uma transportadora nova ("Jadlog") apenas criando uma nova classe, sem mexer no código antigo. O Princípio Open/Closed do SOLID.
Integração: Em 
DeliveryService
, mostre a chamada real ao viacep.com.br. Isso traz realismo.
3. controller (A API REST)
A porta de entrada.

RESTful Real: Mostre que temos GET, POST, PUT, DELETE.
ProductController
: Gerencia o catálogo.
CartController
: Manipula o estado do usuário.
OrderController
: Finaliza a transação.
Comentários: O código está documentado com Javadoc explicando cada endpoint.
🚀 Como Rodar e Testar
Start: Rode a classe BackendApplication.java.
Porta: O servidor sobe na porta 8080.
Cenário de Teste (Vídeo):
Passo 1: Liste produtos (GET /api/products).
Passo 2: Adicione o "Notebook Gamer" ao carrinho (POST /api/cart/items).
Passo 3: Simule um frete SEDEX para o CEP 01001-000 (Praça da Sé).
Passo 4: Finalize o pedido (POST /api/orders).
Resultado: Mostre o JSON de resposta com o valor do frete calculado e prazo de entrega.
Dica Pro: Mencione que o sistema está preparado para Escalabilidade por usar Interfaces e Injeção de Dependência, permitindo trocar componentes (como Banco de Dados ou API de Frete) facilmente.