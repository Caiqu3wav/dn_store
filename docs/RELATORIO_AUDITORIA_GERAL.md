# Relatório de Auditoria Geral — DN Store

Data: 2026-11-17 · Branch `main` · Auditoria somente leitura (nenhum arquivo do projeto foi alterado, exceto este relatório; nenhum commit, push, deploy, migration ou escrita em banco real).

> **Como ler este relatório.** Cada afirmação tem um destes níveis de evidência:
> - **[EXEC]** executado nesta auditoria (testes, build, lint, Docker, MySQL descartável);
> - **[LEIT]** confirmado pela leitura direta do código (não executado);
> - **[AGENTE]** apontado por uma varredura de leitura por área e **não** reconfirmado individualmente — tratar como "provável, a confirmar";
> - **[PEND]** depende de credencial, sandbox ou ambiente externo e **não foi validado**.

---

## 1. Resumo executivo

A DN Store tem uma base técnica bem construída (arquitetura em camadas, máquina de estados de pagamento, webhooks duráveis, trava de estoque e cupom em MySQL real, perfil `readonly` verificado, segurança de rotas admin testada). O que existe **compila, passa nos testes locais e sobe em Docker**.

Mas **não está pronta para produção**, e o motivo principal não é falta de código, e sim:

1. **Nenhuma integração externa foi validada.** Asaas, Melhor Envio, Resend e Cloudinary têm nível máximo "testado com mock" (e "banco descartável" só para o schema). Nunca houve chamada a sandbox. [PEND]
2. **Há lacunas financeiras reais**: não existe fluxo de estorno (o Asaas só avisa por webhook e o pedido não reage), o boleto expira antes de poder ser pago, o preço promocional é exibido mas não cobrado, e o admin consegue enviar um pedido nunca pago.
3. **Falhas de fluxo de usuário**: usuário com e-mail não verificado fica sem saída no login; o limite de 5 tentativas de código não persiste.
4. **Entrega**: não há CI; vários arquivos importantes (V26, perfil `readonly`, pagamento manual, 6 testes) estão **não commitados**; dependências com vulnerabilidades conhecidas (`npm audit`: 1 crítica + 7 altas).

### Percentuais (estimativa ponderada; metodologia na seção 4)

| Indicador | Valor |
|---|---:|
| **Conclusão geral do projeto** | **≈ 65%** |
| Conclusão técnica (backend, frontend, banco, segurança, testes, infra) | ≈ 70% |
| Conclusão do backend | ≈ 75% |
| Conclusão do frontend | ≈ 68% |
| Validação das integrações — local (mock / banco descartável) | ≈ 32% |
| Validação das integrações — **externa (sandbox/produção)** | **0%** |
| **Prontidão para produção** | **≈ 40%** |

São estimativas fundamentadas numa matriz explícita, não medições. Mudam se os pesos mudarem; os pesos estão na seção 4.

### Veredito

- **Para apresentação final (demo)**: viável após corrigir os itens da seção 15, fase 1 (principalmente login não verificado, preço promocional, `eventos` fixos, "Frete: Grátis" fixo e rodapé com meios de pagamento inexistentes).
- **Para produção**: bloqueada até validar Asaas e Melhor Envio em sandbox e fechar os achados CRÍTICO/ALTO.

---

## 2. Arquitetura

### 2.1 Inventário
- Backend: Java 21, Spring Boot **3.4.0** (desatualizado), Spring Security, JPA/Hibernate (`ddl-auto=validate`), Flyway 10.x, MySQL (Aiven em produção, TLS), jjwt 0.11.5. ~131 classes principais, 49 classes de teste.
- Frontend: Next.js 16 (App Router), React 19, Tailwind 4 + styled-components, axios, SWR (usado em admin), js-cookie. 23 rotas.
- Integrações: Asaas (pagamentos), Melhor Envio (frete), Resend (e-mail), Cloudinary (imagens, só no frontend), ViaCEP.
- Monorepo sem build raiz. `package.json` da raiz contém só `styled-components` (resíduo).
- CI/CD: **inexistente** (sem `.github/`, sem `vercel.json`). Dockerfile compila com `-DskipTests`.

### 2.2 Diagrama

```
                    ┌────────────────────────────┐
  Navegador ───────▶│ Frontend Next.js (Vercel)  │
                    │ cookie auth_token (JS)     │
                    │ carrinho em localStorage   │
                    └───────┬─────────┬──────────┘
                            │ axios   │ upload direto (preset unsigned)
                  Bearer JWT│         ▼
                            │     Cloudinary  (só URL vai ao backend)
                            ▼
        ┌───────────────────────────────────────────────┐
        │ Backend Spring Boot (container, 1 instância)  │
        │ controller → service → repository → model     │
        │ Security: JWT + RBAC + rate limit em memória  │
        │ Schedulers: expiração, webhook worker, limpeza│
        └──┬──────────┬───────────┬───────────┬─────────┘
           │          │           │           │
           ▼          ▼           ▼           ▼
        MySQL       Asaas     Melhor Envio   Resend
        (Aiven)  (cobrança + (OAuth+cotação) (e-mails de
                  webhook)                     auth)
           ▲
           └── perfil readonly (Hikari read-only) para consulta real
```

Fluxos principais: cadastro → código por e-mail → login (+ MFA por e-mail) → JWT 24 h; checkout → cotação ME (persistida, TTL 15 min) → pedido (trava carrinho e variantes `FOR UPDATE`, reserva estoque, consome cupom atômico) → cobrança Asaas (Pix/boleto) → webhook durável → máquina de estados de pagamento → logística.

