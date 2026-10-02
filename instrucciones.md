# Instrucciones de ejecución y prueba

Desde la raíz del repositorio se levanta el stack y se prueba cada componente con los comandos de abajo.

## Prerrequisitos

- Java 21
- Docker Desktop (o un daemon Docker compatible) con Compose v2
- Maven 3.9+ (o el wrapper del módulo de migración si se usa de forma aislada)

## Certificados TLS de desarrollo

Los tres BFFs sirven HTTPS con certificados de una CA de desarrollo autofirmada; `bff-atm` además exige un certificado de cliente (mTLS) del terminal. `core-service` es plano HTTP salvo su conector de verificación de PIN (puerto 8453).

Generar (o regenerar) la CA y todos los certificados:

```bash
./scripts/generate-dev-tls-certs.sh
```

Esto escribe `dev/certs/` (montado por `docker-compose.yml`) y una copia bajo `src/test/resources/tls/` en cada módulo que la necesita. Es dev-only: nunca reutilices esta CA en un entorno real.

Para que `curl` acepte la cadena autofirmada sin desactivar la validación, pásale la CA con `--cacert dev/certs/ca.crt`; alternativamente, `-k` la ignora por completo. Para confiar en la CA a nivel de sistema/navegador:

```bash
# macOS
security add-trusted-cert -d -r trustRoot -k ~/Library/Keychains/login.keychain-db dev/certs/ca.crt

# Linux (Debian/Ubuntu)
sudo cp dev/certs/ca.crt /usr/local/share/ca-certificates/xyz-bank-dev-ca.crt && sudo update-ca-certificates
```

## Arranque

```bash
docker compose up --build
```

| Servicio | Puerto |
|---|---|
| MySQL 8.4 | 3306 |
| PostgreSQL 16 | 5432 |
| config-server | 8888 |
| eureka-server | 8761 |
| authorization-server | 9000 |
| Kafka (KRaft) | 9092 |
| core-service | 8080, 8453 |
| interests-service | 8084 |
| bff-web | 8081 |
| bff-mobile | 8082 |
| bff-atm | 8083 |
| gateway | 8090 |

`data-migration` es un job de una sola ejecución. Procesa `data/legacy/movimientos_financieros_diarios.csv`, `data/legacy/intereses_trimestrales.csv` y `data/legacy/estados_financieros_anuales.csv` con `dailyTransactionsJob`, `monthlyInterestsJob` y `annualGenerationJob`.

Enrutamiento de intereses en `bff-web`:

| Variable | Default en Compose | Efecto |
|---|---|---|
| `FEATURE_USE_INTERESTS_SERVICE` | `true` | `true`: `bff-web` llama a `interests-service`. `false`: llama a `core-service` en `/internal/accounts/{id}/interest-summary`. |
| `INTERESTS_SERVICE_BASE_URL` | `http://interests-service:8084` | Base URL de `interests-service`. |
| `FEATURE_INTEREST_CREDIT_VIA_KAFKA` | `false` | `false`: `interests-service` acredita por HTTP. `true`: publica `InterestCalculated` en `interests.calculated` y no llama a `creditInterest`. El GET de resumen no cambia. |
| `FEATURE_TRANSACTION_CONFIRMED_EVENTS` | `true` (Compose) / `false` (app default) | `true`: `core-service` escribe `TransactionConfirmed` en el outbox y el relay lo publica en `transactions.confirmed`. Independiente de la saga de intereses. |

```bash
FEATURE_USE_INTERESTS_SERVICE=false docker compose up -d bff-web
FEATURE_INTEREST_CREDIT_VIA_KAFKA=true docker compose up -d core-service interests-service
docker compose down
docker compose down -v
```

Con `EUREKA_CLIENT_ENABLED=true` los tres BFF llaman a `http://core-service` y `register-with-eureka` es `false`. Sin esa variable mantienen `http://localhost:8080`.

## Datos de demo

| Recurso | Identificador |
|---|---|
| Cliente | `11111111-1111-1111-1111-111111111111` |
| Cuenta | `22222222-2222-2222-2222-222222222222` |
| Número de cuenta | `1000000001` |
| Tarjeta | `77777777-7777-7777-7777-777777777777` |
| PIN | `1234` |
| Resumen de intereses | año `2025` |

## data-migration

```bash
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT * FROM migration_executions;"
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT COUNT(*) FROM daily_transaction_reports;"
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT COUNT(*) FROM account_balances;"
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT COUNT(*) FROM annual_audit_reports;"
```

`status = SUCCESS` en `migration_executions` para `dailyTransactionsJob`, `monthlyInterestsJob` y `annualGenerationJob` indica que el job ya corrió.

Para el job CSV con MySQL aislado, el Compose del módulo es [`data-migration/docker-compose.yml`](data-migration/docker-compose.yml).

## Salud de cada servicio

