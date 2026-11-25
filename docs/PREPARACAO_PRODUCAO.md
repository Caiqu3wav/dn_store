# DN Store — Preparação para Produção

> **Legenda.** `[x]` = implementado/revisado **e verificado** (evidência registrada no item). `[ ]` = pendente; o motivo está no item. Atualizado em 17/11/2026.

## Objetivo

Preparar toda a estrutura da DN Store para lançamento em produção, mantendo a arquitetura atual e evitando retrabalho.

A meta é que, no momento do lançamento, seja necessário essencialmente configurar as credenciais definitivas, URLs, domínios e serviços de hospedagem.

## 1. Infraestrutura

- [x] Preparar backend Java 21 para hospedagem Docker.
  - Dockerfile migrado de Java 17 para 21 (alinhado ao `pom.xml`), usuário sem root, `JAVA_OPTS`, `.dockerignore` sem segredos.
  - Evidência: `docker build` OK; container subiu contra MySQL 8.4 descartável, aplicou as 24 migrations Flyway e passou no `ddl-auto=validate`; processo roda como `app`.
  - Bug de produção corrigido: com `shipping.gateway=melhorEnvio` (obrigatório em prod) o app não subia ("No default constructor found" em `MelhorEnvioOAuthClient`). Teste de regressão `MelhorEnvioOAuthClientWiringTest`; o perfil `prod` com config fictícia de produção sobe e responde `UP`.
  - Medição: com limite de 512 MB o app sobe, mas ocioso usa ~429 MiB; recomendado 1 GB.
- [x] Preparar frontend Next.js para Vercel.
  - Corrigido erro de tipos que quebrava `next build` (`window.__bhldScript`) e 3 erros de lint. Build de Production na Vercel agora falha cedo se `NEXT_PUBLIC_API_URL` faltar. Criado `frontend/.env.example`.
  - Evidência: `npm run build` OK (23 rotas); `npm run lint` com 0 erros (1 aviso `<img>` pré-existente); build com `VERCEL_ENV=production` falha sem a variável e passa com ela.
  - Pendente para a Seção 8: o deploy real na Vercel (Root Directory = `frontend`).
- [x] Configurar perfil Spring Boot de produção.
  - `application-prod.properties` (logs INFO, `ddl-auto=validate`, pool, encerramento gracioso, erros sem detalhes) e `application-staging.properties` (mesmas proteções com sandbox). `PORT` da plataforma respeitada.
  - Evidência: container em `staging` sem nenhuma linha DEBUG do app; erro 401 sem vazar detalhes internos; `prod` boot validado (ver item do Docker).
- [x] Configurar HTTPS, CORS e headers de proxy.
  - CORS tolera espaços e `/` final nas origens (`BackendConfigCorsTest`). `forward-headers-strategy=native` para HTTPS/IP real atrás do proxy.
  - Evidência: preflight de origem permitida → 200 com `Access-Control-Allow-Origin`; origem estranha → 403; `X-Forwarded-For` resolvido no log de segurança (`ip=203.0.113.9`).
  - Observação: PATCH não foi adicionado ao CORS porque nenhum endpoint nem chamada do frontend usa PATCH. O certificado HTTPS é da plataforma; a verificação ponta a ponta fica na Seção 8.
- [x] Implementar health checks.
  - Actuator com só `health` exposto, sem detalhes, público via `SecurityConfiguration`. Para a plataforma: `/actuator/health/liveness`.
  - Evidência: `ActuatorHealthSecurityTest` (5 testes; falha se a regra for removida); no container, `/actuator/health`, `/liveness` e `/readiness` → `UP`, `/actuator/env` → 401.
- [x] Organizar variáveis de ambiente.
  - `backendJava/.env.example` completo (perfil, PORT, FRONTEND_URL, Resend, pool, JVM) e tabelas por serviço em `docs/DEPLOY.md`.
  - Evidência: variáveis conferidas contra `application.properties` e o código; nenhum valor real nos exemplos.
- [x] Preparar documentação de deploy.
  - `docs/DEPLOY.md`: arquitetura, perfis, variáveis, Vercel, Docker, webhook do Asaas, OAuth do Melhor Envio, troca de domínio, operação.
  - Será complementado pelo guia de backup/recuperação (Seção 2).

