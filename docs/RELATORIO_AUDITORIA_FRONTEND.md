# Relatório de Auditoria do Frontend — DN Store

Data: 2026-11-17 · Escopo: `frontend/` (Next.js 16 / React 19) · Auditoria feita por QA/dev sênior assistido (Claude Code).

> **Regra deste relatório:** nada é marcado como aprovado sem evidência de execução. Legenda:
> ✅ Testado e aprovado · 🔧 Testado e corrigido · ❌ Testado e reprovado (pendente) · ⬜ Não testado · 🌐 Dependente de ambiente externo

---

## 1. Resumo executivo

- O frontend compila (`tsc`, `eslint` sem erros, `next build` OK) e as páginas carregam sem erros de console relevantes nem rolagem horizontal em desktop (1440), tablet (820) e mobile (390).
- Foram encontrados **1 problema crítico (backend, decisão de negócio)**, **4 altos**, vários médios/baixos. **Os problemas do frontend foram corrigidos e reverificados em execução**; o crítico **não foi alterado** e depende da sua autorização (seção 13).
- **Crítico em aberto — preço promocional:** a listagem exibe o preço promocional (R$ 149,90), mas página de produto, carrinho **e o backend** (`OrderService`, linhas ~252/317/374 usam `product.getPrice()`) usam o preço cheio (R$ 189,90). O cliente vê um preço e o pedido é cobrado por outro. A origem é o backend; não foi corrigido só no frontend.
- **Não foi possível validar pagamento (Asaas), e-mails (Resend), frete real (Melhor Envio) nem a entrega de e-mails**, por regra de segurança. Esses itens estão marcados 🌐.
- Cobertura estimada da auditoria: **≈ 70 %** (seção 14).

## 2. Ambiente de teste (isolamento)

O backend que estava rodando em `:8080` está conectado ao **banco Aiven (produção)** (`backendJava/.env` → `DB_URL` Aiven; conexões TCP confirmadas na porta 22273). Por isso **ele não foi usado para nenhuma escrita**. O seu frontend em `:3000` também não foi tocado.

Para os testes foi montado um ambiente isolado e descartável:

| Item | Configuração |
|---|---|
| Backend de auditoria | porta **8081**, sem ler `.env` (`-Dspring.config.import=optional:file:/nonexistent`), segredos fictícios |
| Banco | MySQL **local**, schema novo `dn_store_audit` (o schema `Dn_store` local existente não foi tocado). Flyway real aplicou as migrations (27 tabelas) |
| E-mail | `RESEND_API_KEY` vazio → backend responde 503 em qualquer envio. **Nenhum e-mail foi enviado** |
| Pagamento | `ASAAS_API_KEY` vazio → pagamento não é testável de ponta a ponta |
| Frete | gateway `simulated` (cotação real do backend, valores simulados) |
| Frontend de auditoria | cópia em diretório temporário, `next dev -p 3100`, `NEXT_PUBLIC_API_URL=http://localhost:8081/api` |
| Dados | 3 usuários e 3 produtos descartáveis (`*.audit@example.test`, domínio inexistente), 1 pedido de teste no banco local |

Tudo que foi validado "contra o backend real" significa: contra **este backend real (código atual) com banco local**, não contra produção.
Mocks: **nenhum mock de API foi usado**. Simulações: modo offline do navegador (erro de rede) e o gateway de frete `simulated`.

## 3. Páginas e rotas inspecionadas

Rotas (23 no build): `/`, `/produtos`, `/produtos/[id]`, `/eventos`, `/sobre`, `/favoritos`, `/carrinho`, `/checkout` (+`/sucesso`), `/conta`, `/auth`, `/auth/recover`, `/auth/recover/reset`, `/admin`, `/admin/produtos` (+`/novo`, `/lote`, `/[id]`), `/admin/pedidos`, `/admin/categorias`, `/admin/eventos`, `/admin/usuarios`, 404.

