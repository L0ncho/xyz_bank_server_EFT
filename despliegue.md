# Despliegue en AWS

El descriptor está en [`deploy/aws/ecs-task-definition.json`](deploy/aws/ecs-task-definition.json). Cubre los servicios de `docker-compose.yml`. Cada elemento de `taskDefinitions` es una familia ECS con `networkMode` `awsvpc` y `requiresCompatibilities` `FARGATE`.

`imageBuild` indica cómo construir la imagen en este repositorio. Al registrar la familia se usan `family` y `containerDefinitions` (imagen, puerto, health check, `environment` y `secrets`). El valor de cada secreto queda fuera del descriptor: aquí solo figura la referencia `valueFrom`.

## Construir las imágenes

Desde la raíz del repositorio. Las imágenes públicas se usan tal como las nombra el JSON.

```bash
docker build -f data-migration/Dockerfile -t xyz-bank-data-migration .
docker build -f platform/config-server/Dockerfile -t xyz-bank-config-server .
docker build -f platform/eureka-server/Dockerfile -t xyz-bank-eureka-server .
docker build -f platform/authorization-server/Dockerfile -t xyz-bank-authorization-server .
docker build -f platform/core-service/Dockerfile -t xyz-bank-core-service .
docker build -f platform/interests-service/Dockerfile -t xyz-bank-interests-service .
docker build -f bff/bff-web/Dockerfile -t xyz-bank-bff-web .
docker build -f bff/bff-mobile/Dockerfile -t xyz-bank-bff-mobile .
docker build -f bff/bff-atm/Dockerfile -t xyz-bank-bff-atm .
docker build -f platform/gateway/Dockerfile -t xyz-bank-gateway .
```

| Imagen | Origen |
|---|---|
| `mysql:8.4` | Imagen pública |
| `postgres:16-alpine` | Imagen pública |
| `xyz-bank-data-migration` | `data-migration/Dockerfile` |
| `xyz-bank-config-server` | `platform/config-server/Dockerfile` |
| `xyz-bank-eureka-server` | `platform/eureka-server/Dockerfile` |
| `xyz-bank-authorization-server` | `platform/authorization-server/Dockerfile` |
| `xyz-bank-core-service` | `platform/core-service/Dockerfile` |
| `apache/kafka:3.8.1` | Imagen pública, familias `xyz-bank-kafka` y `xyz-bank-kafka-init` |
| `xyz-bank-interests-service` | `platform/interests-service/Dockerfile` |
| `xyz-bank-bff-web` | `bff/bff-web/Dockerfile` |
| `xyz-bank-bff-mobile` | `bff/bff-mobile/Dockerfile` |
| `xyz-bank-bff-atm` | `bff/bff-atm/Dockerfile` |
| `xyz-bank-gateway` | `platform/gateway/Dockerfile` |

Publica cada imagen en el registro que vaya a leer ECS y registra la familia con ese URI en `image`.

## Registrar cada familia

Por cada objeto de `taskDefinitions`, registra una task definition con la `family`, el `networkMode`, `requiresCompatibilities` y el `containerDefinitions` de ese objeto. En el contenedor deja el `name`, la imagen, `portMappings`, `healthCheck`, `environment` y `secrets` que el JSON ya trae. `kafka-init` además lleva `entryPoint` y `command`.

Las referencias `valueFrom` se crean en el almacén de secretos antes del registro. El documento de la task definition guarda el nombre de la variable y la ruta `valueFrom`, no el valor.

Familias, en el orden del JSON:

1. `xyz-bank-mysql`
2. `xyz-bank-postgres`
3. `xyz-bank-data-migration`
4. `xyz-bank-config-server`
5. `xyz-bank-eureka-server`
6. `xyz-bank-authorization-server`
7. `xyz-bank-core-service`
8. `xyz-bank-kafka`
9. `xyz-bank-kafka-init`
10. `xyz-bank-interests-service`
11. `xyz-bank-bff-web`
12. `xyz-bank-bff-mobile`
13. `xyz-bank-bff-atm`
14. `xyz-bank-gateway`

### xyz-bank-mysql

