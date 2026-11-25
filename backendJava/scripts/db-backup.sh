#!/usr/bin/env bash
# Backup lógico (mysqldump) do banco da DN Store. Somente leitura no banco.
#
# Uso:
#   DB_HOST=... DB_PORT=... DB_NAME=... DB_USER=... MYSQL_PWD='...' ./scripts/db-backup.sh [pasta-de-saída]
#
# Variáveis:
#   DB_HOST, DB_NAME, DB_USER, MYSQL_PWD   obrigatórias (a senha vai por MYSQL_PWD para não aparecer em `ps`)
#   DB_PORT                                padrão 3306
#   DB_SSL_MODE                            padrão REQUIRED (Aiven exige TLS); use DISABLED só em banco local de teste
# O arquivo gerado (.sql.gz) contém dados de clientes: guarde fora do Git, em local privado.
set -euo pipefail

: "${DB_HOST:?defina DB_HOST}" "${DB_NAME:?defina DB_NAME}" "${DB_USER:?defina DB_USER}" "${MYSQL_PWD:?defina MYSQL_PWD}"
DB_PORT="${DB_PORT:-3306}"
DB_SSL_MODE="${DB_SSL_MODE:-REQUIRED}"
OUT_DIR="${1:-.}"

mkdir -p "$OUT_DIR"
OUT_FILE="$OUT_DIR/dn_store_${DB_NAME}_$(date +%Y%m%d_%H%M%S).sql.gz"

# --single-transaction: snapshot consistente sem travar escritas (tabelas InnoDB).
# --no-tablespaces / --set-gtid-purged=OFF: evitam exigir privilégios extras em bancos gerenciados.
mysqldump \
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --ssl-mode="$DB_SSL_MODE" \
  --single-transaction --routines --triggers --hex-blob \
  --no-tablespaces --set-gtid-purged=OFF \
  "$DB_NAME" | gzip -9 > "$OUT_FILE"

# Falha cedo se o arquivo saiu vazio ou truncado.
gzip -t "$OUT_FILE"
if ! zcat "$OUT_FILE" | tail -n 5 | grep -q "Dump completed"; then
  echo "ERRO: backup sem a marca 'Dump completed'; descarte $OUT_FILE" >&2
  exit 1
fi
echo "Backup criado: $OUT_FILE ($(du -h "$OUT_FILE" | cut -f1))"