### 2.3 Acoplamentos e pontos de atenção
- Chamadas HTTP externas dentro de `@Transactional` (cotação ME, expiração Asaas, envio Resend) seguram conexão do pool e locks.
- Rate limit em memória → exige instância única.
- Entidades JPA expostas diretamente como corpo/resposta em vários controllers (produtos, pedidos, cupom).
- Código morto: `ProductRepository.searchProducts`, `findByNameContainingIgnoreCase`, `OrderRepository.findByStatus`, `ProductVariantRepository.findBySku`, `ProductService.searchForAdmin/findAll`; SDK `mercadopago` no `pom.xml` sem uso; no frontend `cartService.ts`, `hooks/useApi.ts`, `CategoryCard`, `EventsTeaser`, `lib/auth/AuthApi.tsx` (vazio), rota `/checkout/sucesso` órfã.

---

## 3. Estado geral — o que funciona de fato

| Funcionalidade | Estado | Evidência |
|---|---|---|
| Catálogo, filtros, detalhe, variantes/estoque | Funcional | LEIT (frontend + backend), build ok |
| Favoritos | Funcional | LEIT |
| Carrinho | Parcial | só localStorage + `PUT /cart/sync`; preço não revalidado; "Frete: Grátis" fixo e falso |
| Cadastro / login / MFA / reset de senha | Parcial | login de e-mail não verificado quebra; limite de tentativas ineficaz; **sem testes de `AuthController`** |
| Checkout (cotação → pedido → Pix) | Parcial | fluxo existe; retomada por `sessionStorage` frágil; **nunca testado contra sandbox** |
| Boleto | Inviável hoje | vencimento +1 dia × expiração 60 min |
| Cartão de crédito | **Não existe** | gateway rejeita |
| Cupons | Parcial | aplicação e concorrência bem testadas (MySQL real); **sem UI de cupom, sem CRUD admin** |
| Pedidos e estoque | Funcional com falhas | reserva/liberação corretas; transições de status sem validação |
| Estorno / reembolso | **Não existe** | ver C1 |
| Frete: cotação | Implementado (mock) | OAuth, token cifrado, cotação, vínculo ao pedido |
| Frete: etiqueta, postagem, rastreio | **Não existe** | envio manual pelo painel do ME |
| E-mails de pedido/pagamento | **Não existe** | só 3 e-mails de autenticação |
| Painel admin | Parcial | CRUD de produtos/categorias/eventos ok; dashboard com "Pedidos recentes" vazio fixo; eventos do admin não chegam ao site |
| `/eventos` público | Dados fixos | não consome a API |

---

## 4. Percentuais — matriz e metodologia

### 4.1 Metodologia
- **Conclusão** = proporção do escopo **necessário para vender em produção** que está implementada de forma funcional, avaliada por leitura de código e resultados de teste. Existir classe/endpoint não conta; conta comportamento verificado. A nota por área é julgamento do auditor, ancorado nas evidências da coluna "Evidência".
- **Peso** reflete risco e esforço do negócio (pagamentos e backend pesam mais).
- **Validação das integrações** usa a escala: 1 implementado = 20 · 2 mock = 40 · 3 banco descartável = 55 · 4 sandbox = 80 · 5 produção = 100. Validação **externa** só conta níveis 4–5 → **0%**.
- **Prontidão para produção**: conclusão geral descontada por "gates" não atendidos (validação externa, CI, achados CRÍTICO/ALTO abertos, dependências vulneráveis, entrega não commitada). Estimativa qualitativa: **≈ 40%**.

### 4.2 Matriz

| Área | Peso | Conclusão | Evidência | Pendências principais |
|---|---:|---:|---|---|
| Backend | 20 | 75% | 309 testes passam [EXEC]; locks/idempotência corretos [LEIT] | sem máquina de estados de pedido; preço promocional; cancelar pedido pago; N+1 e sem paginação; chamadas HTTP em transação |
| Frontend | 15 | 68% | `tsc`, lint (0 erros), build de 23 rotas [EXEC] | login não verificado; eventos fixos; carrinho sem revalidar preço; páginas sem erro/loading; sem testes |
| Banco de dados | 8 | 85% | V1–V26 + `validate` em MySQL 8.4 limpo; 24 ITs [EXEC] | V26 vs histórico Aiven [PEND]; V26 não idempotente; schema SQL da raiz obsoleto |
| Asaas | 12 | 55% | Pix/boleto/webhook/idempotência com mock + IT [EXEC/LEIT] | sem sandbox; sem cartão; sem estorno; bloqueio nunca resolvido; boleto inviável |
| Melhor Envio | 8 | 50% | OAuth, cifra AES-GCM, cotação [mock] | sem sandbox; sem etiqueta/postagem/rastreio; refresh token pode se perder (rollback) |
| Resend | 4 | 50% | 3 e-mails de auth [LEIT]; 1 teste | sem timeout; sem e-mails de pedido; domínio verificado [PEND] |
| Cloudinary | 4 | 55% | upload unsigned no frontend [LEIT] | sem backend, sem exclusão, preset a restringir [PEND]; fallback silencioso |
| Segurança | 10 | 70% | rotas admin testadas; BCrypt/HMAC; webhook fail-closed | JWT sem revogação; open redirect; tentativas não persistem; deps vulneráveis |
| Testes | 9 | 55% | 309 unit/slice + 24 IT [EXEC] | sem `AuthController`; sem oversell concorrente; ITs fora do `mvn test`; sem runner no frontend |
| Infraestrutura | 10 | 65% | imagem Docker constrói [EXEC]; Dockerfile não-root; prod endurecido [LEIT] | sem CI; perfis `production/homolog` sem properties; sem backup agendado |

Cálculo: Σ(peso × conclusão) / Σ pesos = **65,3%**. Subconjunto técnico (Backend, Frontend, Banco, Segurança, Testes, Infra; pesos somam 72) = **70%**.

### 4.3 Validação por nível (integrações)

