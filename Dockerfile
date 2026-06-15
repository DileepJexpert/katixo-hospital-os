# Multi-stage build for optimized Docker image

# Build stage
FROM maven:3.9-eclipse-temurin-21 as builder

WORKDIR /build

# Copy build files (monolith: parent pom + the single service module)
COPY pom.xml .
COPY katixo-hospital-service ./katixo-hospital-service

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy built JAR from builder
COPY --from=builder /build/katixo-hospital-service/target/*.jar app.jar

# Create non-root user
RUN addgroup -g 1000 appuser && \
    adduser -u 1000 -G appuser -s /bin/sh -D appuser

RUN chown -R appuser:appuser /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Expose port
EXPOSE 8081

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
