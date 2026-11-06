# The pipeline builds and tests the jar with Maven first, so this image contains
# exactly the artifact that passed the tests.
FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home app

WORKDIR /app
COPY --chown=app:app target/devops-app.jar app.jar

USER 10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