| Integração | 1 Implementado | 2 Mock | 3 Banco descartável | 4 Sandbox | 5 Produção |
|---|:-:|:-:|:-:|:-:|:-:|
| Asaas — cobrança Pix | ✔ | ✔ | parcial | ✘ não validado | ✘ |
| Asaas — boleto | ✔ | ✔ | — | ✘ | ✘ |
| Asaas — cartão | ✘ não existe | — | — | — | — |
| Asaas — webhook (auth, duplicado, fila) | ✔ | ✔ | ✔ (duplicidade concorrente) | ✘ | ✘ |
| Asaas — estorno / chargeback | parcial (só recebe status) | ✔ | — | ✘ | ✘ |
| Melhor Envio — OAuth/cotação | ✔ | ✔ | schema apenas | ✘ | ✘ |
| Melhor Envio — etiqueta/postagem/rastreio | ✘ não existe | — | — | — | — |
| Resend — e-mails de auth | ✔ | mínimo (1 teste) | — | ✘ | ✘ |
| Cloudinary — upload | ✔ (frontend) | ✘ | — | ✘ | ✘ |

---

## 5. Estado de cada API

### 5.1 Asaas (pagamentos)

| Capacidade | Estado real |
|---|---|
| Criação de cobrança | `AsaasGatewayService` consulta por `externalReference`, cria cliente e cobrança; 5xx/timeout → `RECONCILIATION_REQUIRED`. Valor sempre `BigDecimal`, vindo do `order.total` persistido. |
| Pix / Boleto | Implementados. Boleto com `dueDate = hoje + 1`, mas pedido expira em 60 min (ver A5). |
| Cartão | Não implementado (rejeitado em `AsaasGatewayService` e `PaymentService`). |
| Consulta | `getPayment`/busca por referência; `getPayment` sem teste direto. |
| Idempotência | UNIQUE (pedido, chave), (pedido, tentativa), `external_id`; lock no pedido. Testado com mock e IT. |
| Autenticidade do webhook | Header `asaas-access-token` em tempo constante; sem token configurado falha fechado. |
| Duplicados / fora de ordem | PK `event_id`; worker consulta estado canônico e `PaymentTransitionPolicy` ignora regressão. |
| Fila durável | `SKIP LOCKED`, lease, backoff 1/5/15/60 min, máx. 8 — a parte de claim só é testada com mocks. |
| Atrasado / recusado / cancelado | Mapeado; mapeamento de "cancelada" **incerto** contra API real (A6). |
| Estorno / reembolso | Webhook só altera o status do pagamento. `PaymentRefundService.requestRefund` não tem chamadores e não chama o Asaas; `refundedAmount` nunca é gravado (LEIT: nenhum `setRefundedAmount` em `src/main`). |
| Chargeback | Estados na política, cria bloqueio. |
| Bloqueio financeiro | Só cria; nunca resolve (LEIT: nenhum `setActive(false)`/`resolvedAt` para `FinancialBlock`). |
| Reconciliação | Reativa (retry com mesma chave ou expiração); sem job periódico. |
| Indisponibilidade | Timeouts 3 s/10 s; falha incerta vira reconciliação. |
| Sandbox × produção | `AsaasProperties.validate()` exige URL e prefixo de chave coerentes. **Nunca houve chamada ao sandbox** (admitido em `docs/PREPARACAO_PRODUCAO.md`). |
| Pagamento manual (admin) | Tripla barreira + justificativa + auditoria + recusa com tentativa Asaas viva. Testado com mock e IT. |

### 5.2 Melhor Envio
- OAuth authorization_code correto no código: `state` de 256 bits (guardado só como hash, TTL 10 min, consumo atômico), redirect HTTPS, tokens AES-GCM com AAD por ambiente, `MelhorEnvioConfiguration` impõe `production`/`sandbox` coerentes. [AGENTE, leitura]
- **Cotação** (`POST /api/v2/me/shipment/calculate`): envia peso, dimensões e valor segurado; persiste opções por usuário (TTL 15 min); valida dono, expiração, CEPs, fingerprint do carrinho; UNIQUE(`orders.shipping_quote_option_id`).
- **Não existe**: compra de etiqueta, geração de postagem, impressão, rastreio, webhook do ME. O próprio checklist admite envio manual.
- `SimulatedShippingGateway` (padrão fora de prod) ignora CEP e dimensões.
- Pendente de sandbox: formato do corpo do token (JSON vs form), rotação do refresh token, formato de `delivery_time`/`custom_price`.

### 5.3 Resend
- Três e-mails em texto puro (código de verificação, MFA, reset). Sem template HTML, sem e-mails de pedido/pagamento/envio, sem retry, **sem timeout**. Remetente padrão `onboarding@resend.dev` só entrega ao dono da conta; domínio verificado [PEND]. Sem `RESEND_API_KEY`, cadastro e login retornam 503.

### 5.4 Cloudinary
- Somente frontend (`frontend/utils/cloudinary.ts`), upload **unsigned** com preset; o backend só guarda a URL (qualquer host/esquema). Não há exclusão, assinatura nem transformações. Fallback silencioso `dn_store` / `dn_store_preset` se as variáveis faltarem. Restrições do preset (formato, tamanho, pasta) [PEND] no console Cloudinary.

---

## 6. Estado do backend

**Pontos fortes verificados** [LEIT]: checkout trava carrinho e variantes `FOR UPDATE` em ordem determinística (sem deadlock), re-checa estoque após o lock; `consumeUsage` de cupom é `UPDATE` atômico e `releaseUsage` não vai abaixo de zero; `releaseReservedStock` idempotente (`stockReleased`); cancelamento/expiração/criação de pagamento serializados pelo lock do pedido; arredondamento `HALF_UP`; seeder não cria usuário admin (só categorias); dashboard com 5 consultas agregadas coerentes.

**Principais fraquezas**: ver seção 12 (A1–A4, A9 e os MÉDIO de backend).

---

## 7. Estado do frontend

