# ADR 002: Saga coreografiada para la acreditación de intereses

## Estado

Aceptado.

## Contexto

La acreditación anual de intereses era una llamada HTTP síncrona: `interests-service` calculaba el monto y `core-service` acreditaba saldo, transacción `CREDIT` y `AnnualInterestSummary` en una sola transacción de PostgreSQL (`POST /internal/accounts/{accountId}/interest-credits`, scope `interests:write`).

Ese comando acopla la disponibilidad de los dos procesos. El GET de resumen (`bff-web` → `interests-service` → `core-service`) no tiene ese problema y se queda síncrono. El estado de la cuenta sigue siendo la fila en PostgreSQL. Los eventos solo tienen que coordinar el crédito.

`interests-service` no tiene base de datos. `core-service` sí, y ya persiste el crédito de forma transaccional.

## Decisión

El crédito de intereses, cuando `FEATURE_INTEREST_CREDIT_VIA_KAFKA` está en `true`, es una saga coreografiada sobre un broker Apache Kafka en modo KRaft (un nodo, sin ZooKeeper).

No hay orquestador. Cada servicio reacciona al evento que le corresponde:

1. `interests-service` publica `InterestCalculated` en `interests.calculated` (clave de partición `accountId`) y guarda el cálculo en memoria como `PENDING`.
2. `core-service` consume ese evento, acredita con el mismo `CreditInterestUseCase` y, en la misma transacción, inserta el resultado en `outbox_events`.
3. Un relay de `core-service` publica `InterestCreditApplied` o `InterestCreditRejected` en `interests.credit-results` (misma clave) y marca la fila como publicada solo después del ack.
4. `interests-service` consume el resultado y cierra el cálculo como `APPLIED` o `REJECTED`. Un segundo resultado del mismo `eventId` no cambia un cálculo ya cerrado.

La entrega es at-least-once. El `eventId` determinista `interest:{accountId}:{year}` hace idempotente al consumidor. La clave HTTP del camino síncrono sigue siendo `interest-{accountId}-{year}`.

Con la flag en `false` (default, también en Compose) el POST HTTP actual no cambia: no escribe outbox ni arranca el listener ni el relay. El endpoint `POST /internal/accounts/{accountId}/interest-credits` se mantiene. Listener, outbox y relay de `core-service` solo se activan con `FEATURE_INTEREST_CREDIT_VIA_KAFKA=true`.

## Por qué outbox y no publicar dentro del caso de uso

El crédito y el evento de resultado tienen que confirmarse juntos. Si la transacción del crédito falla, no queda fila en `outbox_events` y el relay no anuncia un crédito que no ocurrió. Si el relay no puede hablar con Kafka, la fila sigue sin publicar y el siguiente ciclo reintenta. El crédito no se deshace.

Un rechazo de negocio (`VALIDATION`, `NOT_FOUND` o `CONFLICT`) no acredita y escribe solo `InterestCreditRejected` con `reason`. El offset se confirma para no bloquear la partición. Un fallo técnico no confirma el offset: Kafka reentrega.

## Por qué no Event Sourcing

Event Sourcing reconstruye el agregado releyendo su historia. Aquí la cuenta no se reconstruye desde eventos. El saldo, la transacción y el resumen anual siguen siendo filas de PostgreSQL, escritas por el caso de uso que ya existía. `InterestCalculated` y el resultado solo coordinan a dos servicios. Guardar esa historia como fuente de verdad duplicaría el estado de la cuenta y obligaría a un replay que el dominio no necesita.

## Por qué no hay outbox en interests-service

No hay transacción local donde encajar la publicación: el servicio no tiene datasource, JPA ni Flyway. El `eventId` determinista cubre una re-ejecución del cálculo. El estado `PENDING` / `APPLIED` / `REJECTED` vive en un repositorio en memoria del proceso.