## 2. Banco de dados

- [x] Preservar MySQL e migrations Flyway.
  - Nenhuma migration foi criada ou alterada. Nenhuma apaga dados (única ocorrência destrutiva: um `DROP INDEX` em V18, troca de índice).
  - Evidência: as 24 migrations (V1–V18, V20–V25; não existe V19) aplicadas em MySQL 8.4 vazio + `ddl-auto=validate` OK; `PaymentSchemaMySqlIT` 9/9 em MySQL 8.4 real. O teste tinha contagem fixa desatualizada (22 migrations, versões 1–22) e agora compara com os arquivos do classpath.
  - Atenção (verificação no banco do Aiven, feita por você): V19/V20 já existiram com outro conteúdo e foram renomeadas para V22/V23. Consulta de conferência em `docs/BACKUP_RECUPERACAO.md` (seção 4).
- [x] Revisar configurações de produção.
  - Novo `DeploymentConfigurationValidator` (staging/prod): exige TLS no banco, segredos fortes e não-exemplo, e `FRONTEND_URL`/CORS em HTTPS público. Pool Hikari dimensionável (`DB_POOL_SIZE`). Fuso da imagem fixado em `America/Sao_Paulo` (o sistema usa `LocalDateTime` e os dados existentes foram gerados no horário de Brasília).
  - Evidência: `DeploymentConfigurationValidatorTest` (8 testes); no container, config insegura → recusa subir com mensagem sem expor o valor; config correta com TLS real → `UP`; log com `-03:00`.
- [x] Preparar backup e recuperação.
  - `backendJava/scripts/db-backup.sh` e `db-restore.sh` (restauração só em banco vazio e com `CONFIRM_RESTORE=yes`) + `docs/BACKUP_RECUPERACAO.md`. `*.sql.gz` ignorado no Git.
  - Evidência: backup → restauração em banco novo → contagem exata de linhas idêntica nas 26 tabelas e 24 registros no `flyway_schema_history`; restauração recusada sem confirmação e em banco não vazio.
- [x] Verificar criação do primeiro administrador.
  - Verificado: não existe admin padrão nem seed; o cadastro sempre cria `USER` e só um ADMIN promove outro. O primeiro admin é promovido por SQL (procedimento em `BACKUP_RECUPERACAO.md`, seção 5).
  - Evidência: SQL testado no schema real: promove a conta verificada e afeta 0 linhas em e-mail não verificado.
- [ ] Avaliar plano e hospedagem definitiva do banco.
  - Pendente de decisão sua. Dados verificados em 17/11/2026: o MySQL gratuito do Aiven foi descontinuado; serviços free restantes são desligados e **têm dados e backups apagados em 21/12/2026**. O plano pago de entrada (Developer, US$ 5/mês segundo a página de preços do Aiven) mantém dados e conexão. Não há região no Brasil. Confirme o plano atual no console do Aiven. Não contratei nada.

## 3. Integração de pagamentos — Asaas

- [x] Revisar integração existente.
  - Revisados `AsaasGatewayService`, `PaymentService`, `PaymentController`, ingestão/worker/transações de webhook e `PaymentStatus`. Arquitetura preservada. Nenhuma chamada real ao Asaas foi feita.
- [x] Preparar autenticação por API.
  - Header `access_token` + `User-Agent`; `AsaasProperties` falha ao iniciar se ambiente, URL base e prefixo da chave (`$aact_hmlg_`/`$aact_prod_`) não combinarem, e exige chave e token de webhook em `production`. Timeouts de conexão/leitura configuráveis.
  - Evidência: `AsaasPropertiesTest` (rejeita config incoerente); container `prod` com config fictícia de produção sobe. A chave real é inserida só na Seção 8.
- [x] Revisar criação e consulta de cobranças.
  - Antes de criar, consulta por `externalReference` (evita cobrança duplicada); confere valor, moeda e referência da cobrança retornada; erro 5xx/timeout vira `RECONCILIATION_REQUIRED` em vez de falha definitiva; CPF/CNPJ normalizado e validado; só Pix e boleto.
  - Evidência: `AsaasGatewayServiceTest` (14), `PaymentServiceTest`. Pendente fora do código: validar uma cobrança real no sandbox do Asaas com a sua chave de sandbox (Seção 7/8).
