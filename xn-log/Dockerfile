# syntax=docker/dockerfile:1
# 在仓库根目录构建: docker build -f xn-log/Dockerfile .
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY spotbugs-exclude.xml .
COPY xn-gateway/pom.xml xn-gateway/
COPY xn-system/pom.xml xn-system/
COPY xn-file/pom.xml xn-file/
COPY xn-log/pom.xml xn-log/
COPY xn-job/pom.xml xn-job/
COPY xn-log/src xn-log/src
RUN chmod +x mvnw && ./mvnw -q -pl xn-log -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/xn-log/target/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
