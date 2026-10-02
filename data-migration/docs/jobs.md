# Jobs de migración

Cada job sigue el patrón Spring Batch chunk-oriented, con un **guard** previo que consulta el ledger `migration_executions` para no reescribir datos ya migrados con éxito.

## Patrón común

```mermaid
flowchart TB
    JobLauncher --> Job
    subgraph Job [Job]
        guardStep[Step checkMigrationNotDone]
        exitCheck{ExitStatus}
        processStep[Process Step multithread]
        guardStep --> exitCheck
        exitCheck -->|ALREADY_MIGRATED| endNode[end sin escribir]
        exitCheck -->|COMPLETED| processStep
    end
    processStep --> JdbcWriter[Jdbc Port Adapter]
    JdbcWriter --> BusinessTable[(tabla MySQL)]
    Job --> JobRepository[(BATCH_*)]
    guardStep --> Ledger[(migration_executions)]
    processStep --> LedgerMark[afterJob marca SUCCESS o FAILED]
```

## Escalado y resiliencia

```mermaid
flowchart LR
    yml[chunk-size 5 throttle-limit 3] --> step[Process Step]
    syncReader[SynchronizedItemStreamReader] --> pool[TaskExecutor 3 hilos]
    pool --> processor[Processor thread-safe]
    processor --> writer[ItemWriter]
    skipPol[DomainSkipPolicy] --> step
    retryPol[TransientRetryPolicy] --> step
    backOff[ExponentialBackOffPolicy] --> step
    metrics[StepMetricsListener JobSummaryListener] --> step
```

Escalado por **multithreading** (`TaskExecutorRepeatTemplate` + pool acotado). No se usa particionado (`PartitionHandler`): un CSV por job y, en anual, agregación cross-chunk hacen que MT sea el fit correcto.

Parámetros en `application.yml`:

| Propiedad | Default | Uso |
|---|---|---|
| `migration.batch.chunk-size` | 5 | Tamaño de chunk |
| `migration.batch.throttle-limit` | 3 | `corePoolSize` / `maxPoolSize` / `queueCapacity` del `TaskExecutor` |
| `migration.batch.skip-limit` | 2000 | Máximo de skips de dominio/parse |
| `migration.batch.retry-limit` | 3 | Reintentos de errores transitorios JDBC |

BackOff fijo en código: `ExponentialBackOffPolicy` (initial 1000 ms, multiplier 2.0, max 10000 ms) entre reintentos JDBC.

## CSV por defecto

`application.yml` apunta a los archivos oficiales:

| Job | CSV por defecto |
|---|---|
| `dailyTransactionsJob` | `data/legacy/movimientos_financieros_diarios.csv` |
| `monthlyInterestsJob` | `data/legacy/intereses_trimestrales.csv` |
| `annualGenerationJob` | `data/legacy/estados_financieros_anuales.csv` |

Los tests existentes conservan otras rutas. `TheDailyTransactionsJobTest`, `TheMonthlyInterestsJobTest`, `TheAnnualGenerationJobTest` y `src/test/resources/application.yml` leen `data/semana_2/transacciones.csv`, `data/semana_2/intereses.csv` y `data/semana_2/cuentas_anuales.csv`. `TheRunAllMigrationsRunnerTest` lee `data/semana_3/transacciones.csv`, `data/semana_3/intereses.csv` y `data/semana_3/cuentas_anuales.csv`.

## dailyTransactionsJob

| Elemento | Valor |
|---|---|
| CSV | `data/legacy/movimientos_financieros_diarios.csv` (por defecto) |
| Guard | `checkDailyMigrationNotDone` |
| Process | `processDailyTransactions` |
| Puerto | `DailyReportWriter` → `JdbcDailyReportWriter` |
| Tabla | `daily_transaction_reports` |

### Catálogo de tipos de transacción (regla de negocio)

Solo se aceptan estos valores de `tipo` (trim, minúsculas y **sin tildes**: p. ej. `débito` ≡ `debito`, `crédito` ≡ `credito`):

| Valor en CSV | Dominio |
|---|---|
| `debito` / `débito` | `DEBIT` |
| `credito` / `crédito` | `CREDIT` |

