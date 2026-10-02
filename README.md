# XYZ Bank Server

Repositorio: https://github.com/L0ncho/xyz_bank_server_EFT

Plataforma BFF de XYZ Bank: tres backends por canal (`bff-web`, `bff-mobile`, `bff-atm`) frente a un `core-service` interno y un `interests-service` extraído (con `config-server` y `eureka-server`), más un job de migración CSV hacia MySQL.

**Autenticación y HTTPS están implementadas con configuración de desarrollo.** Cada canal se autentica con una credencial real — cookie de sesión (web), JWT de dispositivo (mobile), o certificado mTLS del terminal más un PIN de tarjeta (ATM) — pero todo el material de confianza es de dev/test: un proveedor OIDC simulado (mock, ver `platform/*/src/test/.../MockOidcProvider`), un secreto de firma de sesión fijo, credenciales de servicio por BFF fijas, y una CA de desarrollo autofirmada (`scripts/generate-dev-tls-certs.sh`). Antes de un despliegue real hace falta: un IdP externo real, una CA gestionada que emita certificados reales, y credenciales de servicio rotadas por BFF.

El login de `bff-web`/`bff-mobile` pasa por ese proveedor OIDC simulado, que solo existe como fixture de test (WireMock, arrancado por los propios tests) — no corre como servicio dentro de `docker compose up`. Por eso los ejemplos de `curl` de este README no incluyen el login completo de web/mobile: se pueden ejercitar levantando ese fixture vía los tests (`mvn test` en `bff-web`/`bff-mobile`), o conectando un proveedor OIDC real. El flujo de ATM (verificación de PIN) no depende de ningún proveedor externo y sí es 100% ejecutable contra el stack de `docker compose`, como se muestra más abajo.

## Prerrequisitos

- Java 21
- Docker Desktop (o un daemon Docker compatible) con Compose v2
- Maven 3.9+ (o el wrapper del módulo de migración si se usa de forma aislada)

## Topología del proyecto

Cada canal prueba la identidad del llamante con una credencial real en vez de una cabecera de confianza: cookie de sesión OAuth2/OIDC para web, JWT de dispositivo para mobile, y mTLS más una sesión verificada por PIN para ATM. Todo borde de cara al cliente es TLS; el borde BFF→plataforma sigue siendo HTTP plano salvo la única llamada que transporta un PIN, que es TLS-only por diseño. `bff-web` enruta el resumen de intereses a `interests-service` (flag `FEATURE_USE_INTERESTS_SERVICE`, default `true`). El GET de resumen sigue siendo síncrono: `interests-service` reenvía el Bearer del usuario a `core-service`. La acreditación anual, con `FEATURE_INTEREST_CREDIT_VIA_KAFKA` en `false` (default), sigue siendo el POST HTTP con scope `interests:write`. Con la flag en `true`, `interests-service` publica `InterestCalculated` y `core-service` responde por `interests.credit-results` (saga coreografiada; ver [`docs/adr/002-event-architecture.md`](docs/adr/002-event-architecture.md)).

El `gateway` escucha en el puerto 8090 y enruta `/web`, `/mobile` y `/atm`. Con `EUREKA_CLIENT_ENABLED=true` los tres BFF llaman a `http://core-service` y `register-with-eureka` es `false`. Sin esa variable mantienen `http://localhost:8080`. `core-service` también abre, cierra y mantiene cuentas, acepta depósitos, transferencias y pagos externos, y actualiza el perfil del cliente. El PIN sigue en el conector TLS 8453.