- [x] Revisar estados de pagamento.
  - Máquina de estados em `PaymentTransitionPolicy`/`PaymentAttemptStateService`, mapeamento Asaas→interno em `PaymentStatus`. Evidência: `PaymentStatusTest`, `PaymentTransitionPolicyTest`, `PaymentAttemptStateServiceTest` (17), `PaymentExpirationServiceTest`; todos passando na suíte (ver relatório de testes).
- [x] Preparar webhooks.
  - `POST /api/payment/webhook`, autenticado por `asaas-access-token` (comparação em tempo constante), limite de 256 KB, gravação durável e processamento assíncrono com lease/backoff. Ajuste feito: evento autenticado com `status` desconhecido agora é gravado em vez de rejeitado com 400, já que o worker consulta o estado atual no Asaas (a documentação do Asaas diz que a fila pode ser interrompida por erros). Teste atualizado.
  - Procedimento e cuidados (habilitar só `PAYMENT_*`, reabilitar fila) em `docs/DEPLOY.md`. Pendente na Seção 8: cadastrar a URL real no painel do Asaas.
- [x] Garantir idempotência.
  - Evento: `event_id` único (duplicado vira 200 sem reprocessar); tentativa: chave de idempotência por pedido; fila protegida por lease e "fencing" por número de tentativa.
  - Evidência: `PaymentSchemaMySqlIT` (9/9 em MySQL 8.4 real, incluindo `webhookEventIdIsPersistentlyUnique` e unicidade por pedido/tentativa/id externo), `PaymentWebhookIngestionServiceTest`.
- [x] Revisar segurança e tratamento de erros.
  - Erros do provedor viram 502 genérico sem detalhes; mensagens do worker têm tokens/chaves redigidos; sem CPF/payload/token nos logs de pagamento (verificado por busca); pagamento só visível ao dono do pedido ou ADMIN.
  - Observação: sem limitação de taxa (rate limit) no webhook; mitigado pelo token. Fica como melhoria opcional.

## 4. Integração logística — Melhor Envio

- [x] Revisar OAuth 2.0.
  - `state` aleatório (32 bytes), guardado só como hash SHA-256, com TTL, vinculado ao ambiente e de uso único; `/authorize` exclusivo para ADMIN; o callback valida o state antes de qualquer outra coisa; erros do provedor não vazam detalhes. Corrigido o bug de inicialização do `MelhorEnvioOAuthClient` (ver Seção 1).
  - Evidência: `MelhorEnvioOAuthControllerTest`, `MelhorEnvioOAuthServiceTest`, `MelhorEnvioOAuthClientTest`, `MelhorEnvioOAuthClientWiringTest`.
- [x] Preparar callback de produção.
  - `GET /api/integrations/melhor-envio/callback` é público (necessário), retorna 400 sem state válido e redireciona para `<FRONTEND_URL>/admin?integration=melhor-envio&status=...`. A configuração exige HTTPS, host oficial do ambiente e escopo `shipping-calculate`.
  - Evidência: `MelhorEnvioConfigurationTest`; container `prod` com config fictícia sobe e o callback sem state responde 400. Pendente na Seção 8: cadastrar o Redirect URI real no aplicativo do Melhor Envio.
- [x] Revisar armazenamento e renovação de tokens.
  - Tokens cifrados com AES-GCM (nonce aleatório + AAD por ambiente/tipo); refresh antecipado e serializado por lock de linha; 401 gera uma única tentativa de renovação; falha definitiva marca `REAUTH_REQUIRED`; erros não expõem tokens.
  - Evidência: `MelhorEnvioTokenStoreTest`, `MelhorEnvioShippingGatewayTest` (ex.: erro do provedor não expõe o token). Atenção operacional: `MELHOR_ENVIO_TOKEN_ENCRYPTION_KEY` não pode mudar depois de autorizar (documentado).