Si el proceso muere entre publicar y consumir el resultado, ese estado se pierde. El crédito en `core-service` sigue siendo idempotente, así que un reintento no acredita dos veces. Persistirlo exigiría una base nueva. Este cambio no la agrega.

## Consecuencias

- El GET de resumen y la lectura de saldo siguen en HTTP. Con la flag encendida, el circuit breaker de `creditInterest` no interviene. El de `fetchInterestSummary` y `fetchAccountBalance` sí.
- El listener de `core-service` no pasa por `EnforcementFilter`. El POST HTTP sigue exigiendo `interests:write`.
- En desarrollo no hay ACLs de Kafka. Quien alcance el broker puede producir en `interests.calculated`. Pendiente de producción: solo `interests-service` produce en `interests.calculated` y solo `core-service` produce en `interests.credit-results` y `transactions.confirmed`.
- La durabilidad del cierre del cálculo en `interests-service` queda pendiente junto con esas ACLs. No se agrega base ni outbox en ese servicio en este cambio.

## Extensión: TransactionConfirmed (Fase 2)

Además de la saga de intereses, `core-service` publica un evento por cada movimiento de dinero confirmado en el tópico `transactions.confirmed` (clave de partición `accountId`).

Contrato del evento `TransactionConfirmed` (`schemaVersion` 1):

| Campo | Descripción |
|---|---|
| `eventId` | Id de la fila en `transactions` |
| `eventType` | `TransactionConfirmed` |
| `schemaVersion` | `1` |
| `accountId` | Cuenta afectada |
| `type` | `WITHDRAWAL` o `INTEREST_CREDIT` |
| `amount` | Monto del movimiento |
| `currency` | Moneda |
| `occurredAt` | Fecha del movimiento |

No incluye número de tarjeta, PIN, datos personales del cliente ni identificadores del terminal ATM.

El retiro ATM sigue siendo síncrono: `bff-atm` → `POST /internal/accounts/{id}/withdrawals` no cambia de contrato. El evento se escribe en el outbox dentro de la misma transacción del débito. Un reintento con el mismo `Idempotency-Key` no genera un segundo `TransactionConfirmed`. Un retiro o crédito rechazado no escribe el evento.

En un crédito de interés exitoso (camino Kafka), `InterestCreditApplied` y `TransactionConfirmed` se escriben en la misma transacción de PostgreSQL. El relay enruta por `event_type`: resultados de interés a `interests.credit-results`, confirmaciones a `transactions.confirmed`.

La publicación de `TransactionConfirmed` se controla con `FEATURE_TRANSACTION_CONFIRMED_EVENTS` / `app.events.transaction-confirmed.enabled`, independiente de `FEATURE_INTEREST_CREDIT_VIA_KAFKA`. No se implementan consumidores de negocio (reportes, anomalías) en esta fase.

## Extensión: security.alerts

Cuando la verificación de PIN bloquea una tarjeta que estaba desbloqueada, `core-service` inserta `CardBlocked` en el mismo outbox. El relay lo publica en el tópico `security.alerts`, con clave de mensaje `cardId`. El mensaje no incluye el PIN. Las respuestas 200, 401 y 423 de la verificación no cambian.

Contrato del evento `CardBlocked` (`schemaVersion` 1):

| Campo | Descripción |
|---|---|
| `eventId` | `{cardId}:blocked` |
| `eventType` | `CardBlocked` |
| `schemaVersion` | `1` |
| `cardId` | Tarjeta bloqueada |
| `occurredAt` | Fecha del bloqueo |

La publicación se controla con `FEATURE_SECURITY_ALERTS` / `app.events.security-alerts.enabled`, independiente de la saga de intereses y de `FEATURE_TRANSACTION_CONFIRMED_EVENTS`. El default de la aplicación es `false`. En Compose queda en `true`. Con la flag encendida, `core-service` también consume `security.alerts` (grupo `core-service-security-alerts`) y registra la alerta.