Contenedor `mysql`, imagen `mysql:8.4`. Puerto **3306**.

Health check: `mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD"`. Intervalo 5 s, timeout 5 s, 20 reintentos.

| Variable | Valor en el descriptor |
|---|---|
| `MYSQL_DATABASE` | `xyz_bank_migration` |
| `MYSQL_USER` | `migration` |

| Variable | Referencia |
|---|---|
| `MYSQL_PASSWORD` | `xyz-bank/mysql/MYSQL_PASSWORD` |
| `MYSQL_ROOT_PASSWORD` | `xyz-bank/mysql/MYSQL_ROOT_PASSWORD` |

### xyz-bank-postgres

Contenedor `postgres`, imagen `postgres:16-alpine`. Puerto **5432**.

Health check: `pg_isready -U core_service -d core_service`. Intervalo 5 s, timeout 5 s, 20 reintentos.

| Variable | Valor en el descriptor |
|---|---|
| `POSTGRES_DB` | `core_service` |
| `POSTGRES_USER` | `core_service` |

| Variable | Referencia |
|---|---|
| `POSTGRES_PASSWORD` | `xyz-bank/postgres/POSTGRES_PASSWORD` |

### xyz-bank-data-migration

Contenedor `data-migration`, imagen `xyz-bank-data-migration`. Sin puerto publicado y sin health check.

| Variable | Valor en el descriptor |
|---|---|
| `DB_HOST` | `mysql` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `xyz_bank_migration` |
| `DB_USERNAME` | `migration` |
| `MIGRATION_RUN_ALL` | `true` |

| Variable | Referencia |
|---|---|
| `DB_PASSWORD` | `xyz-bank/data-migration/DB_PASSWORD` |

### xyz-bank-config-server

Contenedor `config-server`, imagen `xyz-bank-config-server`. Puerto **8888**.

Health check: `wget -qO- http://127.0.0.1:8888/actuator/health | grep -q UP`. Intervalo 10 s, timeout 5 s, 20 reintentos, start period 30 s.

| Variable | Valor en el descriptor |
|---|---|
| `CONFIG_REPO_PATH` | `/config-repo` |

Sin `secrets`.

### xyz-bank-eureka-server

Contenedor `eureka-server`, imagen `xyz-bank-eureka-server`. Puerto **8761**.

Health check: `wget -qO- http://127.0.0.1:8761/actuator/health | grep -q UP`. Intervalo 10 s, timeout 5 s, 20 reintentos, start period 40 s.

Sin `environment` y sin `secrets`.

### xyz-bank-authorization-server

Contenedor `authorization-server`, imagen `xyz-bank-authorization-server`. Puerto **9000**.

Health check: `wget -qO- http://127.0.0.1:9000/oauth2/jwks | grep -q keys`. Intervalo 10 s, timeout 5 s, 20 reintentos, start period 30 s.

Sin `environment` y sin `secrets`.

### xyz-bank-core-service

Contenedor `core-service`, imagen `xyz-bank-core-service`. Puertos **8080** y **8453**.

Health check: `wget -qO- http://127.0.0.1:8080/actuator/health | grep -q UP`. Intervalo 10 s, timeout 5 s, 20 reintentos, start period 40 s.

| Variable | Valor en el descriptor |
|---|---|
| `CONFIG_SERVER_URL` | `http://config-server:8888` |
| `DB_HOST` | `postgres` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `core_service` |
| `DB_USERNAME` | `core_service` |
| `TLS_PIN_KEYSTORE_PATH` | `file:/certs/keystore.p12` |
| `EUREKA_CLIENT_ENABLED` | `true` |
| `EUREKA_SERVER_URL` | `http://eureka-server:8761` |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` |
| `FEATURE_INTEREST_CREDIT_VIA_KAFKA` | `false` |
| `FEATURE_TRANSACTION_CONFIRMED_EVENTS` | `true` |
| `FEATURE_SECURITY_ALERTS` | `true` |
| `ISSUER_JWK_SET_URI` | `http://authorization-server:9000/oauth2/jwks` |

