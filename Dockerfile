# --- Frontend stage ---
# Built here rather than by Maven, because the Node that Maven downloads does not run on Alpine
FROM node:24-alpine AS frontend
WORKDIR /frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend ./
RUN npm run build

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Download dependencies first so they are cached between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Maven copies frontend/dist into the jar; the Node stage already built it
COPY --from=frontend /frontend/dist ./frontend/dist
RUN mvn -B -q package -DskipTests -Dskip.frontend=true

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
