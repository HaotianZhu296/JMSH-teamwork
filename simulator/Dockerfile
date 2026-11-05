FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/mhd-simulator-0.1.0-SNAPSHOT.jar app.jar

VOLUME ["/app/data"]

COPY --from=build /app/src/main/resources/simulator-docker.properties simulator.properties

CMD ["java", "-jar", "app.jar", "simulator.properties"]
