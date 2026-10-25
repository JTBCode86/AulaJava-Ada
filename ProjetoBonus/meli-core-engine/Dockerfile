# Estágio de Build utilizando Maven com Java 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copia apenas o pom.xml
COPY pom.xml ./

# Copia os fontes e compila a aplicação (o Maven descarrega as dependências automaticamente aqui)
COPY src ./src
RUN mvn clean package -DskipTests

# Estágio de Execução (imagem final leve)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]