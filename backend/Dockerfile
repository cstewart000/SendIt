FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN mkdir -p /data/storage
ENV STORAGE_PATH=/data/storage
COPY --from=build /app/build/libs/*.jar /app/app.jar
EXPOSE 8080
CMD ["sh", "-c", "export SPRING_DATASOURCE_URL=$(echo \"${DATABASE_URL}\" | sed -e 's|^postgres://|jdbc:postgresql://|' -e 's|^postgresql://|jdbc:postgresql://|'); exec java -XX:+UseContainerSupport -Xmx512m -jar /app/app.jar"]
