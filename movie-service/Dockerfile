# 1. Берем легкий образ Java
FROM eclipse-temurin:21-jre
# 2. Рабочая директория
WORKDIR /app
# 3. Копируем скомпилированный JAR (путь может чуть отличаться, проверь build/libs)
COPY build/libs/movie-service-0.0.1-SNAPSHOT.jar app.jar
# 4. Команда запуска
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]