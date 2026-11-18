# Roteiro de lançamento da DN Store

Passo a passo para colocar a loja no ar, na ordem. Cada passo aponta o guia com os detalhes. Itens marcados com 💲 geram custo ou exigem contratação; 🔑 exigem credenciais suas. Nada aqui foi executado: é o roteiro para você (ou para fazermos juntos).

Referências: `DEPLOY.md` (arquitetura, variáveis, perfis), `BACKUP_RECUPERACAO.md` (banco, backup, primeiro admin), `MELHOR_ENVIO_PRODUCAO_CHECKLIST.md`, `PREPARACAO_PRODUCAO.md` (estado do checklist técnico).

## 0. Decisões e pendências antes de começar

- [ ] 💲 **Plano do banco no Aiven.** Confirme no console qual é o plano. O MySQL gratuito do Aiven é descontinuado e, segundo o Aiven, serviços free restantes são desligados e **têm dados e backups apagados em 21/12/2026**. Faça backup (`BACKUP_RECUPERACAO.md`, seção 2) e faça upgrade para um plano pago ou migre antes dessa data.
- [ ] 💲 **Hospedagem do backend.** Precisa ser sempre ligada, 1 instância, Docker, ≥ 1 GB de RAM (medido: com 512 MB o app sobe, mas ocioso já usa ~430 MiB). Comparação de preços que levantei (17/11/2026, confira antes de contratar): Render Starter US$ 7/mês (512 MB; o plano gratuito hiberna e não serve), Railway Hobby US$ 5/mês + consumo, Fly.io por máquina. Nenhuma tem região no Brasil.
- [ ] 💲 **Domínio próprio, para o e-mail.** O remetente de teste do Resend só entrega ao e-mail da sua própria conta; sem domínio verificado no Resend os clientes não recebem o código de cadastro/login. O site e a API funcionam sem domínio, o e-mail não.
- [ ] 🔑 Rotacionar as credenciais do arquivo `contas_teste` (repositório público) e fazer commit/push da remoção do arquivo.
- [ ] Commitar as alterações desta preparação (ainda não foram commitadas).

## 1. Banco de dados

- [ ] Backup do banco atual (`db-backup.sh`).
- [x] Verificar o histórico do Flyway no banco do Aiven (`BACKUP_RECUPERACAO.md`, seção 4). Feito em 17/11/2026: 24 migrations aplicadas (V1–V18, V20–V25), todas com sucesso, `validate` do Flyway OK e 0 pendentes. Repetir antes de cada deploy com migration nova.
- [ ] ⚠ Decidir quando aplicar a **V26** (aditiva) no Aiven: backup antes, e ela será aplicada na próxima inicialização normal do backend contra o Aiven (`BACKUP_RECUPERACAO.md`, seção 4). Até lá o perfil `readonly` não sobe contra o Aiven.
- [ ] Primeiro administrador (`BACKUP_RECUPERACAO.md`, seção 5), depois que o e-mail estiver funcionando.

## 2. Homologação (staging) com sandbox, sem dinheiro real

Use `SPRING_PROFILES_ACTIVE=staging`, Asaas sandbox e Melhor Envio sandbox.

- [ ] 🔑 Subir o backend na hospedagem escolhida com as variáveis de `DEPLOY.md` (segredos novos, gerados por você).
- [ ] Health check da plataforma em `/actuator/health/liveness`.
- [ ] Vercel: Root Directory `frontend`, `NEXT_PUBLIC_API_URL`, variáveis do Cloudinary. Deploy.
- [ ] Ajustar no backend `FRONTEND_URL` e `CORS_ALLOWED_ORIGINS` para a URL real da Vercel e reiniciar.
- [ ] 🔑 Asaas sandbox: cadastrar o webhook `https://<backend>/api/payment/webhook` (somente eventos `PAYMENT_*`) com o mesmo token de `ASAAS_WEBHOOK_TOKEN`. Criar um pedido e iniciar Pix/boleto; confirmar o pagamento pelo sandbox e ver o pedido passar a pago.
- [ ] 🔑 Melhor Envio sandbox: cadastrar o Redirect URI `https://<backend>/api/integrations/melhor-envio/callback`; no painel admin, "Conectar Melhor Envio"; cotar um CEP real e fechar um pedido de teste. **Não comprar etiqueta.**
- [ ] Conferir que o horário de um pedido de teste bate com o relógio de Brasília.
- [ ] Testar cadastro, login (com e sem MFA), recuperação de senha, carrinho, favoritos, painel admin (produtos, upload de imagem, pedidos, eventos, usuários).

## 3. Produção

- [ ] 🔑 `SPRING_PROFILES_ACTIVE=prod`; `ASAAS_ENVIRONMENT=production`, `ASAAS_BASE_URL=https://api.asaas.com/v3`, chave `$aact_prod_...` e novo token de webhook; `MELHOR_ENVIO_ENV=production` com as URLs de produção e **chaves/segredos de produção diferentes dos de sandbox**.
- [ ] Recadastrar o webhook do Asaas e o Redirect URI do Melhor Envio **no ambiente de produção** de cada serviço.
- [ ] Autorizar o Melhor Envio de produção pelo painel admin (conta real da loja).
- [ ] O app recusa subir se algo estiver incoerente (mensagem cita a variável). Corrija e reinicie.
- [ ] 💲 **Validação real** (só com sua autorização): uma compra de valor baixo pelo Pix, conferir a confirmação via webhook, e o fluxo de reembolso no painel do Asaas se necessário. Depois, uma cotação real de frete. Etiquetas são compradas manualmente no painel do Melhor Envio (a compra automática não está implementada).

## 4. Depois do lançamento

- [ ] Backup periódico (semanal ou diário) e **antes de cada deploy com migration**.
- [ ] Cotação de frete de teste periodicamente: se a autorização do Melhor Envio expirar, o checkout passa a responder 503 até reconectar no painel.
- [ ] Se a fila de webhooks do Asaas for interrompida, reabilite no painel do Asaas.
- [ ] Ficar de olho nos logs por `payment.webhook.failed`, `payment.creation.uncertain`, `security.rate_limit.blocked`.
- [ ] Trocar de URL/domínio: `DEPLOY.md`, seção 9.