| Variable | Referencia |
|---|---|
| `DB_PASSWORD` | `xyz-bank/core-service/DB_PASSWORD` |
| `TLS_PIN_KEYSTORE_PASSWORD` | `xyz-bank/core-service/TLS_PIN_KEYSTORE_PASSWORD` |
| `SERVICE_CREDENTIAL_WEB` | `xyz-bank/core-service/SERVICE_CREDENTIAL_WEB` |
| `SERVICE_CREDENTIAL_MOBILE` | `xyz-bank/core-service/SERVICE_CREDENTIAL_MOBILE` |
| `SERVICE_CREDENTIAL_ATM` | `xyz-bank/core-service/SERVICE_CREDENTIAL_ATM` |
| `SERVICE_CREDENTIAL_INTERESTS` | `xyz-bank/core-service/SERVICE_CREDENTIAL_INTERESTS` |

### xyz-bank-kafka

Contenedor `kafka`, imagen `apache/kafka:3.8.1`. Puerto **9092**.

Health check: `/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`. Intervalo 10 s, timeout 10 s, 20 reintentos, start period 20 s.

| Variable | Valor en el descriptor |
|---|---|
| `KAFKA_NODE_ID` | `1` |
| `KAFKA_PROCESS_ROLES` | `broker,controller` |
| `KAFKA_LISTENERS` | `PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093` |
| `KAFKA_ADVERTISED_LISTENERS` | `PLAINTEXT://kafka:9092` |
| `KAFKA_CONTROLLER_LISTENER_NAMES` | `CONTROLLER` |
| `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` | `CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT` |
| `KAFKA_CONTROLLER_QUORUM_VOTERS` | `1@kafka:9093` |
| `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` | `1` |
| `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | `1` |
| `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR` | `1` |
| `KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS` | `0` |
| `CLUSTER_ID` | `4L6g3nShT-eMCtK--X86sw` |

Sin `secrets`.

### xyz-bank-kafka-init

Contenedor `kafka-init`, imagen `apache/kafka:3.8.1`. Sin puerto, sin health check y sin variables.

`entryPoint`: `/bin/bash`, `-c`.

`command`: crea, si no existen, los tópicos `interests.calculated`, `interests.credit-results`, `transactions.confirmed` y `security.alerts` en `kafka:9092`, con 1 partición y factor de replicación 1.

### xyz-bank-interests-service

Contenedor `interests-service`, imagen `xyz-bank-interests-service`. Puerto **8084**.

Health check: `wget -qO- http://127.0.0.1:8084/actuator/health | grep -q UP`. Intervalo 10 s, timeout 5 s, 20 reintentos, start period 40 s.

| Variable | Valor en el descriptor |
|---|---|
| `CONFIG_SERVER_URL` | `http://config-server:8888` |
| `EUREKA_SERVER_URL` | `http://eureka-server:8761` |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` |
| `FEATURE_INTEREST_CREDIT_VIA_KAFKA` | `false` |

| Variable | Referencia |
|---|---|
| `CORE_SERVICE_CREDENTIAL` | `xyz-bank/interests-service/CORE_SERVICE_CREDENTIAL` |
| `CHANNEL_AUTH_JWT_SECRET` | `xyz-bank/interests-service/CHANNEL_AUTH_JWT_SECRET` |

### xyz-bank-bff-web

Contenedor `bff-web`, imagen `xyz-bank-bff-web`. Puerto **8081**.

Health check: `wget --no-check-certificate -qO- https://127.0.0.1:8081/actuator/health | grep -q UP`. Intervalo 10 s, timeout 5 s, 20 reintentos, start period 30 s.

| Variable | Valor en el descriptor |
|---|---|
| `CONFIG_SERVER_URL` | `http://config-server:8888` |
| `EUREKA_CLIENT_ENABLED` | `true` |
| `EUREKA_SERVER_URL` | `http://eureka-server:8761` |
| `CORE_SERVICE_BASE_URL` | `http://core-service` |
| `OIDC_AUTHORIZATION_URI` | `http://localhost:9000/oauth2/authorize` |
| `OIDC_TOKEN_URI` | `http://authorization-server:9000/oauth2/token` |
| `OIDC_JWK_SET_URI` | `http://authorization-server:9000/oauth2/jwks` |
| `INTERESTS_SERVICE_BASE_URL` | `http://interests-service:8084` |
| `FEATURE_USE_INTERESTS_SERVICE` | `true` |
| `TLS_KEYSTORE_PATH` | `file:/certs/keystore.p12` |

