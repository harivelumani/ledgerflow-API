FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 ledger
WORKDIR /app
COPY --from=build /src/target/ledgerflow.jar /app/ledgerflow.jar
USER ledger
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/ledgerflow.jar"]
