FROM eclipse-temurin:17-jre
COPY /target/chat-agent-0.1.0.jar /app/app.jar
WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