- `tsc --noEmit` ok; `npm run lint` 0 erros e 1 aviso (`<img>` em `admin/eventos`); `npm run build` gera 23 rotas [EXEC].
- Sem runner de testes (só ESLint).
- Contrato com a API: conferido e consistente na maior parte; divergências: login 403 com corpo `EMAIL_VERIFICATION_REQUIRED`, `authService.me` (`/auth/me` inexistente), `cartService` inteiro morto e incompatível, preço promocional.
- Ausentes: `not-found.tsx`, `error.tsx`, `loading.tsx`, `robots`, `sitemap`, `generateMetadata` (todas as páginas compartilham o título da home).
- Identidade visual não foi tocada.
- Status por página [AGENTE, leitura estática]: Home parcial; Catálogo/busca/filtros funcional (sem paginação/debounce); Detalhe parcial; Categorias só via `?categoryId=`; Carrinho parcial; Checkout parcial; Auth parcial; Recuperação de senha funcional; Conta parcial (sem tratamento de erro, pedidos sem detalhe); Favoritos funcional; Eventos fixos; Admin produtos/categorias/eventos funcionais; Admin pedidos/usuários parciais (sem estado de erro); Dashboard parcial.

---

## 8. Estado do banco

- **V1–V26 aplicam sem erro em MySQL 8.4.13 limpo e o Hibernate `validate` passa** [EXEC] (container descartável; já removido). Isso comprova compatibilidade de sintaxe e entidade↔migration **a partir do zero**.
- **Não comprova** compatibilidade da V26 com o histórico real do Aiven [PEND]: exige o perfil `readonly` e credenciais de leitura (não usadas por esta auditoria). A V26 não foi aplicada em lugar nenhum fora do container descartável.
- V26 [AGENTE, leitura]: sintaxe válida em MySQL 8.4 (não usa `ADD COLUMN IF NOT EXISTS`); tipos de FK idênticos (`VARCHAR(36)`); sem backfill. Riscos: **não idempotente e não atômica** (DDL tem commit implícito: se o `CREATE TABLE` falhar após o `ALTER`, a coluna fica aplicada e o re-run dá "Duplicate column"); falha se V26 já tiver sido aplicada manualmente; **o arquivo está não commitado** — editá-lo depois de aplicado causa erro de checksum no Flyway.
- Lacuna V19: aceita pelo Flyway; falharia só se algum ambiente tivesse V19 aplicado (o commit `1a81486` compara aplicado × classpath).
- `dn_store_schema.sql` (raiz) tem 17 `CREATE TABLE` contra ~26 reais: obsoleto.
- Índices: `orders(status, created_at)`, favoritos e carrinho únicos. Falta unique `(cart_id, product_variant_id)` (só protegido pela aplicação) e há `LIKE '%x%'` em `products(name)`.
- **Schema atual compatível com o código?** Para um banco criado do zero: **sim** [EXEC]. Para o Aiven: **não verificado** [PEND].

---

## 9. Segurança

**Verificado e correto** [AGENTE, leitura; itens centrais conferidos]: BCrypt para senhas; códigos e tokens de reset guardados como HMAC-SHA256 e comparados em tempo constante; sem segredo de fallback para JWT; teto de 24 h; usuário recarregado do banco a cada requisição; CORS por origem explícita sem credenciais; CSRF desabilitado coerente com Bearer; consultas `@Query` parametrizadas; erros sem stack trace em prod; logs com e-mail mascarado; Actuator só `health`; sem IDOR encontrado em pedidos, pagamentos, endereços, carrinho, favoritos e cotações; rotas `/api/admin/**` exigem ADMIN em URL e `@PreAuthorize` (testado em `AdminRoutesSecurityTest`).

**Fragilidades**: JWT sem revogação (troca de senha não derruba sessões); token em cookie legível por JS e sem CSP/headers de segurança; open redirect em `?next=`; enumeração de e-mail no cadastro; força bruta só limitada por IP em memória (sem lockout por conta, `change-password` sem limite); `GET /api/coupons/validate` enumerável e devolve a entidade; contador de tentativas de código não persiste (A2); Resend sem timeout (diferença de tempo em `forgot-password`).

**Dependências** [EXEC `npm audit --omit=dev`]: 8 vulnerabilidades (7 altas, 1 crítica) — `next` (versão ≤ 16.4.0 marcada crítica), `axios`, `follow-redirects`, `form-data`, `nanoid`, `postcss`, `sharp`, `source-map-js`. Backend: Spring Boot 3.4.0 e jjwt 0.11.5 desatualizados [AGENTE; não rodei scanner Maven]. SDK `mercadopago` sem uso.

**Segredos**: `contas_teste` **não está mais rastreado** (`git ls-files` vazio; `.gitignore:262`; commit `2329ee4`) — o `CLAUDE.md` está desatualizado nesse ponto — **mas as credenciais continuam no histórico do Git** [EXEC]. Rotacionar e garantir que essas contas não existam em produção.

---

## 10. Infraestrutura

- Dockerfile: Java 21, multi-stage, usuário não-root, `exec java`; **imagem construída com sucesso** [EXEC]; sem `HEALTHCHECK` (apontar a plataforma para `/actuator/health/liveness`).
- Backend precisa de container sempre ligado, **uma instância**, ≥ 1 GB (rate limit em memória + 3 `@Scheduled`); não serve Vercel/serverless. Frontend: Vercel com Root Directory `frontend`; o build de produção falha sem `NEXT_PUBLIC_API_URL` (correto).
- Perfis: `prod` bem endurecido (INFO, `validate`, shutdown gracioso, `forward-headers-strategy=native`, pool). **`production`, `homolog`, `homologacao` passam no validador mas não carregam `application-prod.properties`** (M13). TLS do banco `REQUIRED` não verifica certificado.
- Perfil `readonly` é real em 3 camadas (sem Flyway, `@Profile("!readonly")` no seeder e nos agendadores, Hikari read-only + sessão `READ ONLY`) e provado por IT com controle positivo [EXEC]. Ressalva: opt-in; um usuário do Aiven só com `SELECT` seria o ideal.
- `docker compose up` do README com `.env` apontando ao Aiven roda no perfil `local`: executa Flyway, seeder e agendadores **contra o banco real** [AGENTE].
- Backup: scripts `db-backup.sh`/`db-restore.sh` corretos, mas manuais (sem agendamento/criptografia/envio externo). Prazo do Aiven citado nos docs [PEND].
- Variáveis de ambiente usadas no código e ausentes do `.env.example`: `PAYMENT_GATEWAY`, `SHOW_SQL`, `SHIPPING_CLEANUP_INTERVAL_MS`, `SHIPPING_ORIGIN_ZIP` (legado), `auth.rate-limit.*`, `DB_PASSWORD` (plugin Flyway). `.env.example` copiado sem edição não sobe (falha fechado, corretamente).
- **Pronto para implantar sem mudança estrutural?** Sim para a estrutura; **não** sem os ajustes de configuração acima e sem a validação em sandbox.