- [x] Preparar cotação de fretes.
  - Cotação via `/api/v2/me/shipment/calculate` com dados de peso/dimensão vindos do cadastro do produto; opções inválidas são descartadas e, sem nenhuma opção válida, falha fechada.
  - Evidência: `MelhorEnvioShippingGatewayTest`, `OrderServiceTest` (validação de produtos). Pendente: homologação real no sandbox com credenciais (Seção 7/8).
- [x] Integrar frete aos pedidos.
  - Já existente: a cotação é persistida (snapshot) e o checkout usa o valor cotado; rejeita cotação expirada, de outro cliente, com carrinho/CEP alterados ou de ambiente diferente.
  - Evidência: `OrderServiceTest` (`checkout_rejectsQuoteWhenCartChangedAfterCustomerAcceptedIt`, `checkout_rejectsQuoteThatBelongsToAnotherCustomer`), `OrderControllerShippingTest`.
- [ ] Preparar compra e impressão de etiquetas.
  - **Não implementado** e não será feito sem sua decisão: é funcionalidade nova (carrinho/checkout/geração/impressão no Melhor Envio), exige escopos adicionais, dados fiscais e do remetente, e só pode ser validado com credenciais de sandbox. Comprar etiqueta em produção gera custo. Alternativa de lançamento: operar etiquetas manualmente pelo painel do Melhor Envio (documentado em `MELHOR_ENVIO_PRODUCAO_CHECKLIST.md`).
- [ ] Preparar rastreamento.
  - **Não implementado**; depende da compra de etiquetas (código de rastreio). Mesmo motivo do item acima. Interim: informar o código de rastreio ao cliente manualmente.

## 5. Serviços complementares

- [x] Preparar Resend para envio de e-mails.
  - Variáveis `RESEND_API_KEY`/`RESEND_FROM` documentadas; sem chave, todo envio falha com 503 (fail-closed, sem "fingir" que enviou). Evidência: novo `ResendEmailServiceTest`.
  - **ATENÇÃO (depende de você):** o remetente de teste `onboarding@resend.dev` só entrega para o e-mail da própria conta Resend (a documentação do Resend retorna 403 para outros destinatários). Como cadastro e login dependem de código por e-mail, **sem um domínio verificado no Resend os clientes não conseguem se cadastrar**. Seus planos de começar sem domínio próprio funcionam para o site e a API, mas não para e-mail; é necessário um domínio (custo) para verificar no Resend. Não contratei nada.
- [x] Preparar Cloudinary para imagens.
  - Upload direto do navegador com preset unsigned. Variáveis `NEXT_PUBLIC_CLOUDINARY_CLOUD_NAME`/`NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET` agora documentadas em `frontend/.env.example` e `docs/DEPLOY.md` (o código tem fallback `dn_store`/`dn_store_preset`, que provavelmente não é a sua conta). `res.cloudinary.com` já está liberado em `next.config.ts`.
  - Pendente (console do Cloudinary, por você): restringir o preset (formatos, tamanho, pasta).
- [x] Revisar autenticação e recuperação de senha.
  - Códigos e tokens (verificação, MFA, redefinição) guardados com HMAC-SHA256, expiram, têm limite de 5 tentativas, cooldown de reenvio de 60 s e "esqueci a senha" não revela se o e-mail existe. Adicionado: limite de tentativas por IP em `POST /api/auth/**` (login 10 e demais 30 por 5 min, resposta 429 com `Retry-After`).
  - Evidência: `AuthRateLimitFilterTest` (5); no container, 10 logins → 401 e do 11º em diante → 429, outro IP não afetado.
  - Observações: o contador é em memória (reinicia com o processo; adequado à instância única). Tokens JWT já emitidos continuam válidos até expirar (máx. 24 h) após troca de senha; não há revogação.
- [x] Revisar painel administrativo.
  - Guarda no cliente (`app/admin/layout.tsx`) + backend como autoridade: todas as rotas administrativas exigem ADMIN por URL e por `@PreAuthorize` (ver Seção 6). O botão "Conectar Melhor Envio" existe; o painel não mostra o estado da conexão (só o resultado do retorno do OAuth).