```mermaid
flowchart LR
  subgraph clients [Clients]
    WebClient[Web client]
    MobileClient[Mobile client]
    AtmClient[ATM client]
  end

  Gateway[gateway :8090]

  subgraph bffs [BFFs - channel auth]
    BffWeb[bff-web :8081 OAuth2/OIDC session cookie]
    BffMobile[bff-mobile :8082 device-bound JWT]
    BffAtm[bff-atm :8083 mTLS + PIN session]
  end

  subgraph platform [Platform]
    ConfigServer[config-server :8888]
    EurekaServer[eureka-server :8761]
    InterestsService[interests-service :8084]
  end

  CoreService[core-service :8080]
  CoreServicePin[core-service :8453 PIN-verification connector]
  Kafka[(Kafka KRaft :9092)]
  Postgres[(PostgreSQL 16)]
  MySQL[(MySQL 8.4)]
  Migration[data-migration one-shot]

  WebClient -- HTTPS --> Gateway
  MobileClient -- HTTPS --> Gateway
  AtmClient -- HTTPS --> Gateway
  Gateway -- "/web" --> BffWeb
  Gateway -- "/mobile" --> BffMobile
  Gateway -- "/atm" --> BffAtm
  WebClient -- HTTPS --> BffWeb
  MobileClient -- HTTPS --> BffMobile
  AtmClient -- HTTPS + mTLS --> BffAtm
  BffWeb -- HTTP --> InterestsService
  BffWeb -- HTTP --> CoreService
  BffMobile -- HTTP --> CoreService
  BffAtm -- HTTP --> CoreService
  BffAtm -- HTTPS --> CoreServicePin
  InterestsService -- HTTP --> CoreService
  InterestsService -- "InterestCalculated" --> Kafka
  Kafka -- "InterestCalculated" --> CoreService
  CoreService -- "InterestCreditApplied / Rejected" --> Kafka
  Kafka -- "credit result" --> InterestsService
  CoreService -- "TransactionConfirmed" --> Kafka
  CoreService -- "security.alerts" --> Kafka
  BffAtm -- "retiro síncrono HTTP" --> CoreService
  InterestsService --> ConfigServer
  InterestsService --> EurekaServer
  CoreService --> EurekaServer
  CoreService --> Postgres
  CoreServicePin -.-> CoreService
  Migration --> MySQL
```

`CoreServicePin` es un segundo conector Tomcat del mismo `core-service`, no un servicio aparte — comparte proceso y acceso a base de datos; se dibuja por separado solo para mostrar que ese conector exige TLS mientras el resto de `core-service` sigue en HTTP plano. `interests-service` toma configuración de `config-server` (repo nativo `config-repo/`), se registra en Eureka, descubre `core-service` por nombre de servicio (LoadBalancer), y aplica tolerancia a fallos con Resilience4j (circuit breaker) hacia core en las llamadas HTTP (resumen, saldo y, con la flag de Kafka apagada, el crédito). Kafka es un broker único en KRaft, sin ZooKeeper. Los tópicos `interests.calculated`, `interests.credit-results`, `transactions.confirmed` y `security.alerts` los crea `kafka-init` al arrancar. Cada movimiento de dinero confirmado en `core-service` (retiro ATM síncrono o crédito de interés) publica `TransactionConfirmed` vía outbox a `transactions.confirmed` (clave `accountId`). La flecha HTTP de intereses a core sigue siendo el camino del GET y del crédito síncrono. Detalle completo de cada credencial por canal en `docs/contracts/*/openapi.yaml` y en `docs/architecture.md`.

## Arranque local

Desde la raíz del repositorio:

```bash
docker compose up --build
```

Eso levanta:

| Servicio | Puerto | Rol |
|---|---|---|
| MySQL 8.4 | 3306 | Reportes de la migración CSV |
| PostgreSQL 16 | 5432 | Datos de `core-service` |
| data-migration | (one-shot) | Procesa los CSV `data/legacy/movimientos_financieros_diarios.csv`, `data/legacy/intereses_trimestrales.csv` y `data/legacy/estados_financieros_anuales.csv` con `dailyTransactionsJob`, `monthlyInterestsJob` y `annualGenerationJob`, y sale con código 0 |
| config-server | 8888 | Configuración nativa (`config-repo/`) |
| eureka-server | 8761 | Service discovery |
| authorization-server | 9000 | Emisor OAuth2/OIDC |
| Kafka (KRaft) | 9092 | Broker de la saga de intereses |
| core-service | 8080, 8453 | API interna de dominio: abre, cierra y mantiene cuentas; depósitos, transferencias, pagos externos y actualización del perfil del cliente. 8453 es el conector TLS de PIN |
| interests-service | 8084 | Cálculo/acreditación de intereses anuales |
| bff-web | 8081 | Dashboard, historial e intereses |
| bff-mobile | 8082 | Resumen aplanado de cuenta |
| bff-atm | 8083 | Saldo y retiro |
| gateway | 8090 | Enruta `/web`, `/mobile` y `/atm` |

El job espera a que MySQL esté sano. `kafka-init` espera a que el broker esté sano y crea los tópicos `interests.calculated`, `interests.credit-results`, `transactions.confirmed` y `security.alerts`. `core-service` espera a PostgreSQL, a que la migración termine con éxito, a Eureka y a `kafka-init`. `eureka-server` espera a `config-server`. `interests-service` espera a `config-server`, `eureka-server`, `core-service` y `kafka-init`. Los BFFs esperan a que `core-service` reporte `/actuator/health` en UP; `bff-web` además espera a `interests-service`.

Enrutamiento de intereses en `bff-web`:

| Variable | Default en Compose | Efecto |
|---|---|---|
| `FEATURE_USE_INTERESTS_SERVICE` | `true` | `true`: `bff-web` llama a `interests-service`. `false`: llama a `core-service` en `/internal/accounts/{id}/interest-summary`. |
| `INTERESTS_SERVICE_BASE_URL` | `http://interests-service:8084` | Base URL de `interests-service`. |
| `FEATURE_INTEREST_CREDIT_VIA_KAFKA` | `false` | `false`: `interests-service` acredita por HTTP. `true`: publica `InterestCalculated` en `interests.calculated` y no llama a `creditInterest`. El GET de resumen no cambia. |
| `FEATURE_TRANSACTION_CONFIRMED_EVENTS` | `true` (Compose) / `false` (app default) | `true`: `core-service` escribe `TransactionConfirmed` en el outbox y el relay lo publica en `transactions.confirmed`. Independiente de la saga de intereses. |

Para forzar el path legacy del resumen: `FEATURE_USE_INTERESTS_SERVICE=false docker compose up -d bff-web`.

Para acreditar por la saga: `FEATURE_INTEREST_CREDIT_VIA_KAFKA=true docker compose up -d core-service interests-service`. Eso enciende el listener, el outbox y el relay de `core-service` y el productor/consumidor de `interests-service`. Con la flag en `false` el crédito HTTP no escribe outbox de intereses; `FEATURE_TRANSACTION_CONFIRMED_EVENTS` sigue pudiendo publicar movimientos confirmados.

Para apagar: `docker compose down`. Para resetear volúmenes (incluido el seed de demo): `docker compose down -v`.

## Datos de demo

Tras un arranque limpio, PostgreSQL contiene un cliente y una cuenta fijos (Flyway `V6__seed_demo_data.sql`). No se copian filas desde MySQL.

| Recurso | UUID |
|---|---|
| Cliente | `11111111-1111-1111-1111-111111111111` |
| Cuenta | `22222222-2222-2222-2222-222222222222` |
| Número de cuenta | `1000000001` |
| Resumen de intereses | año `2025` |

## Verificar la migración (MySQL)

```bash
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT * FROM migration_executions;"
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT COUNT(*) FROM daily_transaction_reports;"
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT COUNT(*) FROM account_balances;"
docker compose exec mysql mysql -umigration -pmigration xyz_bank_migration -e "SELECT COUNT(*) FROM annual_audit_reports;"
```

