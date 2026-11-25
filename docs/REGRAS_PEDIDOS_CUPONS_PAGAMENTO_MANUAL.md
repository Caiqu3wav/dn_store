# Regras: cupons, receita do painel e confirmação manual de pagamento

Estado em 17/11/2026. Cada regra aponta o teste que a protege. Tudo foi validado em MySQL 8 descartável; **nada foi aplicado no Aiven**.

## 1. Cupons

- **Limite de uso.** O checkout consome a utilização com um único `UPDATE` atômico no banco (`CouponRepository.consumeUsage`): só incrementa se `current_usage < max_usage`. Antes era "ler, comparar e gravar", e duas compras simultâneas passavam pelo último uso. Se o checkout falhar depois, o rollback devolve a utilização. Cupom sem `max_usage` é ilimitado.
- **Devolução.** A utilização volta junto com o estoque, em `OrderInventoryService.releaseReservedStock`, que já era o ponto único e idempotente (flag `stock_released`) usado pelo cancelamento e pela expiração. Logo, cancelamentos/expirações repetidos ou concorrentes devolvem **uma única vez**, e o contador nunca fica negativo.
- **Pedido reativado** (`reserveReleasedStock`) reaplica a utilização.
- Testes: `OrderCouponAndManualPaymentMySqlIT` (16 checkouts disputando 3 vagas; 4 cancelamentos simultâneos + 1; expiração repetida; corrida cancelamento × expiração; piso em zero; rollback), `CouponServiceTest`, `OrderInventoryServiceTest`, `OrderServiceTest`.
- **Dados reais (consulta de leitura em 17/11/2026):** o Aiven não tem cupons; não há contador a reconciliar.

## 2. Receita e indicadores do painel

- **Receita** = soma de `orders.total` dos pedidos em `PAID`, `PROCESSING`, `SHIPPED` e `DELIVERED`, **menos** o valor já reembolsado (`payments.refunded_amount`), nunca abaixo de zero.
- **Ticket médio** = receita ÷ pedidos pagos (antes dividia pelo total de pedidos, inclusive cancelados).
- Ficam fora: `PENDING_PAYMENT`, `PAYMENT_FAILED`, `EXPIRED`, `CANCELLED` e `PAYMENT_REVIEW` (pagamento recebido, mas sob revisão).
- O DTO ganhou `paidOrders`; `totalOrders` continua sendo o total geral. O painel mostra "Pedidos (pagos/total)".
- **Dados reais:** o Aiven tem 1 pedido `EXPIRED` e 1 `PENDING_PAYMENT` (R$ 372,77 somados) e nenhum pago. A regra antiga mostraria R$ 372,77 de "receita"; a nova mostra R$ 0,00.
- Testes: `AdminDashboardServiceTest`, `OrderCouponAndManualPaymentMySqlIT.dashboardRevenueCountsOnlyPaidOrdersNetOfRefunds`.

## 3. Confirmação manual de pagamento

- **Quem:** somente `ADMIN` (regra de URL + `@PreAuthorize` + checagem no serviço). Rotas: `POST /api/orders/{id}/confirm-payment` (corpo `{"reason": "..."}`) e `GET /api/orders/{id}/status-audit`.
- **Justificativa obrigatória:** de 10 a 500 caracteres.
- **Quando:** somente em pedidos `PENDING_PAYMENT` ou `PAYMENT_FAILED`, com estoque ainda reservado, sem bloqueio financeiro ativo e **sem nenhuma tentativa do Asaas que não esteja `FAILED`** (pendente, criando, vencida, paga, reembolsada...). Isso impede cobrar duas vezes.
- **Registro (`order_status_audit`):** pedido, administrador, tipo (`MANUAL_PAYMENT_CONFIRMATION`), status anterior, status novo, justificativa e data/hora. O pedido recebe `manualPaymentConfirmed = true`, e o painel mostra o selo "manual" e o histórico, então não se confunde com a confirmação automática do Asaas (que registra `Payment` e `payment_financial_history` com origem `WEBHOOK`).
- **`PUT /orders/{id}/status` não aceita mais `PAID`.** Também não deixa mais um pedido cancelado/expirado (estoque já devolvido) voltar para `PROCESSING/SHIPPED/DELIVERED`.
- **Convivência com webhooks.** Pagamento do Asaas que chegar *depois* da confirmação manual (por exemplo, uma tentativa `FAILED` que o cliente acaba pagando) é gravado, o pedido segue `PAID`, e o sistema abre um **bloqueio financeiro** `MANUAL_CONFIRMATION_CONFLICT` para reconciliação; o bloqueio já existente impede avançar para `PROCESSING/SHIPPED/DELIVERED` até alguém resolver. Eventos duplicados do Asaas continuam sendo no-op.
- Testes: `OrderManualPaymentServiceTest`, `AdminOrderPaymentControllerTest`, `AdminRoutesSecurityTest` (anônimo 401, cliente 403, admin 200), `PaymentAttemptStateServiceTest` (2 novos), `OrderCouponAndManualPaymentMySqlIT` (duplo clique → 1 auditoria).

## 4. Correção que veio junto

A tela de pedidos do painel enviava `pending`, `CONFIRMADO`, `enviado`, `entregue`, `cancelado`, valores que o backend recusava com 400. **Toda alteração de status pelo painel falhava.** Agora envia os status reais e mostra o motivo do erro.

## 5. Dúvidas de negócio em aberto (não inventei regra)

1. **Cupom por cliente:** não há limite de uso por usuário; só o limite global. Deve haver?
2. **Receita:** inclui o frete (usa `orders.total`). É isso mesmo, ou a receita deve ser só produtos?
3. **Chargeback:** só reembolsos registrados (`refunded_amount`) são descontados. Chargebacks entram no cálculo?
4. **Pedidos `PAYMENT_REVIEW` ou `EXPIRED` com pagamento recebido por fora:** a confirmação manual recusa. Qual deve ser o procedimento (reativar? reembolsar?)
5. **Permissão fina:** hoje qualquer `ADMIN` confirma pagamentos. Deve haver um perfil mais restrito?