```bash
curl -sS http://localhost:8080/actuator/health
curl -sS http://localhost:8084/actuator/health
curl -sS http://localhost:8888/actuator/health
curl -sS http://localhost:8761/actuator/health
curl -sS http://localhost:9000/oauth2/jwks
curl -sS http://localhost:8090/actuator/health
curl -sS --cacert dev/certs/ca.crt https://localhost:8081/v3/api-docs
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

`kafka-init` crea `interests.calculated`, `interests.credit-results`, `transactions.confirmed` y `security.alerts`.

## Gateway :8090

El gateway escucha en el puerto 8090. Quita el primer segmento y reenvía `/web` a `bff-web` :8081, `/mobile` a `bff-mobile` :8082 y `/atm` a `bff-atm` :8083.

```bash
curl -sS http://localhost:8090/actuator/health

curl -sS http://localhost:8090/web/customers/11111111-1111-1111-1111-111111111111/dashboard \
  -b "session=$SESSION_COOKIE"

curl -sS "http://localhost:8090/web/accounts/22222222-2222-2222-2222-222222222222/interest-summary?year=2025" \
  -b "session=$SESSION_COOKIE"

curl -sS http://localhost:8090/mobile/accounts/22222222-2222-2222-2222-222222222222/summary \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H "X-Device-Id: $DEVICE_ID"

TERMINAL_CERT="dev/certs/atm-terminal/keystore.p12:xyzbank-dev"

curl -sS --cacert dev/certs/ca.crt \
  --cert-type P12 --cert "$TERMINAL_CERT" \
  -X POST http://localhost:8090/atm/pin-verifications \
  -H "Content-Type: application/json" \
  -d '{"cardNumber":"77777777-7777-7777-7777-777777777777","pin":"1234"}'
```

## bff-atm :8083

```bash
TERMINAL_CERT="dev/certs/atm-terminal/keystore.p12:xyzbank-dev"

SESSION_TOKEN=$(curl -sS --cacert dev/certs/ca.crt \
  --cert-type P12 --cert "$TERMINAL_CERT" \
  -X POST https://localhost:8083/pin-verifications \
  -H "Content-Type: application/json" \
  -d '{"cardNumber":"77777777-7777-7777-7777-777777777777","pin":"1234"}' \
  | jq -r .sessionToken)

curl -sS --cacert dev/certs/ca.crt \
  --cert-type P12 --cert "$TERMINAL_CERT" \
  https://localhost:8083/accounts/22222222-2222-2222-2222-222222222222/balance \
  -H "Authorization: Bearer $SESSION_TOKEN"

curl -sS --cacert dev/certs/ca.crt \
  --cert-type P12 --cert "$TERMINAL_CERT" \
  -X POST https://localhost:8083/accounts/22222222-2222-2222-2222-222222222222/withdrawals \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -H "Idempotency-Key: demo-withdrawal-1" \
  -d '{"amount":40.00,"currency":"USD"}'
```

La sesión expira a los 120 segundos: repite el paso 1 si el paso 2 o 3 devuelven 422. Tres PINs incorrectos seguidos bloquean la tarjeta demo (423 en adelante, incluso con el PIN correcto); desbloquéala con:

```bash
./scripts/reset-dev-card-lock.sh
```

## bff-web :8081 y bff-mobile :8082

`bff-web` usa la cookie httpOnly `session`. `bff-mobile` usa `Authorization: Bearer` junto con `X-Device-Id`.

```bash
curl -sS --cacert dev/certs/ca.crt \
  -b "session=$SESSION_COOKIE" \
  https://localhost:8081/customers/11111111-1111-1111-1111-111111111111/dashboard

curl -sS --cacert dev/certs/ca.crt \
  -b "session=$SESSION_COOKIE" \
  "https://localhost:8081/accounts/22222222-2222-2222-2222-222222222222/interest-summary?year=2025"

curl -sS --cacert dev/certs/ca.crt \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H "X-Device-Id: $DEVICE_ID" \
  https://localhost:8082/accounts/22222222-2222-2222-2222-222222222222/summary
```

## core-service :8080

Las llamadas de abajo van a `http://localhost:8080` con la cabecera `X-Service-Credential: dev-service-credential-web`. Depósitos, transferencias y pagos externos llevan `Idempotency-Key`. El PIN del cajero sigue en el conector TLS `8453`; el ejemplo de verificación está en la sección de `bff-atm`.

Abrir dos cuentas del cliente demo, mantener el número de la primera, depositar, transferir, pagar a un destinatario externo, cerrar la cuenta destino y actualizar el perfil:

