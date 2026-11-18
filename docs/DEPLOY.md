# Deploy da DN Store

Guia para publicar frontend (Vercel), backend (container Docker) e banco (MySQL), e conectar Asaas e Melhor Envio.
Para o procedimento específico do Melhor Envio, veja também `MELHOR_ENVIO_PRODUCAO_CHECKLIST.md`.
O andamento da preparação está em `PREPARACAO_PRODUCAO.md` e o roteiro de lançamento em `CHECKLIST_LANCAMENTO.md`.

## 1. Arquitetura

```
Navegador ──► Vercel (Next.js, frontend/)
    │
    └─ axios ──► https://<backend>/api  (Spring Boot em container Docker, backendJava/)
                      │       ▲   ▲
                      ▼       │   └── Melhor Envio: OAuth redireciona para /api/integrations/melhor-envio/callback
                  MySQL (TLS) └────── Asaas: POST /api/payment/webhook
```

- O navegador chama a API **diretamente** (não há proxy/rewrite no Next.js). Por isso o CORS do backend precisa listar a URL do frontend.
- O backend é um processo **sempre ligado e com uma única instância**: há tarefas agendadas (worker de webhooks do Asaas, expiração de pedidos não pagos, limpeza de cotações). Não use plano que hiberne por inatividade e não rode duas réplicas.

## 2. Perfis do Spring

| Perfil | Uso | Observações |
|---|---|---|
| `local` (ou nenhum) | desenvolvimento | logs DEBUG; frete simulado permitido |
| `staging` | hospedagem de teste | exige `MELHOR_ENVIO_ENV=sandbox`; mesmas proteções do `prod` |
| `prod` | produção | exige `SHIPPING_GATEWAY=melhorEnvio` e `MELHOR_ENVIO_ENV=production`; Asaas em `production` exige chave `$aact_prod_...` e `ASAAS_WEBHOOK_TOKEN` |

A imagem Docker já define `SPRING_PROFILES_ACTIVE=prod`. Em `staging`/`prod`: logs em INFO, `ddl-auto=validate`, `forward-headers-strategy=native` (HTTPS e IP real do cliente atrás do proxy da plataforma), encerramento gracioso e sem detalhes de erro nas respostas.

Se a configuração estiver incompleta ou incoerente, o app **recusa subir** (fail-closed) com uma mensagem indicando a variável (nunca o valor). Em `staging`/`prod` isso inclui, além das regras de Asaas e Melhor Envio:

- `DB_URL` precisa exigir TLS (`ssl-mode=REQUIRED`, `VERIFY_CA` ou `VERIFY_IDENTITY`);
- `JWT_SECRET`: Base64 com pelo menos 32 bytes e diferente do segredo de desenvolvimento do repositório;
- `AUTH_CHALLENGE_SECRET`: pelo menos 32 caracteres e não pode ser o texto de exemplo;
- `FRONTEND_URL` e cada origem de `CORS_ALLOWED_ORIGINS`: HTTPS público, sem `localhost` nem curinga.

**Fuso horário:** a imagem fixa `TZ=America/Sao_Paulo`. O sistema usa `LocalDateTime` (sem fuso) e os dados existentes foram gerados em horário de Brasília; em UTC os horários exibidos ficariam 3h adiantados. Após o primeiro deploy, confira que o horário de um pedido de teste bate com o relógio de Brasília.

## 3. Variáveis de ambiente

Modelo completo e comentado: `backendJava/.env.example`. Nunca versione valores reais.

### Backend

