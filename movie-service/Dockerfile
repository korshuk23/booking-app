# 1. Берем легкий образ Java
FROM eclipse-temurin:17-jdk-alpine
# 2. Рабочая директория
WORKDIR /app
# 3. Копируем скомпилированный JAR (путь может чуть отличаться, проверь build/libs)
COPY build/libs/movie-service-0.0.1-SNAPSHOT.jar app.jar
# 4. Команда запуска
ENTRYPOINT ["java", "-jar", "app.jar"]