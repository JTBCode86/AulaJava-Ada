# Estágio 1: Build (Maven + JDK 21)
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN ./mvnw package -DskipTests

# Estágio 2: Runtime Distroless (JRE 21)
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

COPY --from=builder /build/target/*.jar /app/app.jar

USER nonroot
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]