| Variável | Obrigatória | Observação |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | sim (já `prod` na imagem) | `staging` para homologação |
| `PORT` | não | a plataforma costuma injetar; padrão 8080 |
| `DB_URL`, `DB_USER`, `DB_PASS` | sim | `jdbc:mysql://<host>:<porta>/<db>?ssl-mode=REQUIRED` |
| `DB_POOL_SIZE` | não | padrão 10; respeite o limite de conexões do plano |
| `JWT_SECRET` | sim | segredo aleatório em Base64 |
| `AUTH_CHALLENGE_SECRET` | sim | segredo aleatório longo, diferente do JWT |
| `FRONTEND_URL` | sim | URL pública do frontend (links de e-mail e retorno do OAuth) |
| `CORS_ALLOWED_ORIGINS` | sim | origens exatas do frontend, separadas por vírgula (espaços e `/` final são tolerados) |
| `RESEND_API_KEY`, `RESEND_FROM` | sim | sem a chave, cadastro/login/MFA/recuperação de senha falham com 503 |
| `ORIGIN_ZIP_CODE` | sim | CEP de origem do frete |
| `SHIPPING_GATEWAY` | sim | `melhorEnvio` em staging/prod |
| `MELHOR_ENVIO_*` | sim | ver `.env.example`; `MELHOR_ENVIO_TOKEN_ENCRYPTION_KEY` (32 bytes em Base64) **não pode mudar** depois de autorizar a conta, senão os tokens salvos ficam ilegíveis |
| `ASAAS_ENVIRONMENT`, `ASAAS_BASE_URL`, `ASAAS_API_KEY`, `ASAAS_WEBHOOK_TOKEN` | sim | ambiente e URL base precisam combinar |
| `PAYMENT_*`, `ASAAS_HTTP_*`, `SHIPPING_*` | não | ajustes finos com padrão seguro |
| `JAVA_OPTS` | não | padrão da imagem: `-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError` |

Gerar segredos (exemplos, execute você mesmo e guarde no cofre da plataforma):

```bash
openssl rand -base64 32   # JWT_SECRET e MELHOR_ENVIO_TOKEN_ENCRYPTION_KEY (use valores distintos)
openssl rand -hex 32      # AUTH_CHALLENGE_SECRET e ASAAS_WEBHOOK_TOKEN
```

### Frontend (Vercel)

| Variável | Obrigatória | Observação |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | sim em Production | ex.: `https://<backend>/api`, sem barra final. É embutida no build: se mudar, faça novo deploy. O build de Production na Vercel falha se estiver ausente. |
| `NEXT_PUBLIC_CLOUDINARY_CLOUD_NAME`, `NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET` | sim (para o admin enviar imagens) | Upload direto do navegador com *preset* unsigned da sua conta Cloudinary. Sem elas o código usa os padrões `dn_store`/`dn_store_preset`, que provavelmente não são os da sua conta. Restrinja o preset no Cloudinary (formatos, tamanho, pasta). |

## 4. Frontend na Vercel

1. Importe o repositório e defina **Root Directory = `frontend`** (o repositório é um monorepo).
2. Framework: Next.js (detectado). Sem `vercel.json`.
3. Defina `NEXT_PUBLIC_API_URL` no ambiente Production (e Preview, se quiser testar).
4. Deploy. A URL `*.vercel.app` é permanente.

## 5. Backend (container Docker)

Requisitos da hospedagem: Docker, instância **sempre ligada**, 1 GB de RAM recomendado (medido em teste local: com limite de 512 MB o app sobe, mas ocioso já usa cerca de 430 MiB), variáveis de ambiente, HTTPS fornecido pela plataforma.

- Build: `backendJava/Dockerfile` (Maven + Java 21; imagem final roda como usuário sem privilégios).
- Health check da plataforma: **`GET /actuator/health/liveness`** (não depende do banco, evita reinícios inúteis durante manutenção do banco).
- `GET /actuator/health` também é público e inclui o banco (útil para checagem manual). Nenhum outro endpoint do Actuator é exposto.
- Teste local da imagem:

```bash
cd backendJava
cp .env.example .env      # preencha com valores de TESTE
docker compose up --build # usa o perfil local
curl http://localhost:8080/actuator/health
```

### Migrations

O Flyway roda sozinho na inicialização e o Hibernate só valida (`ddl-auto=validate`). **Nunca** defina `DDL_AUTO=update` em produção. Antes de um deploy que traga migrations novas, faça backup (`BACKUP_RECUPERACAO.md`). Esse guia também traz a verificação do histórico do Flyway no banco existente e o procedimento do **primeiro administrador**.

## 6. Banco de dados

MySQL 8.4 com TLS (`ssl-mode=REQUIRED`). O backend e a plataforma de banco precisam aceitar conexão entre si (ver regras de rede/IP allowlist do provedor). O provedor atual é o Aiven.

## 7. Webhook do Asaas