| Rota | Carrega | Overflow horiz. (3 viewports) | Console | Observação |
|---|---|---|---|---|
| `/`, `/produtos`, `/eventos`, `/sobre`, `/favoritos`, `/carrinho` | ✅ | ✅ sem overflow | ✅ (exceto `/eventos`: ver M6) | testado sem sessão |
| `/auth`, `/auth/recover`, `/auth/recover/reset` | ✅ | ✅ | ✅ | |
| `/checkout` | ✅ | ✅ (sem sessão redireciona) | ✅ | fluxo completo logado: ver seção 6 |
| `/conta` | ✅ | ✅ | ✅ | logado e deslogado |
| `/admin` e 7 subrotas | ✅ | ✅ | ✅ sem erros de API | logado como admin |
| `/produtos/[id]` | ✅ | ⬜ só desktop | ✅ | |
| `/checkout/sucesso`, `/admin/produtos/[id]` | ⬜ | ⬜ | ⬜ | não exercitadas |
| 404 | ✅ | ✅ | ✅ | página padrão do Next (sem identidade visual) |

## 4. Funcionalidades testadas

### Autenticação e recuperação de conta (executado contra o backend isolado)

| Cenário | Resultado |
|---|---|
| Login com credenciais corretas, persistência de sessão, `/conta` carrega `/account/me`, `/orders`, `/addresses` | ✅ |
| Login com senha errada | 🔧 mostrava `Authentication required or credentials are invalid.` (inglês); agora "E-mail ou senha incorretos." |
| Login com e-mail inexistente | 🔧 idem. **Nota:** o backend responde igual para senha errada e e-mail inexistente (evita enumeração de contas). Mantido de propósito — não diz "e-mail não cadastrado" |
| Login com e-mail **não verificado** | 🔧 **Bug alto:** o backend responde `403 + EMAIL_VERIFICATION_REQUIRED`; o axios tratava como erro e mostrava "verifique suas credenciais", **sem nunca abrir a tela de verificação**. Agora abre a tela e dispara o reenvio do código |
| Cadastro — validações (nome, e-mail, telefone, CPF, senha curta/sem número, confirmação, endereço, CEP/ViaCEP, máscaras) | ✅ todas as mensagens e máscaras funcionaram |
| Cadastro — envio final | 🌐 backend respondeu 503 (sem Resend); mensagem tratada ("serviço temporariamente indisponível") ✅. Criação real + código por e-mail não validados |
| Verificação de e-mail, código inválido/expirado, reenvio, expiração, MFA | 🌐 dependem do e-mail. Mensagens por status (400→código inválido/expirado, 429→aguarde 1 min) corrigidas e cobertas por teste unitário, **mas não exercitadas ponta a ponta** |
| Limite de tentativas (429) | 🔧 backend limitou no 8º login; UI agora mostra "Muitas tentativas em pouco tempo…" (antes: mensagem genérica de credenciais) |
| Servidor indisponível / sem internet | 🔧 modo offline: "Não foi possível conectar ao servidor…" (antes: "Verifique suas credenciais") |
| Recuperação de senha — solicitar | ✅ fluxo/UI; 🔧 erros por status, rótulo do campo e opção "Enviar novamente ou usar outro e-mail". 🌐 recebimento do e-mail não validado. **Nota:** com o e-mail indisponível o backend ainda responde 200 e a tela diz "enviaremos o link" (comportamento anti-enumeração do backend) |
| Redefinição — link inválido | 🔧 antes: mensagem única para qualquer erro; agora "inválido, expirou ou já foi utilizado" (400) e 429 separados |
| Redefinição — sem token na URL | 🔧 agora mostra o aviso imediatamente + botão "Solicitar novo link" |
| Redefinição com token válido, link usado/expirado reais, login após redefinição | 🌐 exige o e-mail real |
| Logout | ⬜ botão não exercitado isoladamente (limpeza de cookie vista no código) |
| Renovação de token | ⬜ não implementada no frontend (JWT de 24 h; ao expirar → 401 → redireciona para `/auth`) |
| Rotas protegidas sem sessão | ✅ `/checkout` → `/auth?next=/checkout`; 🔧 `/admin*` agora preserva `?next=`; `/conta` mostra link "Entrar" |
| Redirecionamento pós-login | 🔧 **`next` aceitava URL externa** (open redirect: `/auth?next=https://site-externo` levava para fora após login). Agora só caminhos internos; verificado em execução |