## 6. Segurança

- [x] Revisar JWT e autenticação.
  - HS256, assinatura sempre validada, validade máxima de 24 h imposta na inicialização, papel (ADMIN/USER) lido do banco a cada requisição (promoção/remoção valem na hora). Segredo agora validado em staging/prod (Base64, ≥ 32 bytes, diferente do de desenvolvimento do repositório).
  - Frontend: o cookie `auth_token` passou a usar `sameSite=strict` e `secure` em HTTPS. Continua legível por JavaScript (não é HttpOnly); migrar para cookie HttpOnly exige mudança de arquitetura (CSRF etc.) e não foi feito. Risco residual: XSS poderia ler o token.
- [x] Proteger credenciais e secrets.
  - `.env.example` só com placeholders; `.dockerignore` impede `.env` na imagem; `.env`, `contas_teste` e `*.sql.gz` ignorados no Git; validador de implantação recusa segredos fracos/de exemplo; varredura dos arquivos versionados por padrões de chaves (Asaas, Resend, AWS, chaves privadas) sem achados reais.
- [x] Revisar permissões administrativas.
  - Levantamento de todos os endpoints: os administrativos tinham `@PreAuthorize`, mas `GET /api/products/admin/**` e `GET /api/events/all` ficavam abertos na regra de URL (liberação pública de leitura do catálogo). Adicionadas regras de URL exigindo ADMIN para leituras/mutações administrativas de produtos, categorias, eventos, pedidos e usuários.
  - Evidência: `AdminRoutesSecurityTest` (4 testes, 20 rotas × anônimo/USER/ADMIN + catálogo público); o teste falhou antes da correção e passa depois.
- [x] Revisar logs e dados sensíveis.
  - E-mails de clientes deixaram de aparecer nos logs (mascarados `m***@dominio` ou substituídos por `userId`); sem logs de CPF, tokens ou payloads; sem DEBUG/`printStackTrace` no código; nível INFO em staging/prod; erros do provedor não vazam detalhes.
  - Evidência: `LogSanitizerTest`; no container os logs mostram `n***@example.com`. Observação: o IP do cliente continua nos logs de segurança (necessário para investigar abusos); trate conforme a sua política de privacidade/LGPD.
- [ ] Remover credenciais de teste versionadas.
  - Feito localmente: `contas_teste` foi retirado do índice do Git (`git rm --cached`) e adicionado ao `.gitignore`; o arquivo continua no seu disco. **Falta você** fazer o commit/push dessa remoção.
  - **Exposição:** o repositório no GitHub é **público** (acesso anônimo retorna 200) e o arquivo está no histórico desde 21/10/2026. Considere as credenciais comprometidas: troque as senhas dessas contas e confirme que elas não existem no banco de produção. Apagar do histórico exige reescrever o histórico (operação destrutiva e pública); não foi feito, aguardo sua decisão.
- [x] Revisar configurações de produção.
  - Perfis `prod`/`staging`, `DeploymentConfigurationValidator`, TLS obrigatório no banco, erros sem detalhes, health checks restritos, CORS por origem exata, encerramento gracioso (ver Seções 1 e 2).

## 7. Testes e validação

- [x] Executar testes automatizados.
  - Backend: `./mvnw test` → 253 testes, 0 falhas (eram 223 no início; +30 novos). Integração em MySQL 8.4 real (`-Dtest=PaymentSchemaMySqlIT,CartJsonSerializationMySqlIT`, exige MySQL local em 127.0.0.1:33316) → 10/10. Frontend não tem runner de testes; `npm run lint` → 0 erros.
- [x] Validar build do frontend.
  - `npm run build` OK (23 rotas) com `NEXT_PUBLIC_API_URL` definida; com `VERCEL_ENV=production` sem a variável o build falha de propósito.
- [x] Validar build Docker do backend.
  - `docker build` OK (Java 21); container sobe nos perfis `local`, `staging` e `prod` (este com configuração fictícia de produção), conecta ao MySQL 8.4 por TLS (`ssl-mode=REQUIRED`) e responde `UP` em `/actuator/health`.
