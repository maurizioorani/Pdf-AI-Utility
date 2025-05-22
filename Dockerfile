# Use an official OpenJDK runtime that is based on Ubuntu Jammy (as eclipse-temurin:17-jdk-jammy is)
# This allows us to use apt for installing Tesseract.
FROM eclipse-temurin:17-jdk-jammy

# Install Tesseract OCR and language packs
# Ensure that the package names are correct for the base image (jammy)
# tesseract-ocr-eng for English, tesseract-ocr-ita for Italian
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-ita \
    && rm -rf /var/lib/apt/lists/*

# Set TESSDATA_PREFIX to the standard location for Tesseract data files
# For Ubuntu/Debian, this is typically /usr/share/tesseract-ocr/4.00/tessdata or similar
# We can verify this path inside the container if needed, or let Tess4J find it.
# Often, Tess4J will find it automatically if installed system-wide.
# If not, it can be set: ENV TESSDATA_PREFIX /usr/share/tesseract-ocr/4.00/tessdata

# Set the working directory in the container
WORKDIR /app

# Copy the fat JAR file into the container at /app
# Make sure to build the project using `mvnw package` or `mvnw install` first
COPY target/pdfdemo-0.0.1-SNAPSHOT.jar app.jar

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Define environment variables that can be overridden at runtime
# Default profile if not set by docker-compose or run command
ENV SPRING_PROFILES_ACTIVE=postgres
ENV JAVA_OPTS=""
# The application property app.ocr.tessdata-path will be used by OcrConfig.java
# If Tesseract is installed system-wide in the image, this might not be strictly necessary
# if Tess4J can auto-detect it. However, explicitly setting it via application properties
# (which can be influenced by Docker Compose env vars) is safer.
# For now, we assume OcrConfig's default or an env var like APP_OCR_TESSDATA-PATH will handle it.
# If TESSDATA_PREFIX is set as an ENV var above, that might also work directly for Tess4J.

# Run the JAR file
# The exec form is used so that SIGTERM is received by the Spring Boot application, allowing graceful shutdown.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]