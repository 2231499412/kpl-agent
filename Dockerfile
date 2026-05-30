FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/kpl-data-agent-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
