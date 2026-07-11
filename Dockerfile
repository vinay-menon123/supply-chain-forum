# All-in-one image: Spring Boot API + built frontend served from ./public.
# Used by Railway (and any single-container host). Local docker-compose and
# the k8s manifests use backend/Dockerfile + frontend/Dockerfile instead.

# ---- Frontend build ----
FROM node:22-alpine AS frontend
WORKDIR /fe
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# ---- Backend build ----
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
RUN mvn -q -B -DskipTests package

# ---- Runtime ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /app/target/forum-backend-1.0.0.jar app.jar
COPY --from=frontend /fe/dist ./public

EXPOSE 4000
CMD ["java", "-jar", "app.jar"]