```bash
SERVICE_CREDENTIAL="dev-service-credential-web"
CUSTOMER_ID="11111111-1111-1111-1111-111111111111"

ACCOUNT_ID=$(curl -sS -X POST http://localhost:8080/internal/accounts \
  -H "Content-Type: application/json" \
  -H "X-Service-Credential: $SERVICE_CREDENTIAL" \
  -d "{\"accountNumber\":\"2000000001\",\"customerId\":\"$CUSTOMER_ID\",\"balance\":0.00,\"currency\":\"USD\"}" \
  | jq -r .id)

DESTINATION_ID=$(curl -sS -X POST http://localhost:8080/internal/accounts \
  -H "Content-Type: application/json" \
  -H "X-Service-Credential: $SERVICE_CREDENTIAL" \
  -d "{\"accountNumber\":\"2000000002\",\"customerId\":\"$CUSTOMER_ID\",\"balance\":0.00,\"currency\":\"USD\"}" \
  | jq -r .id)

curl -sS -X PUT "http://localhost:8080/internal/accounts/$ACCOUNT_ID" \
  -H "Content-Type: application/json" \
  -H "X-Service-Credential: $SERVICE_CREDENTIAL" \
  -d '{"accountNumber":"2000000005"}'

curl -sS -X POST "http://localhost:8080/internal/accounts/$ACCOUNT_ID/deposits" \
  -H "Content-Type: application/json" \
  -H "X-Service-Credential: $SERVICE_CREDENTIAL" \
  -H "Idempotency-Key: e2e-deposit-1" \
  -d '{"amount":100.00,"currency":"USD"}'

curl -sS -X POST "http://localhost:8080/internal/accounts/$ACCOUNT_ID/transfers" \
  -H "Content-Type: application/json" \
  -H "X-Service-Credential: $SERVICE_CREDENTIAL" \
  -H "Idempotency-Key: e2e-transfer-1" \
  -d "{\"destinationAccountId\":\"$DESTINATION_ID\",\"amount\":40.00,\"currency\":\"USD\"}"

curl -sS -X POST "http://localhost:8080/internal/accounts/$ACCOUNT_ID/external-payments" \
  -H "Content-Type: application/json" \
  -H "X-Service-Credential: $SERVICE_CREDENTIAL" \
  -H "Idempotency-Key: e2e-payment-1" \
  -d '{"recipient":"North Grid","amount":80.00,"currency":"USD"}'

curl -sS -X POST "http://localhost:8080/internal/accounts/$DESTINATION_ID/closures" \
  -H "X-Service-Credential: $SERVICE_CREDENTIAL"

curl -sS -X PUT "http://localhost:8080/internal/customers/$CUSTOMER_ID" \
  -H "Content-Type: application/json" \
  -H "X-Service-Credential: $SERVICE_CREDENTIAL" \
  -d '{"fullName":"Ana Soto","email":"ana.soto@xyzbank.cl"}'
```

Repetir la apertura con el número `2000000001` o `1000000001` responde 409. Un monto `0.00` en el depósito responde 422.

## Tests

```bash
# Unitarios y E2E que no requieren failsafe (incluye Testcontainers saltados si no hay Docker)
mvn test

# Incluye tests de integración (`*IT`)
mvn verify
```

Los ITs de PostgreSQL/MySQL usan Testcontainers. Sin Docker se omiten (`disabledWithoutDocker`) en lugar de fallar.

## Si algo falla

- **Puertos 3306 o 5432 ocupados.** Otro MySQL/Postgres local está usando el puerto. Para este stack esos puertos deben estar libres, o para el stack con `docker compose down` (eso no apaga bases de otros proyectos).
- **Puertos 8084, 8888, 8761 o 9092 ocupados.** Otro proceso está usando el puerto de `interests-service`, `config-server`, `eureka-server` o Kafka. Libéralos o baja el stack con `docker compose down`.
- **El seed de demo desapareció o el dashboard da 404.** Flyway no reinserta filas de una versión ya aplicada. Reset: `docker compose down -v` y vuelve a `up --build`.
- **La migración falló y core-service no arranca.** Compose espera `service_completed_successfully`. Revisa `docker compose logs data-migration`.
- **PostgreSQL cae con el stack ya arriba.** `GET http://localhost:8080/actuator/health` deja de reportar UP (Actuator incluye el datasource). Los BFFs no tienen base propia: su health sigue UP aunque Postgres esté caído.
- **Testcontainers skipped.** Arranca Docker Desktop y vuelve a `mvn verify`.
- **Solo quieres experimentar el job CSV.** Sigue usando [`data-migration/docker-compose.yml`](data-migration/docker-compose.yml) (MySQL aislado). El camino soportado de plataforma completa es el Compose de la raíz.
- **`curl` falla el handshake TLS contra `bff-atm` con un certificado de cliente (`error:...SSL routines:ST_CONNECT:tlsv1 alert protocol version` o similar).** El `curl`/LibreSSL que trae macOS de fábrica tiene problemas negociando TLS con certificados de cliente P12 contra este stack. Instala una build de `curl` enlazada con OpenSSL (p. ej. `brew install curl`) o usa `openssl s_client` para depurar la conexión.