Las rutas CSV por defecto son `data/legacy/movimientos_financieros_diarios.csv`, `data/legacy/intereses_trimestrales.csv` y `data/legacy/estados_financieros_anuales.csv`. `status = SUCCESS` en `migration_executions` para `dailyTransactionsJob`, `monthlyInterestsJob` y `annualGenerationJob` indica que el job ya corrió. Un segundo `docker compose up` reutiliza el volumen y el job vuelve a salir 0 (ya migrado).

## Certificados TLS de desarrollo

Los tres BFFs sirven HTTPS con certificados de una CA de desarrollo autofirmada; `bff-atm` además exige un certificado de cliente (mTLS) del terminal. `core-service` es plano HTTP salvo su conector de verificación de PIN (puerto 8453), que es TLS-only por diseño — ver `docs/contracts/core-service/openapi.yaml`.

Generar (o regenerar) la CA y todos los certificados:

```bash
./scripts/generate-dev-tls-certs.sh
```

Esto escribe `dev/certs/` (montado por `docker-compose.yml`) y una copia bajo `src/test/resources/tls/` en cada módulo que la necesita. Es dev-only: nunca reutilices esta CA en un entorno real.

Para que `curl` acepte la cadena autofirmada sin desactivar la validación, pásale la CA con `--cacert dev/certs/ca.crt` (todos los ejemplos de abajo lo hacen); alternativamente, `-k` la ignora por completo. Para confiar en la CA a nivel de sistema/navegador (útil para abrir `bff-web` en un navegador):

```bash
# macOS
security add-trusted-cert -d -r trustRoot -k ~/Library/Keychains/login.keychain-db dev/certs/ca.crt

# Linux (Debian/Ubuntu)
sudo cp dev/certs/ca.crt /usr/local/share/ca-certificates/xyz-bank-dev-ca.crt && sudo update-ca-certificates
```

## Ejemplos de curl

Sustituye nada: estos IDs coinciden con el seed. El login OIDC de `bff-web`/`bff-mobile` no es ejercitable contra este stack (ver nota arriba); el flujo completo de ATM sí lo es.

**bff-atm — verificar PIN, consultar saldo y retirar**

La tarjeta demo (PIN `1234`) pertenece al cliente/cuenta del seed. Cada request debe presentar el certificado de cliente del terminal (mTLS):

```bash
TERMINAL_CERT="dev/certs/atm-terminal/keystore.p12:xyzbank-dev"

# 1. Verificar PIN -> obtiene una sesión de 120 segundos
SESSION_TOKEN=$(curl -sS --cacert dev/certs/ca.crt \
  --cert-type P12 --cert "$TERMINAL_CERT" \
  -X POST https://localhost:8083/pin-verifications \
  -H "Content-Type: application/json" \
  -d '{"cardNumber":"77777777-7777-7777-7777-777777777777","pin":"1234"}' \
  | jq -r .sessionToken)

# 2. Consultar saldo
curl -sS --cacert dev/certs/ca.crt \
  --cert-type P12 --cert "$TERMINAL_CERT" \
  https://localhost:8083/accounts/22222222-2222-2222-2222-222222222222/balance \
  -H "Authorization: Bearer $SESSION_TOKEN"

# 3. Retirar (Idempotency-Key evita un doble retiro si se reintenta la misma llamada)
curl -sS --cacert dev/certs/ca.crt \
  --cert-type P12 --cert "$TERMINAL_CERT" \
  -X POST https://localhost:8083/accounts/22222222-2222-2222-2222-222222222222/withdrawals \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -H "Idempotency-Key: demo-withdrawal-1" \
  -d '{"amount":40.00,"currency":"USD"}'
```

La sesión expira a los 120 segundos: repite el paso 1 si el paso 2 o 3 devuelven 422. Tres PINs incorrectos seguidos bloquean la tarjeta demo (423 en adelante, incluso con el PIN correcto); desbloquéala con `./scripts/reset-dev-card-lock.sh` (requiere el stack de `docker compose` arriba).

**bff-web / bff-mobile — una vez autenticado**

