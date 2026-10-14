FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/gateway-service-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]