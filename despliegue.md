# Despliegue en AWS

El descriptor está en [`deploy/aws/ecs-task-definition.json`](deploy/aws/ecs-task-definition.json). Cubre los servicios de `docker-compose.yml`. Cada elemento de `taskDefinitions` es una familia ECS con `networkMode` `awsvpc` y `requiresCompatibilities` `FARGATE`.

`imageBuild` indica cómo construir la imagen en este repositorio; no es un campo de AWS y se quita al registrar. Al registrar la familia se usan `family` y `containerDefinitions` (imagen, puerto, health check, `environment` y `secrets`). El valor de cada secreto queda fuera del descriptor: aquí solo figura la referencia `valueFrom`.

## Herramientas y recursos necesarios

- AWS CLI v2 configurada (`aws configure`) con una cuenta y región, por ejemplo `us-east-1`.
- Docker para construir las imágenes y `jq` para extraer cada familia del descriptor.
- En AWS:
  - **ECR**: un repositorio por imagen propia.
  - **Secrets Manager**: los secretos que nombra cada `valueFrom`.
  - **IAM**: un rol de ejecución de tareas (`ecsTaskExecutionRole`) con `AmazonECSTaskExecutionRolePolicy` y permiso `secretsmanager:GetSecretValue`.
  - **VPC**: subredes privadas para los servicios internos y públicas para el balanceador; un security group que permita el tráfico entre servicios.
  - **ECS**: un clúster Fargate.
  - **Cloud Map**: un namespace privado `xyz-bank.local` para que los servicios se encuentren por nombre (`mysql`, `postgres`, `kafka`, `config-server`, `eureka-server`, `core-service`, etc.), igual que en Compose.
  - **ALB**: un Application Load Balancer público delante del `gateway` (puerto 8090).
  - **EFS** (o imágenes propias): los certificados de `dev/certs` y el `config-repo/` que en Compose se montan como volúmenes.

En producción, MySQL y PostgreSQL se recomiendan en **Amazon RDS** y Kafka en **Amazon MSK**, en lugar de contenedores Fargate sin almacenamiento persistente.

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

## Publicar las imágenes en ECR

```bash
AWS_REGION=us-east-1
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY=$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $REGISTRY

for IMAGE in xyz-bank-data-migration xyz-bank-config-server xyz-bank-eureka-server \
             xyz-bank-authorization-server xyz-bank-core-service xyz-bank-interests-service \
             xyz-bank-bff-web xyz-bank-bff-mobile xyz-bank-bff-atm xyz-bank-gateway; do
  aws ecr create-repository --repository-name $IMAGE --region $AWS_REGION 2>/dev/null || true
  docker tag $IMAGE:latest $REGISTRY/$IMAGE:latest
  docker push $REGISTRY/$IMAGE:latest
done
```

## Crear los secretos

Un secreto por cada `valueFrom` del descriptor. Ejemplo:

```bash
aws secretsmanager create-secret --name xyz-bank/postgres/POSTGRES_PASSWORD --secret-string '<contraseña>'
aws secretsmanager create-secret --name xyz-bank/core-service/DB_PASSWORD --secret-string '<contraseña>'
```

Al registrar, cada `valueFrom` se reemplaza por el ARN completo del secreto (`arn:aws:secretsmanager:<región>:<cuenta>:secret:xyz-bank/...`).

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

Con AWS CLI, cada familia se extrae del descriptor con `jq` y se registra. Fargate exige además `cpu`, `memory` y `executionRoleArn`, que se agregan en el mismo paso:

```bash
EXECUTION_ROLE_ARN=arn:aws:iam::$ACCOUNT_ID:role/ecsTaskExecutionRole

for FAMILY in $(jq -r '.taskDefinitions[].family' deploy/aws/ecs-task-definition.json); do
  jq --arg f "$FAMILY" --arg role "$EXECUTION_ROLE_ARN" --arg reg "$REGISTRY" '
    .taskDefinitions[] | select(.family == $f) | del(.imageBuild)
    | .cpu = "512" | .memory = "1024" | .executionRoleArn = $role
    | .containerDefinitions |= map(if (.image | startswith("xyz-bank-")) then .image = ($reg + "/" + .image + ":latest") else . end)
  ' deploy/aws/ecs-task-definition.json > /tmp/$FAMILY.json
  aws ecs register-task-definition --cli-input-json file:///tmp/$FAMILY.json
done
```