El login real requiere un proveedor OIDC (ver la nota al inicio de este README). Una vez completado, `bff-web` guarda la sesión en una cookie httpOnly (`session`) que el navegador reenvía solo; `bff-mobile` devuelve `sessionToken`/`refreshToken` en el cuerpo de la respuesta de login, que el cliente nativo debe reenviar como `Authorization: Bearer` junto con `X-Device-Id`. Con esos ya obtenidos, las llamadas de dominio lucen así:

```bash
# bff-web (cookie de sesión ya presente)
curl -sS --cacert dev/certs/ca.crt \
  -b "session=$SESSION_COOKIE" \
  https://localhost:8081/customers/11111111-1111-1111-1111-111111111111/dashboard

curl -sS --cacert dev/certs/ca.crt \
  -b "session=$SESSION_COOKIE" \
  "https://localhost:8081/accounts/22222222-2222-2222-2222-222222222222/interest-summary?year=2025"

# bff-mobile (JWT de dispositivo)
curl -sS --cacert dev/certs/ca.crt \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -H "X-Device-Id: $DEVICE_ID" \
  https://localhost:8082/accounts/22222222-2222-2222-2222-222222222222/summary
```

Health y OpenAPI:

```bash
curl -sS http://localhost:8080/actuator/health
curl -sS http://localhost:8084/actuator/health
curl -sS http://localhost:8888/actuator/health
curl -sS http://localhost:8761/actuator/health
curl -sS --cacert dev/certs/ca.crt https://localhost:8081/v3/api-docs
```

Contratos en el repo: [`docs/contracts/`](docs/contracts/). Arquitectura: [`docs/architecture.md`](docs/architecture.md). ADR de la saga de intereses: [`docs/adr/002-event-architecture.md`](docs/adr/002-event-architecture.md).

## Tests

```bash
# Unitarios y E2E que no requieren failsafe (incluye Testcontainers saltados si no hay Docker)
mvn test

# Incluye tests de integración (`*IT`)
mvn verify
```

Los ITs de PostgreSQL/MySQL usan Testcontainers. Sin Docker se omiten (`disabledWithoutDocker`) en lugar de fallar.

## Troubleshooting

- **Puertos 3306 o 5432 ocupados.** Otro MySQL/Postgres local está usando el puerto. Para este stack esos puertos deben estar libres, o para el stack con `docker compose down` (eso no apaga bases de otros proyectos).
- **Puertos 8084, 8888, 8761 o 9092 ocupados.** Otro proceso está usando el puerto de `interests-service`, `config-server`, `eureka-server` o Kafka. Libéralos o baja el stack con `docker compose down`.
- **El seed de demo desapareció o el dashboard da 404.** Flyway no reinserta filas de una versión ya aplicada. Reset: `docker compose down -v` y vuelve a `up --build`.
- **La migración falló y core-service no arranca.** Compose espera `service_completed_successfully`. Revisa `docker compose logs data-migration`.
- **PostgreSQL cae con el stack ya arriba.** `GET http://localhost:8080/actuator/health` deja de reportar UP (Actuator incluye el datasource). Los BFFs no tienen base propia: su health sigue UP aunque Postgres esté caído.
- **Testcontainers skipped.** Arranca Docker Desktop y vuelve a `mvn verify`.
- **Solo quieres experimentar el job CSV.** Sigue usando [`data-migration/docker-compose.yml`](data-migration/docker-compose.yml) (MySQL aislado). El camino soportado de plataforma completa es el Compose de la raíz.
- **`curl` falla el handshake TLS contra `bff-atm` con un certificado de cliente (`error:...SSL routines:ST_CONNECT:tlsv1 alert protocol version` o similar).** El `curl`/LibreSSL que trae macOS de fábrica tiene problemas negociando TLS con certificados de cliente P12 contra este stack. Instala una build de `curl` enlazada con OpenSSL (p. ej. `brew install curl`) o usa `openssl s_client` para depurar la conexión.