---

## 11. Testes executados

| Verificação | Resultado | Nota |
|---|---|---|
| `./mvnw test` | **309 testes, 0 falhas, 0 erros** [EXEC] | unit/slice/MockMvc; sem banco |
| ITs MySQL 8.4 descartável (4 classes) | **24 testes, 0 falhas** [EXEC] | `PaymentSchemaMySqlIT` 9, `OrderCouponAndManualPaymentMySqlIT` 11, `ReadOnlyProfileMySqlIT` 3, `CartJsonSerializationMySqlIT` 1 |
| Flyway V1–V26 + Hibernate `validate` em 8.4 | OK [EXEC] | dentro dos ITs |
| Frontend `tsc --noEmit` | OK [EXEC] | |
| Frontend `npm run lint` | 0 erros, 1 aviso [EXEC] | `<img>` em `admin/eventos/page.tsx:94` |
| Frontend `npm run build` | OK, 23 rotas [EXEC] | com `NEXT_PUBLIC_API_URL` local |
| Docker build do backend | OK [EXEC] | imagem local `dn-store-audit:local` (não enviada a nenhum registro) |
| `npm audit --omit=dev` | 8 vulnerabilidades [EXEC] | ver seção 9 |
| Scanner de dependências Maven | **Não executado** | pendente |
| Testes de frontend / e2e | **Inexistentes** | sem runner |
| Qualquer chamada a sandbox/produção externa | **Nenhuma** | [PEND] |

Nota sobre a primeira rodada de ITs: falhou porque usei a senha do arquivo de propriedades; os ITs sobrescrevem a senha na própria classe (o `CLAUDE.md` não registra isso). Recriado o container com a senha correta, os 24 passam.

**Qualidade da cobertura (não confundir quantidade com cobertura).**
- Forte: concorrência de cupom (16 checkouts × 3 vagas, cancelamentos concorrentes, cancelamento × expiração), duplicidade de webhook, estados de pagamento, rotas admin × papéis, validador de deploy, perfil readonly.
- Fraca: `OrderServiceTest` e `OrderInventoryServiceTest` 100% mock; `InventoryLockingRepositoryTest` só verifica por reflexão que a anotação `@Lock` existe.
- **Sem nenhum teste**: `AuthController` (cadastro, login, MFA, reset, verificação), `UserService/UserController`, `CategoryService`, eventos, `FavoriteController`, `AccountController`, `ShippingController`, `CouponController`, `AdminController`, `PaymentRefundService`, `PaymentController`, scheduler de expiração com exceção inesperada, `ShippingDataCleanupScheduler`, **oversell concorrente de estoque**, checkout ponta a ponta, preço promocional.
- O IT `dashboardRevenueCountsOnlyPaidOrdersNetOfRefunds` passa, mas como nenhum código de produção grava `refundedAmount`, ele não prova que a receita líquida desconta estornos reais [AGENTE].

---

## 12. Problemas encontrados

Para CRÍTICO/ALTO: local, descrição, reprodução, impacto, correção. Para MÉDIO/BAIXO: formato resumido.

### CRÍTICO

**C1. Estornos e chargebacks não refletem no pedido; não existe fluxo de reembolso** [LEIT parcial + AGENTE]
- Local: `service/PaymentRefundService.java` (sem chamadores), `PaymentAttemptStateService` (`needsBlock` não inclui REFUNDED; `refundedAmount` nunca gravado), `AdminDashboardService`.
- Reproduzir: pagar um Pix, estornar no painel do Asaas, aguardar o webhook. O pedido permanece PAID/PROCESSING e pode ser enviado. O teste `applyWebhook_refundStatusesShouldNotChangeOrderLogistics` consagra esse comportamento.
- Impacto: perda financeira (mercadoria enviada de pagamento devolvido); dashboard não desconta estorno; solicitações duplicadas somariam mais que o recebido caso o serviço seja exposto.
- Correção: persistir estorno a partir do campo `refunds` do Asaas; webhook REFUNDED/CHARGEBACK deve abrir bloqueio e mover o pedido; implementar (ou remover) a chamada de reembolso; testes.

### ALTO

**A1. `OrderService.updateStatus` sem máquina de estados** [LEIT]
- Local: `service/OrderService.java` ~471–520. Só bloqueia `PAID`, estoque devolvido e bloqueio financeiro.
- Reproduzir (rota ADMIN): `PUT /api/orders/{id}/status {"status":"PROCESSING"}` num pedido `PENDING_PAYMENT`.
- Impacto: envio de pedido nunca pago; contorna a auditoria do pagamento manual (V26); entra na receita do dashboard (`REVENUE_STATUSES`).
- Correção: tabela de transições permitidas e 409 para o resto; auditar toda mudança de status (hoje só a confirmação manual é auditada).

**A2. Limite de tentativas de código não persiste** [LEIT]
- Local: `AuthController.java` linhas 150–161 (`verifyEmail`) e 187–196 (`verifyMfa`): `@Transactional` + incremento + `throw IllegalArgumentException` → rollback.
- Reproduzir: errar o código de verificação mais de 5 vezes; o contador volta a 0 (a única barreira é o rate limit de 30/5 min por IP). Não executado.
- Impacto: `verify-email` devolve JWT; brute force de 6 dígitos (10 min de validade) permite tomar conta recém-criada e não verificada.
- Correção: `noRollbackFor = IllegalArgumentException.class` ou transação `REQUIRES_NEW` para o contador; teste.

