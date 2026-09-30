# Build from the repository root:
#   gcloud run deploy witspath-routing --source . --dockerfile routing-service/Dockerfile ...
# or: docker build -f routing-service/Dockerfile -t witspath-routing .
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY routing-core ./routing-core
COPY routing-service ./routing-service
# The root build applies the Android plugins and the settings list :app; the service needs neither
RUN sed -i '/include(":app")/d' settings.gradle.kts \
 && : > build.gradle.kts \
 && chmod +x gradlew \
 && ./gradlew :routing-service:installDist --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/routing-service/build/install/routing-service/ ./
ENV PORT=8080
EXPOSE 8080
USER 1000
CMD ["bin/routing-service"]
