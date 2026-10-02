# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY data-migration data-migration
COPY platform platform
COPY bff bff
COPY docs/contracts docs/contracts
RUN --mount=type=cache,target=/root/.m2 mvn -pl data-migration -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/data-migration/target/xyz-bank-data-migration-0.0.1-SNAPSHOT-exec.jar app.jar
COPY data-migration/data /app/data
ENV MIGRATION_DAILY_TRANSACTIONS=file:/app/data/legacy/movimientos_financieros_diarios.csv
ENV MIGRATION_MONTHLY_INTERESTS=file:/app/data/legacy/intereses_trimestrales.csv
ENV MIGRATION_ANNUAL_ACCOUNTS=file:/app/data/legacy/estados_financieros_anuales.csv
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
