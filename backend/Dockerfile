FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN mkdir -p /data/storage
ENV STORAGE_PATH=/data/storage
COPY --from=build /app/build/libs/*.jar /app/app.jar
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
EXPOSE 8080
CMD ["/app/start.sh"]
