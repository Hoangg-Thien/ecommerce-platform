# ==========================================
# Stage 1: Build JAR application with Maven
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 1. Tận dụng Docker layer caching: Copy backend/pom.xml trước để download dependencies
COPY backend/pom.xml .

# Download dependencies offline (layer này sẽ được cache nếu pom.xml không đổi)
RUN mvn dependency:go-offline -B

# 2. Copy source code từ backend/src và tiến hành build package
COPY backend/src ./src

# Build JAR file (skip tests vì đã test trên CI pipeline)
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Minimal Production JRE Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Tạo user không có quyền root (non-root user) để đảm bảo an toàn bảo mật
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy file jar từ builder stage
COPY --from=builder /build/target/*.jar app.jar

# Gán quyền sở hữu file jar cho appuser
RUN chown -R appuser:appgroup /app

# Chuyển sang non-root user
USER appuser

# Expose port mặc định của Spring Boot
EXPOSE 8080

# Thiết lập JVM flags tối ưu bộ nhớ Container Cloud (Supabase / Railway / Render / Fly.io)
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod

# Khởi chạy ứng dụng
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