- URL: `https://<backend>/api/payment/webhook`
- Autenticação: o Asaas envia o token no header `asaas-access-token`; configure no painel do Asaas o **mesmo valor** de `ASAAS_WEBHOOK_TOKEN`.
- O endpoint apenas grava o evento (idempotente por id) e responde; um worker agendado o processa com novas tentativas.
- Cadastre o webhook no ambiente correspondente (sandbox ou produção) e só depois habilite cobranças.
- **Habilite apenas eventos de cobrança (`PAYMENT_*`).** O endpoint espera o objeto `payment` no payload; eventos de outros tipos (transferência, conta etc.) retornam erro (502), e a documentação do Asaas informa que a fila de webhooks pode ser **interrompida** quando há erros (é preciso reabilitá-la no painel do Asaas).
- Eventos `PAYMENT_*` autenticados são sempre gravados; um `status` ainda desconhecido não rejeita o evento. O worker consulta o estado atual da cobrança no Asaas antes de aplicar qualquer mudança (nunca confia só no payload) e, se não conseguir interpretar, tenta de novo com espera crescente (1 min, 5 min, 15 min, 1 h) até `PAYMENT_WEBHOOK_MAX_ATTEMPTS` e então marca o evento para revisão.
- Pix e boleto são os únicos métodos habilitados (cartão está desativado no código).
- Para o Pix funcionar em produção, a conta Asaas precisa estar habilitada para cobranças Pix (verifique no painel do Asaas).

## 8. OAuth do Melhor Envio

- Redirect URI cadastrado no aplicativo do Melhor Envio e em `MELHOR_ENVIO_REDIRECT_URI`: `https://<backend>/api/integrations/melhor-envio/callback` (precisam ser idênticos).
- Após autorizar, o backend redireciona para `<FRONTEND_URL>/admin?integration=melhor-envio&status=...`.
- Em `prod`, as URLs da API/OAuth precisam apontar para `melhorenvio.com.br`; em `staging`, para `sandbox.melhorenvio.com.br`. O escopo `shipping-calculate` é obrigatório.

## 9. Trocar a URL ou o domínio depois (sem recompilar o código)

Nada é fixo no código. Ao trocar a URL do backend ou do frontend:

1. Backend: atualizar `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` e `MELHOR_ENVIO_REDIRECT_URI`.
2. Vercel: atualizar `NEXT_PUBLIC_API_URL` e fazer **novo deploy** (a variável é embutida no build).
3. Melhor Envio: atualizar o Redirect URI no aplicativo.
4. Asaas: atualizar a URL do webhook.
5. Reiniciar o backend.

## 10. Operação

- Logs: pela plataforma (saída padrão). Em produção o nível é INFO e dados sensíveis não devem aparecer; veja o item de revisão de logs em `PREPARACAO_PRODUCAO.md`.
- Rollback: reimplante a imagem/commit anterior. Migrations já aplicadas **não** são desfeitas automaticamente; por isso migrations devem ser aditivas e compatíveis com a versão anterior do código.

## 11. Inspecionar a loja com dados reais sem escrever no banco (perfil `readonly`)

Subir o backend normalmente contra um banco real **escreve nele**: o Flyway aplica migrations pendentes, o `DatabaseSeeder` roda e três agendadores gravam (expiração de pedidos, worker de webhooks, limpeza de cotações). Para olhar o catálogo verdadeiro com segurança, use o perfil `readonly`:

```bash
cd backendJava          # usa o .env habitual (Aiven)
SPRING_PROFILES_ACTIVE=local,readonly ./mvnw spring-boot:run
# em outro terminal: cd frontend && npm run dev  (usa http://localhost:8080/api)
```

O perfil desliga o Flyway, o seeder e os agendadores e abre todas as conexões do pool em sessão MySQL somente leitura (`ReadOnlyProfileMySqlIT` comprova isso contra MySQL real). Efeito esperado: navegar por catálogo, categorias, eventos e detalhes funciona; login, cadastro, carrinho e checkout **não** funcionam, porque precisam gravar. Não use este perfil em produção. **Atenção:** como o perfil não aplica migrations e o Hibernate valida o schema, ele só sobe contra um banco que já tenha todas as migrations do código atual. Hoje isso exclui o Aiven até a V26 ser aplicada (`BACKUP_RECUPERACAO.md`, seção 4). Para garantia total, o ideal continua sendo um usuário do banco com apenas `SELECT`.

Testes automatizados destrutivos ou de integração devem usar sempre um MySQL descartável (`application-integration-local.properties`, porta 33316), nunca o `.env` do Aiven.

