FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
ARG MAVEN_PROFILES=railway-aot
RUN mvn package -DskipTests -B -P${MAVEN_PROFILES}

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ENV PORT=8081
ENV SOCKETIO_PORT=8089
EXPOSE 8081
EXPOSE 8089
ENTRYPOINT ["sh", "-c", "java -Dspring.aot.enabled=true -Dserver.port=${PORT} -Dsocketio.port=${SOCKETIO_PORT} -jar app.jar"]
