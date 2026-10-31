FROM maven:3.9.9-eclipse-temurin-21-alpine
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B
EXPOSE 8085
ENTRYPOINT ["sh", "-c", "mvn package -DskipTests -B && exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar target/alcontable-api-1.0.0.jar"]

