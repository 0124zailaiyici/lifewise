# LifeWise Backend - Dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built JAR
COPY backend/target/life-wise-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
