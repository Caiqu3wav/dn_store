# Banco de dados: backup, recuperação e primeiro administrador

Complementa `DEPLOY.md`. Os scripts ficam em `backendJava/scripts/` e foram testados contra um MySQL 8.4 descartável (backup, restauração em banco novo, comparação de contagens por tabela e recusa de restaurar em banco com dados).

## 1. Antes de tudo: situação do plano no Aiven

O Aiven aposentou o MySQL gratuito. Conforme a documentação do Aiven consultada em 17/11/2026 ([migração do free tier](https://aiven.io/docs/products/mysql/howto/mysql-free-tier-migration-options)):

- serviços MySQL free que ainda existem serão desligados e **seus dados e backups apagados em 21/12/2026**;
- fazer upgrade para um plano pago antes disso preserva dados e conexão.

**Confirme no console do Aiven qual é o plano do seu serviço.** Se for free, faça agora um backup (seção 2) e o upgrade, ou migre antes da data. Esta é a única pendência com prazo.

Backups automáticos do Aiven variam por plano (confira na página de preços e no console); mesmo nos pagos, mantenha um backup próprio antes de cada deploy com migration.

## 2. Backup

Requer o cliente `mysql`/`mysqldump` 8.x instalado (ou rode o script dentro de um container `mysql:8.4`).

```bash
cd backendJava
export DB_HOST=<host-do-aiven> DB_PORT=<porta> DB_NAME=<database> DB_USER=<usuario>
read -rs MYSQL_PWD && export MYSQL_PWD      # digite a senha; não fica no histórico
./scripts/db-backup.sh ~/backups-dn-store
```

- Gera `dn_store_<db>_<data>.sql.gz`, validado (gzip íntegro e marca `Dump completed`).
- É somente leitura e consistente (`--single-transaction`), não bloqueia a loja.
- O arquivo contém dados de clientes: **guarde fora do Git** e em local privado. `*.sql.gz` não deve ser versionado.
- Faça um backup **antes de cada deploy que inclua migration nova** e periodicamente (sugestão: semanal, ou diário com a loja em operação).

## 3. Recuperação

Restaure sempre em um banco **vazio**; o script recusa banco com tabelas e exige `CONFIRM_RESTORE=yes`.

1. Crie um banco novo (no painel do Aiven ou `CREATE DATABASE dn_store_restore;`).
2. Restaure:

```bash
export DB_NAME=dn_store_restore CONFIRM_RESTORE=yes
./scripts/db-restore.sh ~/backups-dn-store/dn_store_<db>_<data>.sql.gz
```

3. Valide: compare contagens (`SELECT COUNT(*)`) das tabelas principais (`users`, `orders`, `payments`, `products`) e confira `flyway_schema_history`.
4. Aponte o backend para o banco restaurado (`DB_URL`) e reinicie; o Flyway só aplicará o que faltar.

**Teste a restauração uma vez** antes de precisar dela, em um banco descartável. Um backup nunca testado não é garantia.

## 4. Migrations (Flyway)

- Em produção o Hibernate só valida (`ddl-auto=validate`); o schema muda **somente** por migrations em `backendJava/src/main/resources/db/migration/`.
- A numeração tem uma lacuna (não existe V19). As migrations atuais são V1–V18 e V20–V26 (a V26 ainda **não** foi aplicada no Aiven, ver abaixo) e foram aplicadas com sucesso em um MySQL 8.4 vazio, além do `PaymentSchemaMySqlIT` (9 testes) passar.
- O Flyway 10.20 (do Spring Boot 3.4.0) avisa que o MySQL 8.4 é mais novo do que a versão testada por ele (8.1). Nos testes locais as 24 migrations funcionaram; mantenha o aviso em observação.
- Nunca edite uma migration já aplicada; crie uma nova.

### V26: confirmação manual de pagamento (pendente no Aiven)

`V26__add_order_manual_payment_audit.sql` é **apenas aditiva**: adiciona a coluna `orders.manual_payment_confirmed` (`NOT NULL DEFAULT FALSE`) e cria a tabela `order_status_audit`. Não altera nem remove dados. Foi aplicada e testada somente em MySQL descartável.

Atenção: o Flyway aplica a V26 sozinho na **próxima inicialização normal do backend contra o Aiven**. Por isso:

1. Faça backup antes (seção 2) e escolha conscientemente o momento de subir o backend com este código.
2. Enquanto a V26 não estiver aplicada, o perfil `readonly` (DEPLOY.md, seção 11) **não sobe** contra o Aiven, porque o Hibernate valida o schema e a coluna/tabela ainda não existem. Para olhar o catálogo real até lá, use o código anterior ao commit desta mudança ou aplique a V26 de propósito.
3. Rollback: a V26 não é desfeita automaticamente; como é aditiva, o código anterior continua funcionando com a coluna e a tabela extras.

### Verificação obrigatória em bancos que já existem (Aiven)

O histórico do Git mostra que V19 e V20 já existiram com outro conteúdo (`durable_payment_webhook_processing` e `allow_pending_payment_webhook_processed_at`) e foram renomeadas para V22 e V23. Se o banco do Aiven aplicou essas versões antigas, o Flyway pode recusar subir (migration aplicada que não existe mais). Confira **antes do primeiro deploy**, somente leitura:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history ORDER BY installed_rank;
```

- Esperado: versões entre 1 e 25 coerentes com os arquivos acima, todas `success = 1`.
**Resultado da verificação (17/11/2026, somente leitura, sessão MySQL em `transaction_read_only`):** o banco do Aiven tem 24 registros em `flyway_schema_history`, todos `success = 1`: versões 1–18 e 20–25, sem V19, e as descrições de V22 e V23 conferem com os arquivos atuais. O `validate` do próprio Flyway (checksums incluídos) contra o Aiven retornou `valid=true`, 24 migrations validadas e **0 pendentes**. Ou seja, o backend atual sobe contra esse banco sem aplicar nada e sem divergência. Repita a consulta antes de qualquer deploy que traga uma migration nova.

- Se aparecer V19 ou um V20/V22/V23 com descrição diferente da dos arquivos atuais, **não force nada**: pare e avalie caso a caso (envolve `flyway repair` e comparação de schema, que mexem no banco).

## 5. Primeiro administrador

O cadastro (`POST /api/auth/register`) sempre cria `USER`. Só um ADMIN consegue promover outro usuário (`PUT /api/users/{id}/role`). Portanto o **primeiro** ADMIN precisa ser promovido direto no banco:

1. Configure o Resend e cadastre-se pelo site normalmente com o e-mail do administrador. O e-mail precisa ser verificado pelo código recebido (sem `RESEND_API_KEY` o cadastro falha com 503).
2. Confira o usuário:

```sql
SELECT id, email, role, email_verified FROM users WHERE email = '<email-do-admin>';
```

3. Promova exatamente uma conta (faça backup antes):

```sql
UPDATE users SET role = 'ADMIN' WHERE email = '<email-do-admin>' AND email_verified = 1 LIMIT 1;
```

4. Entre no site e confirme o acesso ao `/admin`. Depois use o painel para promover outros, se necessário.

Não existe senha ou conta de administrador padrão no código. Não crie administradores com senhas de teste em produção.
