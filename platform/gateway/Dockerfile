# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY data-migration data-migration
COPY platform platform
COPY bff bff
RUN --mount=type=cache,target=/root/.m2 mvn -pl platform/gateway -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
COPY --from=build /src/platform/gateway/target/gateway-0.0.1-SNAPSHOT-exec.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
