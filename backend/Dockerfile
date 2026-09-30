# Stage 1: Build - dùng Maven + JDK đầy đủ, không nằm trong image chạy cuối cùng
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache dependency layer riêng - chỉ tải lại khi pom.xml đổi, không phải mỗi lần sửa code
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: Runtime - chỉ JRE Alpine, không có Maven/JDK/source (đo thật ~410MB,
# chủ yếu do base image eclipse-temurin:21-jre-alpine đã lớn hơn các bản cũ)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