| Variable | Referencia |
|---|---|
| `CORE_SERVICE_CREDENTIAL` | `xyz-bank/bff-web/CORE_SERVICE_CREDENTIAL` |
| `TLS_KEYSTORE_PASSWORD` | `xyz-bank/bff-web/TLS_KEYSTORE_PASSWORD` |

### xyz-bank-bff-mobile

Contenedor `bff-mobile`, imagen `xyz-bank-bff-mobile`. Puerto **8082**.

Health check: `wget --no-check-certificate -qO- https://127.0.0.1:8082/actuator/health | grep -q UP`. Intervalo 10 s, timeout 5 s, 20 reintentos, start period 30 s.

| Variable | Valor en el descriptor |
|---|---|
| `CONFIG_SERVER_URL` | `http://config-server:8888` |
| `EUREKA_CLIENT_ENABLED` | `true` |
| `EUREKA_SERVER_URL` | `http://eureka-server:8761` |
| `CORE_SERVICE_BASE_URL` | `http://core-service` |
| `TLS_KEYSTORE_PATH` | `file:/certs/keystore.p12` |

| Variable | Referencia |
|---|---|
| `CORE_SERVICE_CREDENTIAL` | `xyz-bank/bff-mobile/CORE_SERVICE_CREDENTIAL` |
| `TLS_KEYSTORE_PASSWORD` | `xyz-bank/bff-mobile/TLS_KEYSTORE_PASSWORD` |

### xyz-bank-bff-atm

Contenedor `bff-atm`, imagen `xyz-bank-bff-atm`. Puerto **8083**.

Health check: `curl -sk --cert-type P12 --cert /certs/terminal-keystore.p12:"$TLS_KEYSTORE_PASSWORD" https://127.0.0.1:8083/actuator/health | grep -q UP`. Intervalo 10 s, timeout 5 s, 20 reintentos, start period 30 s. La contraseña del certificado es la variable `TLS_KEYSTORE_PASSWORD`, no un literal.

| Variable | Valor en el descriptor |
|---|---|
| `CONFIG_SERVER_URL` | `http://config-server:8888` |
| `EUREKA_CLIENT_ENABLED` | `true` |
| `EUREKA_SERVER_URL` | `http://eureka-server:8761` |
| `CORE_SERVICE_BASE_URL` | `http://core-service` |
| `CORE_SERVICE_PIN_VERIFICATION_BASE_URL` | `https://core-service:8453` |
| `TLS_KEYSTORE_PATH` | `file:/certs/keystore.p12` |
| `TLS_TRUSTSTORE_PATH` | `file:/certs/truststore.p12` |

| Variable | Referencia |
|---|---|
| `CORE_SERVICE_CREDENTIAL` | `xyz-bank/bff-atm/CORE_SERVICE_CREDENTIAL` |
| `TLS_KEYSTORE_PASSWORD` | `xyz-bank/bff-atm/TLS_KEYSTORE_PASSWORD` |
| `TLS_TRUSTSTORE_PASSWORD` | `xyz-bank/bff-atm/TLS_TRUSTSTORE_PASSWORD` |

### xyz-bank-gateway

Contenedor `gateway`, imagen `xyz-bank-gateway`. Puerto **8090**.

Health check: `wget -qO- http://127.0.0.1:8090/actuator/health | grep -q UP`. Intervalo 10 s, timeout 5 s, 20 reintentos, start period 30 s.

| Variable | Valor en el descriptor |
|---|---|
| `BFF_WEB_BASE_URL` | `https://bff-web:8081` |
| `BFF_MOBILE_BASE_URL` | `https://bff-mobile:8082` |
| `BFF_ATM_BASE_URL` | `https://bff-atm:8083` |

Sin `secrets`. En el puerto 8090 el gateway enruta `/web` hacia `bff-web`, `/mobile` hacia `bff-mobile` y `/atm` hacia `bff-atm`.