**A3. Usuário com e-mail não verificado fica sem saída no login** [LEIT]
- Local: `AuthController.java:109–117` devolve 403 com corpo `EMAIL_VERIFICATION_REQUIRED`; `frontend/app/auth/page.tsx:60–63` só lê `message` (e `authService.login` não trata 403).
- Reproduzir: cadastrar, não confirmar o código, tentar logar → "Erro ao fazer login. Verifique suas credenciais."; recadastrar → "Email já cadastrado".
- Impacto: cliente real bloqueado após cadastro incompleto.
- Correção: tratar 403 `EMAIL_VERIFICATION_REQUIRED` no `catch` (chamar `handleAuthResult`) ou retornar 200 com o status.

**A4. Preço promocional exibido mas não cobrado** [LEIT]
- Local: `OrderService.java` 252, 317, 374 usam `getPrice()`; `promotionalPrice` não é usado em nenhum cálculo de pedido; `ProductCard.tsx:141–152` e `FavoriteCard.tsx` exibem o promocional; o detalhe mostra só `price`.
- Reproduzir: cadastrar preço promocional, adicionar ao carrinho, finalizar.
- Impacto: cobrança acima do preço anunciado (risco de consumidor/CDC); sem validação `promocional < preço`.
- Correção: regra única de preço efetivo no backend (checkout, carrinho, seguro do frete) + validação; refletir no front. Requer decisão de negócio.

**A5. Boleto inviável com expiração de 60 min** [LEIT]
- Local: `AsaasGatewayService.java:73` (`dueDate = hoje+1`) × `payment.order-expiration-minutes=60`.
- Impacto: pedido expira e a cobrança é cancelada antes da compensação (1–3 dias úteis).
- Correção: expiração por método (boleto ≥ vencimento + compensação) ou desabilitar boleto até corrigir.

**A6. Mapeamento de cobrança cancelada pode travar a expiração** [AGENTE; PEND sandbox]
- Local: `PaymentStatus.fromAsaas` (`REFUSED`,`DELETED`→FAILED), `parseProviderPayment` não lê `deleted`.
- Hipótese: o Asaas devolve a cobrança removida com `deleted:true` e status inalterado; então `cancelPayment` devolve PENDING, o pedido não expira e o estoque fica preso, com o job repetindo a cada minuto. Só confirmável em sandbox. Os testes só mockam `FAILED`.
- Correção: tratar `deleted==true` como FAILED; validar em sandbox.

**A7. `FinancialBlock` nunca é resolvido** [LEIT]
- Impacto: um bloqueio (OVERDUE→PAID, REFUND_DENIED→PAID, CHARGEBACK_REVERSED, conflito de confirmação manual) impede PROCESSING/SHIPPED/DELIVERED para sempre; saída só por SQL.
- Correção: endpoint admin de resolução com auditoria; liberação automática quando o estado normaliza.

**A8. Webhook PAID em pedido EXPIRED/CANCELLED sem alerta** [AGENTE]
- Local: `PaymentAttemptStateService.applyLogisticsTransition` (retorna cedo) / `updateFinancialBlock`.
- Impacto: dinheiro recebido, pedido não atendido, estoque devolvido, sem PAYMENT_REVIEW nem bloqueio.
- Correção: PAID nesses estados → PAYMENT_REVIEW + bloqueio/alerta de estorno.

**A9. Cancelar pedido pago não devolve estoque/cupom nem reembolsa** [LEIT]
- Local: `OrderService.updateStatus` (liberação só a partir de `PENDING_PAYMENT`/`PAYMENT_FAILED`).
- Impacto: estoque preso, cupom consumido, dinheiro retido sem rastro.
- Correção: bloquear CANCELLED nesses estados até existir fluxo de reembolso, ou liberar e reembolsar.

**A10. Refresh token do Melhor Envio pode se perder por rollback** [AGENTE; PEND sandbox]
- Local: `OrderService.quoteShipping` (`@Transactional`), `MelhorEnvioTokenStore`.
- Impacto: se o refresh ocorre e a cotação falha depois, o novo par de tokens não é commitado; se o ME invalida o anterior, o checkout cai até reautorização do admin.
- Correção: `REQUIRES_NEW` para persistir o refresh; cotar fora da transação (resolve também M8).

**A11. Credenciais de `contas_teste` no histórico do Git** [EXEC]
- Local: commits anteriores a `2329ee4`; `docs/PREPARACAO_PRODUCAO.md` indica repositório público (não verificado).
- Correção: rotacionar; garantir que as contas não existam em produção; avaliar reescrita de histórico.

**A12. Dependências vulneráveis** [EXEC npm audit]
- `next` (marcado crítico), `axios`, `sharp` etc. Spring Boot 3.4.0 e jjwt 0.11.5 desatualizados [AGENTE].
- Correção: `npm audit fix` / atualizar Next; subir Spring Boot; rodar `dependency-check`.

**A13. Sem CI; ITs não rodam automaticamente; Dockerfile ignora testes** [LEIT]
- Impacto: riscos financeiros (concorrência de cupom, webhook, schema) só são testados manualmente.
- Correção: workflow com `mvn test` + MySQL de serviço para os ITs + `lint/build` do frontend.

**A14. Entrega não commitada** [EXEC git status]
- 45 arquivos modificados/novos, incluindo V26, perfil `readonly`, `AdminOrderPaymentController`, `OrderManualPaymentService`, 6 testes e docs que citam esses itens.

### MÉDIO (resumo)

