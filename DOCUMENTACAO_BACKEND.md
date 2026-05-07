# Documentação da API Backend (Java/Spring Boot)

Esta documentação descreve os endpoints disponíveis na API do backend Java da DN Store.

## Informações Gerais
- **Tecnologias**: Java 17+, Spring Boot, Spring Security (JWT), Spring Data JPA.
- **Base URL**: `http://localhost:8080` (Ambiente de desenvolvimento)
- **Prefixo da API**: `/api`

---

## Autenticação
A maioria dos endpoints requer autenticação via token JWT. O token deve ser enviado no cabeçalho `Authorization` de cada requisição no formato:
`Authorization: Bearer <seu_token_jwt>`

### 🔐 Autenticação e Registro (`/api/auth`)

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `POST` | `/register` | Cadastra um novo usuário no sistema. |
| `POST` | `/login` | Autentica um usuário e retorna o token JWT e dados básicos. |
| `POST` | `/forgot-password` | Solicita um token de recuperação de senha via e-mail (em desenvolvimento). |
| `POST` | `/reset-password` | Redefine a senha do usuário utilizando o token de recuperação. |

---

## Catálogo de Produtos (`/api/products`)

Gerenciamento completo do catálogo de produtos.

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/` | Lista todos os produtos cadastrados. |
| `GET` | `/{id}` | Retorna os detalhes de um produto específico pelo UUID. |
| `POST` | `/` | Cria um novo produto (apenas usuários autorizados). |
| `PUT` | `/{id}` | Atualiza os dados de um produto existente. |
| `DELETE` | `/{id}` | Remove um produto do sistema. |

---

## Carrinho de Compras (`/api/cart`)

Manipulação do carrinho do usuário autenticado.

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/` | Obtém o carrinho do usuário logado (cria um novo se não existir). |
| `POST` | `/items` | Adiciona um item ao carrinho (requer `productVariantId` e `quantity`). |
| `PUT` | `/items/{productVariantId}` | Atualiza a quantidade de um item específico no carrinho. |
| `DELETE` | `/items/{productVariantId}` | Remove um item do carrinho. |
| `DELETE` | `/` | Limpa todos os itens do carrinho. |

---

## Pedidos e Checkout (`/api/orders`)

Processamento de compras e consulta de pedidos.

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `POST` | `/` | Finaliza a compra (Checkout). Requer `zipCode` e `shippingType`. |
| `GET` | `/{id}` | Consulta os detalhes de um pedido específico pelo UUID. |

---

## Área Administrativa (`/api/admin`)

Endpoints protegidos para gestão administrativa.

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/dashboard` | Valida se o usuário tem privilégios de administrador. |

---

## Estrutura de Resposta Padrão
A API utiliza códigos HTTP padrão para indicar o sucesso ou falha das operações:
- `200 OK`: Sucesso na requisição.
- `201 Created`: Recurso criado com sucesso.
- `204 No Content`: Sucesso, mas sem conteúdo de retorno.
- `400 Bad Request`: Requisição inválida (erro de validação).
- `401 Unauthorized`: Token JWT ausente ou inválido.
- `403 Forbidden`: Usuário autenticado mas sem permissão para o recurso.
- `404 Not Found`: Recurso não encontrado.
- `500 Internal Server Error`: Erro inesperado no servidor.
