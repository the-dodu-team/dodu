FROM node:22-alpine AS frontend-build
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY frontend/package.json frontend/package.json
# The lockfile is generated on Windows; include Linux optional native packages
# so Vite/Rolldown can build inside the Linux image as well.
RUN npm ci --include=optional
# The lockfile is shared with Windows development, so install the Linux
# native bindings explicitly for this Alpine build image in one transaction.
RUN npm install --no-save --workspace frontend \
    @rolldown/binding-linux-x64-musl@1.2.9 \
    lightningcss-linux-x64-musl@1.33.0
COPY frontend frontend
RUN npm run build --workspace frontend

FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /workspace
COPY backend backend
COPY --from=frontend-build /workspace/frontend/dist frontend/dist
RUN chmod +x backend/mvnw && cd backend && ./mvnw -B -ntp -Pbundle-frontend -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-build /workspace/backend/target/dodu-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
CMD ["sh", "-c", "exec java -jar /app/app.jar --server.address=0.0.0.0 --server.port=${PORT:-8080}"]
