# Build: docker build -t llmweather-mcp .   Run: docker run -p 8080:8080 llmweather-mcp
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/llmweather-mcp.jar .
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["java", "-jar", "/app/llmweather-mcp.jar", "--http", "8080"]
