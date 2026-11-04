# ──────────────────────────────────────────────
# Build stage
# ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ──────────────────────────────────────────────
# Runtime stage
# ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

RUN groupadd -r scs && useradd -r -g scs scs

WORKDIR /app
COPY --from=build /build/target/supplychain-sentinel-backend-*.jar app.jar

USER scs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