### Catálogo e compra

| Item | Resultado |
|---|---|
| Listagem, categorias, ordenação, filtro de tamanho, contagem | ✅ (3 produtos de teste) |
| Busca textual, filtro de preço (min/máx) | ⬜ só verificada a presença/acessibilidade dos controles; não exercitei os resultados |
| Detalhe do produto, variações, estoque esgotado desabilitado, seleção inicial | ✅ |
| Adicionar ao carrinho | 🔧 usava `alert()` nativo; agora toast |
| Carrinho (quantidade, remover, persistência em localStorage) | ✅ exibição/botões com nome acessível; ⬜ não testei alterar/remover |
| Frete no carrinho | 🔧 exibia "Frete: Grátis" (falso: o frete é cotado no checkout); agora "Calculado no checkout" |
| Checkout — CEP/ViaCEP/cotação | ✅ cotação real do backend (simulada), preenchimento automático ok; 🔧 CEP sem máscara |
| Checkout — CPF/CNPJ | 🔧 aceitava `111.111.111-11` (só contava dígitos); agora valida CPF/CNPJ com mensagem inline (validador CNPJ conferido contra valores conhecidos) |
| Checkout — criação de pedido | ✅ `POST /orders` 201 no banco local |
| Checkout — pagamento (Pix/Boleto/QR) | 🌐 `POST /payment/init` → 500 sem chave Asaas (esperado). Mensagem exibida: "Erro ao processar pagamento." |
| Cupons | ❌ **não existe interface**: backend tem `/api/coupons/validate` e desconto no pedido; o frontend não tem campo de cupom no carrinho/checkout nem tela admin de cupons (só exibe o desconto em pedidos admin). Funcionalidade ausente → decisão sua |
| Confirmação/acompanhamento de pedido | 🌐 `/checkout/sucesso` não exercitada; **não há página de detalhe/rastreio de pedido** (rodapé: "Rastreie seu Pedido — Indisponível") |

### Área do cliente (`/conta`)

| Item | Resultado |
|---|---|
| Carregar dados / pedidos / endereços | ✅ |
| Editar perfil | 🔧 sem tratamento de erro e sem validação de telefone → agora valida, máscara, loading, erro |
| Endereços | 🔧 placeholders eram nomes de campo em inglês (`zipCode`, `street`…), sem rótulos/máscara/tratamento de erro; erros eram exibidos em verde. Corrigido; adicionar e excluir verificados |
| Excluir endereço | 🔧 sem confirmação nem erro. Agora confirma. **Backend:** excluir endereço usado por um pedido devolve 409 (FK); mensagem explicativa adicionada (ver pendência P4) |
| Status dos pedidos | 🔧 exibia enum cru (`PENDING_PAYMENT`) → rótulos em português (reaproveita `utils/orderStatus.ts`) |
| Alterar senha / MFA | ⬜ não exercitados (mensagens de erro agora passam pelo helper central) |

### Painel administrativo

| Item | Resultado |
|---|---|
| Acesso admin/guarda de rota, 8 páginas carregam, sem erros de API/console, sem overflow | ✅ |
| Dashboard/indicadores | ✅ renderiza (R$ 0,00 receita, 3 produtos); valores contra dados reais ⬜ |
| CRUD de produtos/categorias/eventos/usuários, upload (Cloudinary), lote, padrões, confirmações, filtros/paginação, ações de pedido/pagamento manual | ⬜ **não exercitados** (só carga e layout). Upload de imagem depende de Cloudinary 🌐 |
| Cupons / Gestão de pagamentos / Clientes | ❌ sem páginas dedicadas (Clientes ≈ "Usuários") |
| Permissões | ✅ frontend redireciona não-admin; as regras reais estão no backend (`/api/admin/**` exige `ROLE_ADMIN`); **teste de permissão negativa no backend ⬜ não executado** |

## 5. Problemas visuais

