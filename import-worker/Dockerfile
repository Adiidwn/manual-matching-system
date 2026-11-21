FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY api-service/pom.xml api-service/pom.xml
COPY import-worker/pom.xml import-worker/pom.xml
COPY common/src common/src
COPY import-worker/src import-worker/src
RUN mvn -q -pl import-worker -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/import-worker/target/reconciliation-worker-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
