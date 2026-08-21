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
- Uma instância MySQL ativa no **Aiven**.
- **MySQL Workbench** (opcional, recomendado para testar a conexão e consultar o banco).
- O certificado CA (`ca.pem`) baixado na página de conexão do serviço Aiven.

---

### 2. Conectando ao MySQL do Aiven pelo MySQL Workbench
Na página do serviço MySQL no Aiven, abra **Connect** e copie os dados atuais de conexão. Não use host ou porta antigos salvos no projeto.

No MySQL Workbench, crie uma conexão com:

- **Connection Method**: `Standard (TCP/IP)`
- **Hostname**: host exibido pelo Aiven
- **Port**: porta exibida pelo Aiven
- **Username**: usuário exibido pelo Aiven, normalmente `avnadmin`
- **Password**: senha atual do usuário Aiven
- **Default Schema**: banco exibido pelo Aiven, normalmente `defaultdb`

Na aba **SSL**:

1. Selecione **Use SSL** ou **Require**.
2. Em **SSL CA File**, selecione o arquivo `ca.pem` baixado do Aiven.
3. Salve a conexão e use **Test Connection**.

O Workbench deve conectar usando exatamente o par **Hostname + Port** fornecido pelo Aiven. Se houver erro de DNS ou conexão recusada, atualize os dados no Aiven antes de alterar o código.

O certificado `ca.pem` é usado pelo Workbench para validar o servidor. Ele não deve ser commitado no Git nem colocado dentro de `src/main/resources`.

---

### 3. Executando o Backend (Java / Spring Boot)

Depois de testar a conexão no Workbench, configure o arquivo `backendJava/.env`. O Spring Boot importa esse arquivo quando o comando é executado a partir da pasta `backendJava`.

Use a URL JDBC correspondente aos dados atuais do Aiven:

```env
DB_URL=jdbc:mysql://HOST_AIVEN:PORTA/defaultdb?ssl-mode=REQUIRED
DB_USER=avnadmin
DB_PASS=SENHA_ATUAL_DO_AIVEN
DDL_AUTO=validate
```

Não coloque `DB_USER` ou `DB_PASS` dentro de `DB_URL`; eles são informados separadamente. O arquivo `.env` contém segredos e deve permanecer fora do Git.

Para iniciar o backend, abra o terminal na pasta `backendJava`:

#### Pelo Wrapper do Maven (Recomendado)

- **No Windows (PowerShell ou CMD)**:
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **No Linux / macOS**:
  ```bash
  ./mvnw spring-boot:run
  ```

Na inicialização, o Flyway aplica as migrations pendentes e o Hibernate valida o schema. A aplicação só deve ser considerada pronta quando aparecer uma mensagem semelhante a:

```text
Started BackendApplication
```

Se aparecer `Connection refused`, `UnknownHostException` ou `Communications link failure`, confira o **Hostname**, **Port**, senha e o estado do serviço no Aiven. Se aparecer `Schema-validation: missing column`, verifique as migrations Flyway e não altere tabelas manualmente sem registrar uma nova migration.

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
