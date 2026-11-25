#!/usr/bin/env bash
# Restaura um backup (.sql.gz) em um banco VAZIO. Recusa rodar em banco que já tenha tabelas,
# para nunca sobrescrever dados existentes.
#
# Uso:
#   DB_HOST=... DB_PORT=... DB_NAME=... DB_USER=... MYSQL_PWD='...' CONFIRM_RESTORE=yes \
#     ./scripts/db-restore.sh caminho/do/backup.sql.gz
#
# Para validar um backup sem risco, restaure em um banco novo/descartável (ver docs/BACKUP_RECUPERACAO.md).
set -euo pipefail

: "${DB_HOST:?defina DB_HOST}" "${DB_NAME:?defina DB_NAME}" "${DB_USER:?defina DB_USER}" "${MYSQL_PWD:?defina MYSQL_PWD}"
DB_PORT="${DB_PORT:-3306}"
DB_SSL_MODE="${DB_SSL_MODE:-REQUIRED}"
BACKUP_FILE="${1:?informe o arquivo .sql.gz}"

[ -f "$BACKUP_FILE" ] || { echo "Arquivo não encontrado: $BACKUP_FILE" >&2; exit 1; }
[ "${CONFIRM_RESTORE:-}" = "yes" ] || { echo "Defina CONFIRM_RESTORE=yes para confirmar a restauração em $DB_HOST/$DB_NAME." >&2; exit 1; }
gzip -t "$BACKUP_FILE"

MYSQL=(mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --ssl-mode="$DB_SSL_MODE")

TABLES=$("${MYSQL[@]}" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DB_NAME'")
if [ "$TABLES" != "0" ]; then
  echo "ERRO: o banco '$DB_NAME' já tem $TABLES tabela(s). Restauração recusada para não sobrescrever dados." >&2
  exit 1
fi

zcat "$BACKUP_FILE" | "${MYSQL[@]}" "$DB_NAME"
echo "Restauração concluída em $DB_HOST/$DB_NAME."
