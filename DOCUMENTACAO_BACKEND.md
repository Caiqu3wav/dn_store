# Documentação da API Backend (Java/Spring Boot)

Esta documentação descreve os endpoints disponíveis na API do backend Java da DN Store.

## Informações Gerais
- **Tecnologias**: Java 17+, Spring Boot, Spring Security (JWT), Spring Data JPA.
- **Base URL**: `http://localhost:8080` (Ambiente de desenvolvimento)
- **Prefixo da API**: `/api`

---

## Como Executar o Projeto Localmente

### 1. Requisitos Prévios
- **Java 17** ou superior instalado e configurado nas variáveis de ambiente (`JAVA_HOME`).
- **Docker** e **Docker Compose** instalados e em execução.

---

### 2. Executando o Banco de Dados (Docker)
O projeto utiliza uma imagem do **MySQL 8.0** configurada no `docker-compose.yml` para desenvolvimento local.

Para iniciar o banco de dados:
1. Abra o terminal WSL na pasta raiz do backend (`backendJava`).
2. Execute o comando para iniciar o container em segundo plano:
   ```bash
   docker compose up -d
   ```
3. O banco de dados estará acessível em `localhost:3306` com as seguintes configurações/credenciais (já configuradas no arquivo `application.properties`):
   - **Database**: `app_db`
   - **User**: `app_user`
   - **Password**: `app_pass`
   - **Root Password**: `root`

Para parar e remover os containers do banco de dados quando terminar:
```bash
docker compose down
```

---

### 3. Executando o Backend (Java / Spring Boot)

Após garantir que o banco de dados no Docker está em execução, siga os passos abaixo para rodar o backend:

#### Pelo Wrapper do Maven (Recomendado)
Navegue até o diretório `backendJava` no seu prompt de comando ou terminal:

- **No Windows (PowerShell ou CMD)**:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **No Linux / macOS**:
  ```bash
  ./mvnw spring-boot:run
  ```

#### Empacotando e rodando o arquivo JAR diretamente
Caso queira gerar o pacote compilado da aplicação e executá-lo diretamente:

1. Gere o arquivo `.jar`:
   - **No Windows**:
     ```powershell
     .\mvnw.cmd clean package
     ```
   - **No Linux / macOS**:
     ```bash
     ./mvnw clean package
     ```
2. Execute o arquivo gerado na pasta `target`:
   ```bash
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```

---

### 4. Executando os Testes
Para rodar os testes unitários e de integração do projeto:

- **No Windows**:
  ```powershell
  .\mvnw.cmd test
  ```
- **No Linux / macOS**:
  ```bash
  ./mvnw test
  ```

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

Gerenciamento do catálogo de produtos. A visualização é pública, mas as modificações são restritas aos administradores.

| Método | Endpoint | Proteção | Descrição |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Pública | Lista todos os produtos cadastrados. Suporta busca (`search`), preço mínimo/máximo (`minPrice`/`maxPrice`) e ordenação (`sortBy`). |
| `GET` | `/{id}` | Pública | Retorna os detalhes de um produto específico pelo UUID. |
| `POST` | `/` | Requer `ADMIN` | Cria um novo produto físico. |
| `PUT` | `/{id}` | Requer `ADMIN` | Atualiza os dados de um produto existente (incluindo imagens). |
| `DELETE` | `/{id}` | Requer `ADMIN` | Remove um produto do sistema. |

### Exemplo de Payload para POST / PUT (`PhysicalProduct`)

Ao criar ou editar um produto, envie o JSON contendo os dados e a lista de URLs públicas obtidas após o upload (ex: via Cloudinary):

```json
{
  "name": "Bola de Futebol Pro",
  "description": "Bola oficial de alta performance",
  "price": 149.90,
  "promotionalPrice": 129.90,
  "active": true,
  "weight": 0.45,
  "width": 22.0,
  "height": 22.0,
  "depth": 22.0,
  "images": [
    {
      "imageUrl": "https://res.cloudinary.com/dn-store/image/upload/v12345678/products/bola_main.png",
      "main": true
    },
    {
      "imageUrl": "https://res.cloudinary.com/dn-store/image/upload/v12345678/products/bola_angle.png",
      "main": false
    }
  ]
}
```

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
