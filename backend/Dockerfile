FROM gradle:8.10.2-jdk21-alpine AS dev
WORKDIR /app
COPY . .
EXPOSE 8080
CMD ["gradle", "bootRun", "--no-daemon"]

FROM gradle:8.10.2-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS prod
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
