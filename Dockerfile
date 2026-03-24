# Build stage
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
# Build skipping tests for faster deployment, tests should run in CI
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install Python and dependencies
RUN apk add --no-cache python3 py3-pip
# Install specific libraries for PDF analysis and visualization
RUN pip install --no-cache-dir python-docx plotly pandas numpy

# Create necessary directories
RUN mkdir -p /app/scripts /app/uploads/certificates /var/app/signed-documents /var/log/validation-system

# Copy Python scripts
COPY src/main/resources/scripts/fill_validation_template.py /app/scripts/
COPY generate_3d_animation.py /app/scripts/

# Copy the build artifact
COPY --from=build /app/target/validation-system.jar app.jar

# Create non-root user and set ownership
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && chown -R appuser:appgroup /app /var/app /var/log/validation-system

# Expose production port
EXPOSE 8443

# Environment configuration
ENV PYTHON_SCRIPT_PATH=/app/scripts
ENV PYTHON_EXECUTABLE=python3

# Switch to non-root user
USER appuser

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
