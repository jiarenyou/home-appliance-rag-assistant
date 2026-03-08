FROM openjdk:21-jdk-slim

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src ./src
COPY gradlew ./

RUN apt-get update && apt-get install -y gradle && \
    chmod +x gradlew && \
    ./gradlew build --no-daemon

FROM openjdk:21-jre-slim

WORKDIR /app

COPY --from=0 /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