- V1 (médio) — `/admin/categorias` em mobile: a tabela fica cortada à direita (coluna de ações fora da tela, sem indicação de rolagem). ❌ pendente.
- V2 (baixo) — 404 é a página padrão do Next, sem identidade visual. ❌ pendente.
- V3 (baixo) — Recuperar/Redefinir senha usam estilos inline e fundo branco, destoando de `/auth`. ❌ pendente (preservada a identidade, só corrigi comportamento).
- V4 (baixo) — Rodapé com 4 itens "Indisponível" (FAQ, Trocas, Rastreio, Termos). São alterações suas não commitadas; apenas registrado.
- V5 (baixo) — Em `/auth` (cadastro, etapa 3) a mensagem de erro aparece no topo do formulário, longe do botão. ❌ pendente.

## 6. Problemas funcionais

| # | Severidade | Problema | Status |
|---|---|---|---|
| C1 | **Crítico** | Preço promocional exibido na listagem e nunca cobrado (origem: backend) | ❌ aguarda decisão |
| F1 | Alto | Login de e-mail não verificado nunca chegava à tela de verificação | 🔧 |
| F2 | Alto | `next` em `/auth` permitia redirecionar a site externo | 🔧 |
| F3 | Alto | Erros de login/rede/limite exibidos em inglês ou como "verifique credenciais" | 🔧 |
| F4 | Alto | `/conta`: salvar perfil/endereço/excluir sem `try/catch` (falha silenciosa), erros em verde | 🔧 |
| F5 | Médio | Carrinho mostrava "Frete Grátis" | 🔧 |
| F6 | Médio | CPF/CNPJ do checkout não validado | 🔧 |
| F7 | Médio | Botão de login exibia "Cadastrando..." | 🔧 |
| F8 | Médio | `alert()` nativo ao adicionar ao carrinho | 🔧 |
| F9 | Médio | Cupons sem UI; sem página de detalhe/rastreio de pedido | ❌ decisão |
| F10 | Médio | Opção de frete marcada "Simulação local; não disponível para compra" é selecionável e o backend aceita criar o pedido com ela | ❌ pendente (só ocorre com `shipping.gateway=simulated`; confirmar que produção usa `melhorEnvio`) |
| F11 | Baixo | Mensagens de validação de campo do backend (`errors{}`) vêm em inglês; o frontend valida antes, então raramente aparecem | ❌ pendente |
| F12 | Baixo | Produto sem estoque aparece primeiro na listagem com "VER MAIS" | ❌ pendente (regra de negócio) |

## 7. Responsividade

Verificado por script (`scrollWidth > clientWidth`) em 13 rotas públicas + 8 admin × 3 viewports: **sem rolagem horizontal da página**. Capturas inspecionadas visualmente: admin (dashboard, categorias) e `/conta` em 390 px. Problemas: V1 (tabela de categorias). **Não** foram inspecionadas visualmente as demais páginas em tablet/mobile (só medição programática) — ⬜.

## 8. Acessibilidade

| Item | Status |
|---|---|
| Rótulos: `/auth/recover` (1), `/auth/recover/reset` (2), checkout entrega (7) e pagamento (1), `/produtos` filtros (5), `/conta` | 🔧 |
| `LabelInput` tinha `aria-label` redundante no `<label>` | 🔧 |
| Alertas sem `role="alert"` (reset), `role="status"` (recover) | 🔧 |
| `<main>` aninhado (layout + `/conta` + `/admin`) | 🔧 |
| `<title>` idêntico em todas as páginas | 🔧 títulos por rota (`Produtos \| Desafio Natureza`…) e por produto |
| `/admin/produtos/novo` (12 campos), `/admin/categorias` (2 campos + 14 botões), `/admin/usuarios`, `/admin/produtos/lote` sem nome acessível | ❌ pendente |
| `confirm()` nativo em ações admin (excluir categoria/usuário/evento/produto/padrão) | ❌ pendente (funciona, mas não é acessível/estilizável) |
| Navegação por teclado, foco visível, modais, contraste (WCAG) | ⬜ **não testados** de forma sistemática |
| Imagens: todas têm `alt` nas páginas medidas | ✅ |

