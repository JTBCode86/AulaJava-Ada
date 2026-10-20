# Estágio 1: Build (Baixa o Maven e compila o projeto dentro do Docker)
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
COPY src src

# Compila usando o Maven oficial da imagem
RUN mvn clean package -DskipTests

# Estágio 2: Runtime Distroless (JRE 21)
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

COPY --from=builder /build/target/*.jar /app/app.jar

USER nonroot
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]