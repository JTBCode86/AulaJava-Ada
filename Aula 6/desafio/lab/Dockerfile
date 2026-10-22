# Estágio 1: Build da aplicação com Maven e JDK instalados no container
FROM eclipse-temurin:17-jdk-jammy AS build

# Instala o Maven no container Ubuntu/Debian base
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Executa o build utilizando o maven global instalado
RUN mvn clean package -DskipTests

# Estágio 2: Imagem final segura e enxuta (Distroless)
FROM gcr.io/distroless/java17-debian12:nonroot
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER nonroot:nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]