#!/usr/bin/env bash
# Sobe o backend contra um MySQL 8.0 local e descartável (Docker). Nunca toca em produção.
#
#   bash scripts/dev-local.sh          # sobe o MySQL (se preciso) e roda o backend
#   bash scripts/dev-local.sh --stop   # para o container MySQL (os dados ficam no volume)
#   bash scripts/dev-local.sh --reset  # APAGA container e volume locais (dados de dev)
#
# Os valores abaixo são exportados só para este processo. Variáveis de ambiente do SO têm
# precedência sobre o application.properties e sobre o .env importado por ele; mesmo assim o
# backend roda com working directory vazio (target/dev-local-run), então o .env nunca é lido.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="dn_store_dev_mysql"
VOLUME="dn_store_dev_mysql_data"
DB_PORT="33317"
DB_NAME="dn_store_dev"
DB_USER_LOCAL="dn_store_dev"
DB_PASS_LOCAL="dev-only-local-password"

if [[ "${1:-}" == "--stop" ]]; then
  docker stop "$CONTAINER"
  exit 0
fi
if [[ "${1:-}" == "--reset" ]]; then
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  docker volume rm "$VOLUME" >/dev/null 2>&1 || true
  echo "Container e volume locais removidos."
  exit 0
fi

command -v docker >/dev/null || { echo "ERRO: docker não encontrado." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "ERRO: o Docker não está rodando." >&2; exit 1; }

# (a) container MySQL: reaproveita se existir, cria se não.
if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
  docker start "$CONTAINER" >/dev/null
else
  echo "Criando container $CONTAINER (mysql:8.0, 127.0.0.1:$DB_PORT)..."
  docker run -d --name "$CONTAINER" \
    -p "127.0.0.1:$DB_PORT:3306" \
    -v "$VOLUME:/var/lib/mysql" \
    -e MYSQL_ROOT_PASSWORD="dev-only-root-password" \
    -e MYSQL_DATABASE="$DB_NAME" \
    -e MYSQL_USER="$DB_USER_LOCAL" \
    -e MYSQL_PASSWORD="$DB_PASS_LOCAL" \
    --health-cmd='mysqladmin ping -h 127.0.0.1 -uroot -pdev-only-root-password --silent' \
    --health-interval=3s --health-timeout=3s --health-retries=40 \
    mysql:8.0 >/dev/null
fi

echo -n "Aguardando o MySQL ficar saudável"
for _ in $(seq 1 60); do
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$CONTAINER")"
  [[ "$status" == "healthy" ]] && break
  echo -n "."
  sleep 2
done
echo
[[ "$status" == "healthy" ]] || { echo "ERRO: MySQL não ficou saudável (status: $status)." >&2; exit 1; }

# (b) ambiente do backend: tudo local, nada de integrações reais.
# Remove overrides herdados do shell que poderiam redirecionar o banco ou a configuração.
unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
      SPRING_FLYWAY_URL SPRING_FLYWAY_USER SPRING_FLYWAY_PASSWORD SPRING_CONFIG_IMPORT \
      SPRING_CONFIG_ADDITIONAL_LOCATION SPRING_CONFIG_LOCATION SPRING_APPLICATION_JSON \
      DB_PASSWORD

export DB_URL="jdbc:mysql://127.0.0.1:$DB_PORT/$DB_NAME?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USER="$DB_USER_LOCAL"
export DB_PASS="$DB_PASS_LOCAL"
export SPRING_PROFILES_ACTIVE="local"
export DDL_AUTO="validate"
export PORT=8080
# O JwtService decodifica JWT_SECRET como base64 padrão (>= 256 bits); este valor é só de dev.
export JWT_SECRET="ZGV2LWxvY2FsLW9ubHktand0LXNlY3JldC1ub3QtZm9yLXByb2R1Y3Rpb24tMDEyMzQ1Njc4OWFiY2RlZg=="
export AUTH_CHALLENGE_SECRET="dev-local-only-challenge-secret-not-for-production-0123456789abcdef"
export CORS_ALLOWED_ORIGINS="http://localhost:3000"
export FRONTEND_URL="http://localhost:3000"
export RESEND_API_KEY=""
export ASAAS_ENVIRONMENT="sandbox"
export ASAAS_BASE_URL="https://api-sandbox.asaas.com/v3"
export ASAAS_API_KEY=""
export ASAAS_WEBHOOK_TOKEN=""
export SHIPPING_GATEWAY="simulated"
export MELHOR_ENVIO_CLIENT_ID=""
export MELHOR_ENVIO_CLIENT_SECRET=""
export MELHOR_ENVIO_TOKEN_ENCRYPTION_KEY=""
# Schedulers/workers inofensivos: um ciclo a cada 24h.
export SHIPPING_CLEANUP_INTERVAL_MS=86400000
export PAYMENT_EXPIRATION_SCAN_MS=86400000
export PAYMENT_WEBHOOK_SCAN_MS=86400000
export MELHOR_ENVIO_RENEWAL_INTERVAL_MS=86400000
export MELHOR_ENVIO_RENEWAL_INITIAL_DELAY_MS=86400000

# (c) trava de segurança: só segue se o banco for local.
if [[ "$DB_URL" != *127.0.0.1* && "$DB_URL" != *localhost* ]]; then
  echo "ERRO: DB_URL não aponta para 127.0.0.1/localhost. Abortando." >&2
  exit 1
fi

RUN_DIR="$REPO_ROOT/backendJava/target/dev-local-run"
mkdir -p "$RUN_DIR"
cd "$REPO_ROOT/backendJava"
echo "Backend em http://localhost:8080/api  (banco: 127.0.0.1:$DB_PORT/$DB_NAME)"
exec ./mvnw spring-boot:run -Dspring-boot.run.workingDirectory="$RUN_DIR"
