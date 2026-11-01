# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /build
COPY apps/api/pom.xml ./pom.xml
COPY apps/api/src ./src
# Persistence/recovery tests run with Testcontainers in CI, not inside the image builder.
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-noble@sha256:161310cd5bd41fd549d19cf9f90b614897cb4a1532555c613b43b7e0628fe5d6
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/travel-api-0.1.0.jar ./app.jar
COPY deploy/certificates/rds-global-bundle.pem /app/certs/rds-global-bundle.pem
RUN echo "90a6f1a7978a1b1b096dca6c57511ca48d3b8cf052f8e704baf358f34ea2f21b  /app/certs/rds-global-bundle.pem" | sha256sum -c - && chown 10001:10001 /tmp && chmod 1777 /tmp
VOLUME ["/tmp"]
USER 10001:10001
ENV BIND_ADDRESS=0.0.0.0 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