| ID | Local | Descrição | Correção |
|---|---|---|---|
| M1 | `ProductService.mergeVariants` | Edição de produto sobrescreve estoque com valor absoluto (lost update, risco de oversell) | `@Version` ou ajuste por delta |
| M2 | `ProductController` PUT/POST | Entidade como corpo; `active` default true reativa produto; omitir `category` zera; sem `@Valid`; preço 0 permitido | DTOs + validação |
| M3 | `OrderService.findAll/findByUserId`, `ProductService.search` | Sem paginação e com N+1 (entidade completa serializada) | `Pageable` + fetch join/DTO |
| M4 | `PaymentExpirationScheduler` | Captura só 3 tipos de exceção; um pedido problemático aborta a varredura (sem `ORDER BY`/`LIMIT`) | capturar `RuntimeException` por pedido |
| M5 | `PaymentExpirationService` | HTTP ao Asaas dentro de `@Transactional` e `payment.setStatus` direto contornando `PaymentTransitionPolicy` | mover para fora da transação; usar `PaymentAttemptStateService` |
| M6 | `GlobalExceptionHandler` | Exceções de framework (415/406, lock timeout) viram 500 | mapear para 415/406/409/503 |
| M7 | `CouponController` | Enumerável, devolve entidade; sem limite por usuário, sem CRUD admin | DTO mínimo + rate limit |
| M8 | `OrderService.quoteShipping`, `MelhorEnvioTokenStore` | `SELECT … FOR UPDATE` no token serializa todas as cotações durante HTTP externo (até 10 s) | lock só no refresh |
| M9 | `ProductService.delete` | Exclusão física quebra por FK ou apaga em cascata | soft delete |
| M10 | Reconciliação Asaas | Só reativa; webhook de cobrança sem `external_id` local vai a FAILED após 8 tentativas | job periódico + busca por `externalReference` |
| M11 | `AuthController`/`ResendEmailService` | Resend sem timeout e dentro de `@Transactional`; diferença de tempo em `forgot-password` | timeouts + envio pós-commit |
| M12 | `ShippingController`/`OrderController` | Cotação sem rate limit/cache; insere N linhas por chamada | rate limit por usuário; reuso |
| M13 | Perfis | `production`/`homolog`/`homologacao` não carregam `application-prod.properties` (rate limit pelo IP do proxy, log DEBUG, sem shutdown gracioso) | remover aliases ou criar arquivos |
| M14 | TLS do Aiven | `REQUIRED` não verifica certificado | `VERIFY_CA` + CA do Aiven |
| M15 | `frontend/app/auth/page.tsx:36` | Open redirect via `?next=` (comportamento do `router.push` não executado) | aceitar só caminhos `/…` |
| M16 | JWT | Sem revogação; troca/reset de senha não invalida sessões | claim de versão de senha |
| M17 | Frontend sessão | Token em cookie legível por JS, sem CSP/headers; `user_data` do localStorage não validado | headers em `next.config.ts` |
| M18 | Cadastro | Enumeração de e-mail (409); força bruta só por IP, `change-password` sem limite | resposta genérica; lockout por conta |
| M19 | Frontend `/eventos` | Dados 100% fixos (`lib/data.ts`); CRUD de eventos do admin não afeta o site; "Próxima etapa" por índice; links de mapa das etapas 1–2 aparentam apontar para cidades erradas | consumir `GET /events` |
| M20 | Frontend checkout | Retomada por `sessionStorage` abre pedido antigo; expirado/cancelado sem botão "novo checkout" | limpar chave; botão |
| M21 | Frontend | Sem tela de retomada de pagamento pendente (QR/boleto perdido ao fechar a aba) | `/conta/pedidos/[id]` |
| M22 | Frontend carrinho | "Frete: Grátis" fixo e falso; preço local sem revalidação | "calculado no checkout"; revalidar |
| M23 | `Footer.tsx` | Exibe Visa/Mastercard/Mercado Pago; só há Pix e boleto | corrigir bandeiras |
| M24 | Frontend admin | Dashboard mostra zeros se a API falha; "Pedidos recentes" `[]` fixo; "Ver todos" sem ação; listas sem estado de erro; mudança de status/cancelamento sem confirmação | estados de erro; confirmação |
| M25 | Frontend/`ProductService` | Peso/dimensões vazios viram `0`/`null`; backend não valida; cotação do ME falha com 400 para o cliente | validar no `ProductService` |
| M26 | Cloudinary | Preset unsigned, fallback hardcoded, `console.log` de cloud/preset, sem exclusão, uploads órfãos | falhar sem variáveis; restringir preset; considerar upload assinado |
| M27 | Webhooks Asaas | Eventos desconhecidos → `REQUIRES_REVIEW`; evento sem objeto `payment` → 502 (pode pausar a fila); `DEPLOY.md:116` diz "revisão" mas o código marca FAILED | revisar lista de eventos contra a documentação |
| M28 | Pagamento duplicado | PAID em tentativa distinta do mesmo pedido não abre bloqueio | checar outro payment PAID |
| M29 | `payment_webhook_events` | Payload bruto (PII) sem retenção/limpeza | rotina de limpeza |
| M30 | V26 | Não idempotente/atômica e não commitada | dividir em 2 migrations ou usar padrão `information_schema` da V20 |

### BAIXO (resumo)
Fusos mistos (`LocalDateTime.now()` × UTC); `addItem` não valida quantidade acumulada; eventos inativos acessíveis por `GET /events/{id}` e URLs sem validação de esquema; `OrderController.updateStatus` faz `body.get` antes do null-check; `VALID_STATUSES` duplica o enum; `ProductResponse` expõe estoque exato; sem proteção contra rebaixar/excluir o último admin; CPF/CNPJ sem dígito verificador e concatenado na query do Asaas; `/api/zip-code` sem rate limit e `RestTemplate` sem timeout (log com entrada bruta); sem validação de startup para `RESEND_FROM`; `.env.example` com `SHIPPING_GATEWAY=melhorEnvio`; README manda `docker compose up` sem alertar sobre o perfil `local` contra o Aiven; `open-in-view` padrão true; `<main>` aninhado; classes Tailwind inexistentes (`text-brand-red`); `console.log` em produção; `alert()` bloqueante; `<label>` sem `htmlFor` no checkout; favorito invisível em touch/teclado; seta do Hero aponta para `#destaques` mas a seção tem `id="s"` (conferido); `/checkout/sucesso` órfã; botão "Conectar Melhor Envio" aparece mesmo com gateway `simulated`; scripts de backup sem agendamento; `package.json` raiz residual; catálogo SQL destrutivo empacotado no JAR; `GET /api/categories/{id}` exige autenticação (provável bug funcional).

