FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S pulse && adduser -S pulse -G pulse
COPY --from=build /workspace/target/pulse-backend-*.jar app.jar
USER pulse
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