## 9. Correções implementadas

1. **`utils/apiError.ts` (novo)** — helper central `getApiErrorMessage`: erros de rede/timeout; traduz as mensagens genéricas em inglês do backend por status (401/403/404/405/409/422/429/5xx); preserva mensagens de regra de negócio já em português; nunca expõe texto técnico em 5xx/429; aceita sobrescritas por status por fluxo. Aplicado em login, cadastro, verificação/reenvio, recuperar/redefinir senha, conta, checkout (entrega e pagamento).
2. **`utils/safeRedirect.ts` (novo)** — `safeNextPath` bloqueia redirecionamento externo.
3. Fluxo de login para e-mail não verificado (403) → tela de verificação + reenvio automático.
4. Recuperar/Redefinir senha: rótulos, `role`, mensagens por status, reenvio, token ausente.
5. `/conta`: tratamento de erro/loading/duplo envio, rótulos e máscaras (CEP/telefone/UF), rótulos de status, confirmação de exclusão, mensagem para 409.
6. Checkout: máscara de CEP, rótulos, validação de CPF/CNPJ (`isValidCnpj` novo em `utils/authValidation.ts`), mensagens de erro.
7. Carrinho: "Calculado no checkout". Produto: toast no lugar de `alert()`.
8. Metadados: `title.template` global + `layout.tsx` por rota + `generateMetadata` em `/produtos/[id]`.
9. Acessibilidade: rótulos nos filtros de `/produtos`, `<main>` aninhado, `aria-label` redundante.
10. Testes: `npm test` (runner nativo do Node, sem dependências novas) com 11 testes.

## 10. Arquivos modificados

**Novos:** `utils/apiError.ts`, `utils/apiError.test.ts`, `utils/safeRedirect.ts`, `utils/safeRedirect.test.ts`, `app/{auth,carrinho,checkout,conta,eventos,favoritos,produtos,sobre}/layout.tsx`.

**Alterados por esta auditoria:** `app/auth/page.tsx`, `app/auth/recover/page.tsx`, `app/auth/recover/reset/page.tsx`, `app/components/ui/AuthForm.tsx`, `app/components/ui/LabelInput.tsx`, `app/conta/page.tsx`, `app/carrinho/page.tsx`, `app/checkout/page.tsx`, `app/checkout/components/StepDelivery.tsx`, `app/checkout/components/StepPayment.tsx`, `app/produtos/ProductInteractive.tsx`, `app/produtos/[id]/page.tsx`, `app/produtos/page.tsx`, `app/layout.tsx`, `app/admin/layout.tsx`, `utils/authValidation.ts`, `package.json` (script `test`), `tsconfig.json` (`allowImportingTsExtensions`, necessário para os testes).

**Já modificados por você antes da auditoria (e preservados):** `app/admin/page.tsx`, `app/admin/pedidos/page.tsx`, `app/admin/produtos/page.tsx`, `app/components/Footer.tsx`, `utils/orderStatus.ts`, e os arquivos do backend. Em `app/produtos/page.tsx` apenas acrescentei `aria-label`s sobre a sua alteração. **Nenhuma alteração foi feita no backend, em `.env` ou no banco.** Nada foi commitado.

## 11. Testes executados e resultados

| Verificação | Resultado |
|---|---|
| `tsc --noEmit` (antes e depois) | ✅ 0 erros |
| `eslint` (antes e depois) | ✅ 0 erros, 1 warning pré-existente (`<img>` em `admin/eventos/page.tsx:94`) |
| `next build` (cópia isolada, com `NEXT_PUBLIC_API_URL`) | ✅ 23 rotas geradas |
| `npm test` (node --test) | ✅ 11/11 (helper de erros, redirecionamento seguro). Exige Node ≥ 22.18 (rodado em v25.8.1); emite aviso `MODULE_TYPELESS_PACKAGE_JSON` inofensivo |
| E2E manual com Playwright contra backend isolado (login, erros, 403 não verificado, recuperar/redefinir, cadastro, rate limit 429, offline, conta, checkout até pagamento, admin) | ✅ resultados nas seções 4–8; evidência = respostas HTTP e textos coletados na execução |
| Testes automatizados E2E versionados no repositório | ⬜ **não criados** (os E2E foram execuções interativas, não ficaram como suíte) |
| Teste de componente (React Testing Library etc.) | ⬜ não há runner configurado |

