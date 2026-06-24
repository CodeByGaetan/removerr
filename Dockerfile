# Stage 1: build Angular
FROM node:22-alpine AS frontend-build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
# Bake the release version (passed via --build-arg VERSION) into the bundle.
ARG VERSION=0.0.0-local
RUN printf "export const environment = {\n  version: '%s',\n};\n" "$VERSION" \
    > src/environments/environment.ts
RUN npm run build -- --configuration=production

# Stage 2: build Spring Boot (with the frontend bundled into static/)
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -q
COPY backend/src ./src
COPY --from=frontend-build /app/dist/frontend/browser ./src/main/resources/static
RUN mvn package -DskipTests -q

# Stage 3: final image
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache tzdata
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
VOLUME ["/config"]
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