Cualquier otro valor (p. ej. `invalid`, `desconocido`) es **tipo desconocido** → `DomainError` → skip. No se reinterpretan sentinels ni placeholders.

Otras omisiones: `monto` vacío o ≤ 0, `fecha` inválida (p. ej. mes 13), `id` vacío, duplicados por business key (`fecha|monto|tipo`) en el mismo run.

La anomalía `HIGH_AMOUNT` (monto > 2000) se registra en el reporte y el ítem **sí se escribe**. `AnomalyDetector` usa set concurrente para multithreading.

## monthlyInterestsJob

| Elemento | Valor |
|---|---|
| CSV | `data/legacy/intereses_trimestrales.csv` |
| Guard | `checkMonthlyMigrationNotDone` |
| Process | `calculateMonthlyInterests` |
| Puerto | `AccountBalanceWriter` → `JdbcAccountBalanceWriter` |
| Tabla | `account_balances` |

### Catálogo de tipos de cuenta (regla de negocio)

Solo se calculan intereses para estos valores de `tipo` (trim, minúsculas y **sin tildes**: p. ej. `préstamo` ≡ `prestamo`):

| Valor en CSV | Dominio | Tasa |
|---|---|---|
| `ahorro` | `SAVINGS` | 1.00% si edad menor a 65; 1.50% si edad 65 o más |
| `prestamo` / `préstamo` | `LOAN` | 1.50% |
| `hipoteca` | `MORTGAGE` | 0.80% |

Cualquier otro valor (p. ej. `-1`, `unknown`) es **tipo desconocido** → `DomainError` → skip. No se reinterpretan sentinels ni placeholders como productos con tasa.

Otras omisiones: `saldo` vacío o ≤ 0, `edad` vacía o fuera de 18–100, `nombre` vacío, `cuenta_id` vacío o duplicado en el mismo run.

## annualGenerationJob

| Elemento | Valor |
|---|---|
| CSV | `data/legacy/estados_financieros_anuales.csv` |
| Guard | `checkAnnualMigrationNotDone` |
| Process | `compileAnnualAudit` |
| Puerto | `AnnualAuditWriter` → `JdbcAnnualAuditWriter` |
| Tabla | `annual_audit_reports` |

El writer Batch acumula movimientos de **todos** los chunks (buffer sincronizado) y consolida por `cuenta_id` vía `AnnualAccountCompiler` al cerrar el step. El processor deduplica con `ConcurrentHashMap.newKeySet()` (thread-safe bajo MT).

### Catálogo de tipos de movimiento (regla de negocio)

Solo se consolidan estos valores de `transaccion` (trim, minúsculas y **sin tildes**: p. ej. `depósito` ≡ `deposito`):

| Valor en CSV | Dominio | Notas |
|---|---|---|
| `deposito` / `depósito` | `DEPOSIT` | Monto `0` → skip |
| `retiro` | `WITHDRAWAL` | Montos negativos o positivos permitidos |
| `compra` | `PURCHASE` | Montos negativos o positivos permitidos |

Cualquier otro valor (p. ej. `pago`, `transfer`, vacío) es **tipo desconocido** → `DomainError` → skip. No hay mapeo de `pago` a retiro/compra: el catálogo del reporte anual es cerrado a esos tres tipos.

Otras omisiones: monto vacío/inválido, fecha inválida, `cuenta_id` vacío, duplicados por business key.

## Skip / retry / listeners

- **SkipPolicy** `DomainSkipPolicy`: `DomainError` y `FlatFileParseException` hasta `skip-limit`
- **RetryPolicy** `TransientDataAccessRetryPolicy`: solo `TransientDataAccessException`
- **BackOffPolicy** `ExponentialBackOffPolicy`: 1s → ×2 → tope 10s entre retries
- **SkipListener**: log WARN con `thread=`, fase, tipo de excepción e item
- **RetryListener** `LoggingRetryListener`: log INFO con `attempt`, `thread`, tipo de excepción y motivo en cada reintento JDBC transitorio
- **StepMetricsListener**: duración y throughput por step
- **JobSummaryListener**: duración del job + `chunkSize` / `throttleLimit` al inicio
- **ChunkThroughputListener**: DEBUG por chunk (nombre de thread)