## 12. Dependem de validação externa

- Entrega de e-mail (Resend): cadastro, verificação, reenvio, expiração, MFA, link de redefinição, "link já usado".
- Pagamento Asaas (Pix/Boleto, QR, webhook, estorno) — nada foi executado.
- Frete Melhor Envio real (cotação, OAuth no painel admin).
- Upload de imagens (Cloudinary) e Instagram embed.
- Comportamento em produção (HTTPS, cookie `secure`, CORS, `NEXT_PUBLIC_API_URL`).

## 13. Pendências e decisões que precisam de você

| # | Item | Tipo |
|---|---|---|
| P1 | **Preço promocional**: decidir se o backend deve cobrar `promotionalPrice` (ajuste em `OrderService`/carrinho + exibir na página do produto) ou se a promoção não deve aparecer. Enquanto isso o site mostra um preço e cobra outro | **Decisão de negócio + backend (crítico)** |
| P2 | Cupons: criar UI no carrinho/checkout e no admin? (backend já tem `/api/coupons/validate`) | Funcionalidade nova |
| P3 | Detalhe/rastreio de pedido para o cliente | Funcionalidade nova |
| P4 | Excluir endereço usado por pedido → 409. Sugestão: backend fazer exclusão lógica (arquivar) | Backend |
| P5 | Token JWT em cookie **não httpOnly** (gravado por JS) e usuário em `localStorage` — exposto a XSS; migrar para cookie `HttpOnly` emitido pelo backend; a sessão também não é revalidada em `/auth/me` ao carregar | Arquitetura/segurança |
| P6 | Mensagens de validação por campo do backend em inglês; backend usa HTTP 500 para falha do gateway de pagamento mal configurado (seria 502/503) | Backend |
| P7 | Confirmar que produção usa `shipping.gateway=melhorEnvio` (F10) | Configuração |
| P8 | Itens ❌ de acessibilidade/visual (V1, V2, V3, V5, admin sem rótulos, `confirm()` nativo) | Frontend (posso fazer) |
| P9 | **Higiene (do CLAUDE.md):** `contas_teste` versionado com credenciais; considere remover e rotacionar. `backendJava/.env` contém chaves reais (Resend/Asaas/Melhor Envio/Aiven) — não foram lidas nem exibidas nesta auditoria | Segurança |
| P10 | Transformar os testes E2E manuais em suíte Playwright versionada | Qualidade |

Sobre o backend isolado e o frontend de auditoria: continuam rodando nas portas **8081** e **3100** (e o schema local `dn_store_audit`) para você reproduzir; encerre com `kill $(lsof -tiTCP:8081 -sTCP:LISTEN)` / `kill $(lsof -tiTCP:3100 -sTCP:LISTEN)` e `DROP DATABASE dn_store_audit;`. Os arquivos de apoio (script, logs, capturas) estão no diretório temporário da sessão, fora do repositório.

## 14. Percentual estimado de conclusão da auditoria

**≈ 70 %.**

| Área | Cobertura |
|---|---|
| Mapeamento e checagens estáticas (tsc/lint/build) | 100 % |
| Autenticação (parte sem e-mail) | 90 % · (parte com e-mail) 0 % 🌐 |
| Layout/responsividade (medição) | 100 % · (inspeção visual) ≈ 25 % |
| Catálogo/compra (até pagamento) | ≈ 70 % |
| Conta do cliente | ≈ 85 % |
| Admin (carga/layout) | 100 % · (operações CRUD) ≈ 0 % |
| Acessibilidade (rótulos/semântica) | ≈ 70 % · (teclado/foco/contraste) 0 % |
| Pagamento/e-mail/frete real | 0 % 🌐 |