- [x] Testar fluxo de pedidos.
  - Teste de fumaça real (container `staging` + MySQL 8.4, frete simulado, sem pagamento): login admin/cliente, criação de produto (admin), carrinho, cotação de frete, criação do pedido, estoque reservado (10 → 6), listagens do cliente e do admin, e permissões (anônimo 401, cliente 403 em rotas admin). Horários gravados em Brasília.
  - **Bugs reais encontrados e corrigidos:** (1) `GET /api/cart` retornava 500 para qualquer cliente com item no carrinho (proxy lazy do Hibernate no Jackson; reproduzido também no código original, inclusive com devtools); corrigido com `JacksonConfig` (módulo Hibernate) + `@JsonIgnore` em `Cart.user`, com teste de regressão `CartJsonSerializationMySqlIT`. (2) App não subia com `shipping.gateway=melhorEnvio` (Seção 1). (3) Porta fixa 8080 ignorava `PORT` da plataforma.
  - Varredura das respostas da API: sem hash de senha, códigos ou tokens além do JWT do login.
- [ ] Testar fluxo de pagamentos em ambiente seguro.
  - Coberto por testes unitários com gateway simulado e pelo IT de schema, mas **não** executado contra o sandbox do Asaas: exige a chave de sandbox (`$aact_hmlg_...`) e o token do webhook, que não tenho. Não foi feita nenhuma chamada ao Asaas. Procedimento sugerido: configurar o sandbox em `staging`, criar um pedido, iniciar Pix/boleto, e simular a confirmação pelo painel do sandbox para ver o webhook chegar.
- [ ] Testar fluxo logístico sem compra real de etiquetas.
  - Cotação + pedido validados com o frete **simulado** (fumaça acima) e com o gateway do Melhor Envio por testes unitários. A autorização OAuth e a cotação real no sandbox do Melhor Envio exigem Client ID/Secret e uma conta de sandbox; não executado. Nenhuma etiqueta foi comprada.

## 8. Ativação definitiva

Esta seção depende de decisões, credenciais e custos seus; nada foi contratado, configurado nem publicado. O roteiro ordenado está em `docs/CHECKLIST_LANCAMENTO.md`.

- [ ] Escolher hospedagem do backend.
  - Pendente de decisão (custo). Requisitos e comparação de preços (17/11/2026) em `CHECKLIST_LANCAMENTO.md`, seção 0. Recomendação técnica: instância sempre ligada, única, ≥ 1 GB de RAM.
- [ ] Definir plano do banco de dados.
  - Pendente de decisão (custo). **Prazo:** serviços free do Aiven MySQL têm dados e backups apagados em 21/12/2026.
- [ ] Configurar domínio.
  - Pendente. O site e a API funcionam nas URLs gratuitas das plataformas, mas o e-mail transacional (Resend) exige um domínio verificado para entregar aos clientes.
- [ ] Inserir credenciais de produção.
  - Pendente (suas): Asaas produção, Melhor Envio produção, Resend, Cloudinary, segredos novos de JWT/challenge/criptografia. Lista completa em `DEPLOY.md`, seção 3.
- [ ] Configurar webhooks e callbacks.
  - Pendente (depende da URL do backend): webhook do Asaas e Redirect URI do Melhor Envio. Procedimento em `DEPLOY.md`, seções 7 e 8.
- [ ] Realizar deploy.
  - Pendente (autorização e custo). Backend e frontend foram validados localmente (build, container, fumaça); falta publicar.
- [ ] Executar checklist final de lançamento.
  - Pendente. Roteiro em `docs/CHECKLIST_LANCAMENTO.md`.

## Regras do projeto

1. Não realizar cobranças reais durante desenvolvimento.
2. Não comprar etiquetas reais durante desenvolvimento.
3. Não expor credenciais no código ou Git.
4. Não apagar dados existentes.
5. Não substituir tecnologias sem justificativa.
6. Preservar funcionalidades e arquitetura existentes.
7. Executar testes após alterações.
8. Solicitar aprovação antes de operações destrutivas.

**Meta final:** DN Store preparada tecnicamente para produção, sem necessidade de reestruturação quando chegar o momento de ativar os serviços definitivos.