### Documentação desatualizada ou incorreta
- `CLAUDE.md`: `contas_teste` não é mais rastreado; há 4 ITs (não só `PaymentSchemaMySqlIT`) e eles sobrescrevem a senha na própria classe; `swr` é usado no frontend.
- `README.md` (~l.213): "Java 17"; `DOCUMENTACAO_BACKEND.md`: "Java 17+", cita `V16__support_payment_retries…` (é V18), afirma fallback de `JWT_SECRET` para o segredo de desafio (falso — a propriedade é obrigatória), não cobre `readonly`, V26, cupons nem pagamento manual.
- `PREPARACAO_PRODUCAO.md` e `CHECKLIST_LANCAMENTO.md`: dizem que falta commitar a remoção das credenciais (já foi) e citam "253 testes".
- `MELHOR_ENVIO_PRODUCAO_CHECKLIST.md`: lista migrations "V1–V18 e V20–V25", sem V26.

---

## 13. Pendências (o que não foi comprovado)

1. Qualquer chamada a Asaas, Melhor Envio, Resend ou Cloudinary reais/sandbox. [PEND]
2. Compatibilidade da V26 com o histórico real do Aiven (exige perfil `readonly`/credenciais). [PEND]
3. Domínio verificado no Resend; restrições do preset Cloudinary; prazo/plano do Aiven. [PEND]
4. Comportamento do ME (formato do token, rotação do refresh, formato de prazo) e do Asaas (campo `deleted`, lista completa de eventos, `refunds`). [PEND]
5. Scanner de dependências do backend (Maven). Não executado.
6. Achados marcados [AGENTE] (A6, A8, A10, M10, M27–M29 e vários BAIXO) não foram reconfirmados individualmente; os [LEIT] foram conferidos no código. Nenhum achado de lógica foi reproduzido em execução.
7. Responsividade, overflow e acessibilidade **em navegador** não foram exercitados (auditoria estática do frontend; o app não foi aberto em browser).
8. Desempenho sob carga e testes de concorrência de estoque (inexistentes).
9. O container MySQL `dn_store_db` (mysql:8.0) que já estava ativo na máquina **não foi tocado**.

---

## 14. Riscos para produção (resumo)

| Risco | Gravidade | Mitigação mínima |
|---|---|---|
| Estorno/chargeback não refletem no pedido (C1) | Crítico | implementar fluxo ou bloquear envio manualmente até existir |
| Integrações nunca validadas em sandbox | Alto | roteiro de sandbox antes do go-live |
| Pedido enviável sem pagamento (A1) / cancelamento de pago sem estorno (A9) | Alto | máquina de estados |
| Boleto inviável (A5) | Alto | desabilitar boleto ou ajustar prazos |
| Preço promocional não cobrado (A4) | Alto | regra única ou ocultar promo |
| Conta nova pode ser tomada (A2); usuário preso no login (A3) | Alto | correções pequenas e localizadas |
| Dependências vulneráveis; sem CI | Alto | atualizar; pipeline |
| Perfil errado em produção reduz proteção (M13) | Médio | usar apenas `prod` |
| `docker compose up` com `.env` do Aiven roda Flyway/seeder/agendadores no banco real | Médio | usar `readonly` para consulta; documentar |
| V26 não commitada e não idempotente | Médio | commitar; tornar idempotente; testar em cópia do histórico |

---

## 15. Plano de ação priorizado (para a fase de correções — **aguarda autorização**)

**Fase 0 — Higiene (sem risco, rápido)**
- Rotacionar credenciais de `contas_teste`; commitar o que está pendente (V26, `readonly`, pagamento manual, testes) em branch separada.
- Atualizar `CLAUDE.md` e docs apontados na seção 12.

**Fase 1 — Bloqueadores de demo/produção (correções pequenas e localizadas)**
1. A3 login não verificado (frontend).
2. A2 contador de tentativas (`noRollbackFor`) + testes do `AuthController`.
3. A1 + A9 máquina de estados de pedido e auditoria de status.
4. A4 preço promocional (decisão de negócio primeiro) + A5 prazo do boleto (ou desabilitar boleto).
5. M15 open redirect; M19 eventos via API; M22 "Frete: Grátis"; M23 rodapé; M24 dashboard.

**Fase 2 — Financeiro (maior esforço, o mais importante)**
6. C1 + A7 + A8: fluxo de estorno, bloqueio com resolução, PAID em pedido expirado → revisão; `PaymentRefundService` com testes e `refundedAmount` persistido.
7. A6/M10/M27: validar mapeamento do Asaas em sandbox; job de reconciliação.

**Fase 3 — Integrações em sandbox (exige credenciais do usuário)**
8. Roteiro Asaas sandbox: Pix, boleto, webhook, estorno. Roteiro ME sandbox: OAuth, cotação, refresh. Resend com domínio verificado. Preset Cloudinary restrito.
9. A10/M8: tirar HTTP do ME de dentro da transação.

**Fase 4 — Plataforma**
10. CI (testes + ITs em MySQL + lint/build), atualizar Spring Boot/Next/axios, remover SDK `mercadopago`, scanner de dependências, M13/M14 de perfis e TLS, CSP e headers (M17), JWT com versão de senha (M16).
11. M3 paginação/N+1, M1 lost update de estoque, teste de oversell concorrente.
12. V26: tornar idempotente e **validar contra uma cópia do histórico do Aiven** antes de aplicar.

**Fase 5 — Pós-lançamento**
13. Etiqueta/postagem/rastreio do Melhor Envio, e-mails de pedido, cartão de crédito, testes de frontend/e2e, backup agendado e criptografado.

---

*Fim do relatório. Nenhuma correção foi iniciada; aguardo autorização para a fase de correções.*