Processors stateful usan `processorNonTransactional()` y beans `@StepScope`.

## Comparación de parámetros (configuración óptima local)

Para cumplir el requisito de comparar configs de escalado:

```bash
# 1. Generar CSV grande
python3 scripts/generate-performance-data.py

# 2. Revertir ledger/tablas entre corridas
docker compose exec -T mysql mysql -umigration -pmigration xyz_bank_migration < scripts/revert-migration.sql

# 3a. Baseline single-thread
./mvnw spring-boot:run -Dspring-boot.run.profiles=performance \
  -Dspring-boot.run.arguments="--spring.batch.job.enabled=true --spring.batch.job.name=dailyTransactionsJob --migration.batch.throttle-limit=1"

# 3b. Multithread (default óptimo local)
docker compose exec -T mysql mysql -umigration -pmigration xyz_bank_migration < scripts/revert-migration.sql
./mvnw spring-boot:run -Dspring-boot.run.profiles=performance \
  -Dspring-boot.run.arguments="--spring.batch.job.enabled=true --spring.batch.job.name=dailyTransactionsJob --migration.batch.throttle-limit=3"
```

En los logs registrá:

| Corrida | `throttle-limit` | `chunk-size` | `throughputPerSec` | duración step | Notas |
|---|---|---|---|---|---|
| A | 1 | 5 | _(completar)_ | _(completar)_ | Baseline |
| B | 3 | 5 | _(completar)_ | _(completar)_ | Default |
| C (opcional) | 3 | 50 | _(completar)_ | _(completar)_ | Chunks más grandes |

**Configuración óptima local elegida:** `throttle-limit=3`, `chunk-size=5` — paralelismo observable sin saturar MySQL local ni el `JobRepository`; chunks chicos dan commits frecuentes y métricas claras en demos.

Buscá en logs: `Starting job=... chunkSize=... throttleLimit=...` y `Step metrics ... throughputPerSec`.

## Ledger anti-duplicados

Tabla `migration_executions` (`job_name`, `status`, `executed_at`, `write_count`, `skip_count`).

1. Si existe fila `SUCCESS` para el job → exit `ALREADY_MIGRATED` y el job termina sin procesar.
2. Tras un process `COMPLETED` → `markSuccess`.
3. Tras `FAILED` → `markFailed`.

Los jobs usan `RunIdIncrementer`: cada `spring-boot:run` crea una nueva instancia Batch y siempre pasa por el guard.

Para volver a migrar: ejecutar [`scripts/revert-migration.sql`](../scripts/revert-migration.sql). Ver [mysql.md](mysql.md).

## Cómo encaja la arquitectura

El sistema está organizado como un **monolito modular hexagonal**: cada job es un bounded context (`dailytransactions`, `monthlyinterests`, `annualreports`) con dominio propio, puertos de escritura y adapters JDBC. Lo transversal (pool, skip/retry/backoff, métricas, ledger) vive en `shared`.

Flujo de un job de punta a punta:

1. **Entrada** — CSV (`FlatFileItemReader` envuelto en `SynchronizedItemStreamReader` para MT).
2. **Validación y transformación** — `ItemProcessor` + invariantes de dominio (`DomainError` → skip).
3. **Salida** — `ItemWriter` Batch → puerto de aplicación → adapter JDBC → tabla MySQL.
4. **Orquestación** — guard consulta `migration_executions`; el process step corre en paralelo con el pool compartido; al terminar, el ledger marca `SUCCESS` o `FAILED`.

Capas de resiliencia en el process step:

| Capa | Responsabilidad |
|---|---|
| Dominio / processor | Rechaza datos inválidos o duplicados de negocio |
| `DomainSkipPolicy` | Omite corruptos/parse errors sin abortar el lote |
| `TransientDataAccessRetryPolicy` + backoff | Reintenta fallos JDBC transitorios con espera exponencial |
| Listeners | Observabilidad (skips por hilo, throughput, resumen de job) |

El escalado es **multithreading sobre un step chunk-oriented**, no particionado: un archivo por job y, en anual, agregación cross-chunk hacen que un pool acotado (`throttle-limit`) sea suficiente. Los parámetros se ajustan midiendo `throughputPerSec` (ver sección de comparación arriba).
