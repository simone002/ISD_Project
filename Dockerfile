FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw
RUN ./mvnw -q dependency:go-offline

COPY src src
COPY data.csv data.csv
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/ISDProjectHelios-0.0.1-SNAPSHOT.jar app.jar
COPY data.csv data.csv

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]