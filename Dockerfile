# ============================================================
# Stage 1 — Build the JAR
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml first and download dependencies.
# Docker caches this layer — if your pom.xml hasn't changed,
# the next build skips this step entirely.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy source and build. Changing a .java file only
# invalidates this layer onward, not the dependency download.
COPY src ./src
RUN mvn package -DskipTests -B

# ============================================================
# Stage 2 — Run the JAR
# ============================================================
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built JAR from Stage 1
COPY --from=build /app/target/*.jar app.jar

# Render reads EXPOSE to know which port to route traffic to
EXPOSE 8080

# Start the app
ENTRYPOINT ["java", "-jar", "app.jar"]