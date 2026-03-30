FROM eclipse-temurin:21-jdk-alpine AS dev
WORKDIR /workspace
RUN apk add --no-cache curl
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
EXPOSE 8080
CMD ["./gradlew", "--no-daemon", "bootRun"]

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.se,java.net.http,jdk.crypto.ec,jdk.naming.dns,jdk.unsupported \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=2 \
    --output /jre

FROM alpine:3.20
WORKDIR /app
RUN apk add --no-cache curl && addgroup -S athanor && adduser -S -G athanor athanor
COPY --from=build /jre /opt/java
COPY --from=build /workspace/build/libs/*.jar app.jar
USER athanor
ENTRYPOINT ["/opt/java/bin/java", "-jar", "/app/app.jar"]
