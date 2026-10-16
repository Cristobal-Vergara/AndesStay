FROM maven:3.9.15-eclipse-temurin-17 AS build
WORKDIR /app
COPY ["AndesStay Backend/pom.xml", "./pom.xml"]
RUN mvn -B -ntp dependency:go-offline
COPY ["AndesStay Backend/src", "./src"]
COPY config/rabbit-topology.properties /config/rabbit-topology.properties
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --uid 10001 --create-home appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
