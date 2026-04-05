# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:24-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom first (layer-cache friendly)
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -q

# Copy source and build the fat JAR, skipping tests
COPY src ./src
RUN ./mvnw clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=builder /build/target/*.jar app.jar

# Log directory (matches application.yml: logs/abuhint.log)
RUN mkdir -p logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