Antes de ejecutarlo, reemplaza cada `valueFrom` por el ARN de su secreto (ver «Crear los secretos»).

## Crear el clúster y los servicios

```bash
aws ecs create-cluster --cluster-name xyz-bank
aws servicediscovery create-private-dns-namespace --name xyz-bank.local --vpc <vpc-id>
```

Por cada familia de larga duración se crea un servicio ECS registrado en Cloud Map con el nombre que usan las variables (`mysql`, `postgres`, `kafka`, `config-server`, `eureka-server`, `authorization-server`, `core-service`, `interests-service`, `bff-web`, `bff-mobile`, `bff-atm`, `gateway`). Ejemplo para `core-service`:

```bash
aws ecs create-service --cluster xyz-bank --service-name core-service \
  --task-definition xyz-bank-core-service --desired-count 2 --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[<subnet-privada>],securityGroups=[<sg>],assignPublicIp=DISABLED}" \
  --service-registries "registryArn=<arn-del-servicio-cloud-map-core-service>"
```

`data-migration` y `kafka-init` no son servicios: se ejecutan una vez con `aws ecs run-task` después de que MySQL y Kafka estén sanos. El orden de arranque es el mismo que en Compose: bases de datos y Kafka → `data-migration` y `kafka-init` → `config-server` → `eureka-server` y `authorization-server` → `core-service` → `interests-service` → BFF → `gateway`.

El `gateway` se crea con `--load-balancers "targetGroupArn=<tg-arn>,containerName=gateway,containerPort=8090"` para quedar detrás del ALB público. El resto de los servicios queda en subredes privadas.

## Escalado horizontal

Los BFF, `interests-service` y `core-service` no guardan estado en memoria entre peticiones (el estado vive en PostgreSQL y en los tokens), así que se pueden ejecutar varias tareas de cada uno:

- `--desired-count` fija cuántas tareas corren de cada servicio.
- Los BFF y `interests-service` llaman a `core-service` por nombre de servicio con Spring Cloud LoadBalancer (`@LoadBalanced`) y Eureka, que reparte las llamadas entre las instancias registradas.
- El ALB reparte el tráfico entre las tareas del `gateway`.
- El consumo de Kafka escala hasta el número de particiones de cada tópico: para más de un consumidor por grupo hay que crear los tópicos con más particiones (en `kafka-init` hoy es 1).

Auto scaling por CPU, ejemplo para `bff-web`:

```bash
aws application-autoscaling register-scalable-target --service-namespace ecs \
  --resource-id service/xyz-bank/bff-web --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 --max-capacity 6

aws application-autoscaling put-scaling-policy --service-namespace ecs \
  --resource-id service/xyz-bank/bff-web --scalable-dimension ecs:service:DesiredCount \
  --policy-name bff-web-cpu --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{"TargetValue":60.0,"PredefinedMetricSpecification":{"PredefinedMetricType":"ECSServiceAverageCPUUtilization"}}'
```

En Docker Compose local no se escala con `--scale` porque cada servicio tiene `container_name` y puerto de host fijos; el escalado horizontal se aplica en ECS.

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
| `TLS_TRUSTED_CA_PATH` | `file:/certs/ca.crt` |

Sin `secrets`. En el puerto 8090 el gateway enruta `/web` hacia `bff-web`, `/mobile` hacia `bff-mobile` y `/atm` hacia `bff-atm`. `TLS_TRUSTED_CA_PATH` apunta al certificado de la CA que firma los certificados de los BFF; el gateway lo usa para confiar en ellos al reenviar por HTTPS.
