FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/discovery-server-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]