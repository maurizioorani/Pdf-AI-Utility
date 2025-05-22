# 📝 PDF PowerTool Suite 🚀

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue.svg?style=for-the-badge&logo=java)](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
[![Spring Boot Version](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg?style=for-the-badge&logo=spring)](https://spring.io/projects/spring-boot)
[![Docker Support](https://img.shields.io/badge/Docker-Fully%20Containerized-blue.svg?style=for-the-badge&logo=docker)](tool/pdfdemo/Readme.md#%EF%B8%8F-running-with-docker-compose-recommended)
[![License](https://img.shields.io/badge/License-Personal%20Use-lightgrey.svg?style=for-the-badge)](tool/pdfdemo/Readme.md#%EF%B8%8F-license)

Welcome to the **PDF PowerTool Suite**! This Spring Boot application is your comprehensive solution for a wide array of PDF manipulations, including dynamic generation, advanced OCR with AI-driven enhancement, and robust document management features like merging, optimization, splitting, and protection. Now fully containerized with Docker Compose for easy deployment!

---

## 📖 Table of Contents

- [✨ Core Capabilities](#-core-capabilities)
- [🚦 Getting Started](#-getting-started)
  - [⚙️ Prerequisites](#️-prerequisites)
  - [🐳 Running with Docker Compose (Recommended)](#-running-with-docker-compose-recommended)
    - [Option 1: Full Stack (App + Database + Ollama)](#option-1-full-stack-app--database--ollama)
    - [Option 2: Database Only (for Local App Development)](#option-2-database-only-for-local-app-development)
    - [Managing Ollama Models in Docker (for Full Stack)](#managing-ollama-models-in-docker-for-full-stack)
    - [Important Note on Docker Compose File Names](#important-note-on-docker-compose-file-names)
  - [🚀 Installation & Setup (Local Machine - Alternative)](#-installation--setup-local-machine---alternative)
- [🌟 Features Showcase](#-features-showcase)
- [🌐 Application Endpoints](#-application-endpoints)
  - [🖥️ User Interface Endpoints](#️-user-interface-endpoints)
  - [⚙️ API/Processing Endpoints](#️-apiprocessing-endpoints)
- [🛠️ Technologies Powering the Suite](#️-technologies-powering-the-suite)
- [🗂️ Future Enhancements (Roadmap)](#️-future-enhancements-roadmap)
- [🙌 Credits & License](#-credits--license)

---

## ✨ Core Capabilities
(Content unchanged)
This application empowers you to:
- **Convert** HTML and Markdown content into high-quality PDF documents.
- **Manage** HTML templates efficiently using a rich, integrated editor.
- **Extract** text from images (PNG, JPG, TIFF) and scanned PDFs with Tesseract OCR.
- **Enhance** OCR accuracy significantly using local Large Language Models (LLMs) via Ollama.
- **Merge** multiple PDF documents into a single, cohesive file.
- **Optimize** PDF file sizes through structural improvements and optional image re-compression.
- **Split** large PDFs into individual pages or custom ranges (future).
- **Protect** your sensitive PDF documents with user and owner passwords.

---

## 🚦 Getting Started

The recommended way to run the PDF PowerTool Suite is using Docker Compose. We provide two configurations: one for running the full stack (application, database, and Ollama) and another for running only the PostgreSQL database if you prefer to run the application locally.

### ⚙️ Prerequisites

**For all setups:**
- Ensure Git is installed to clone the repository.
- Basic familiarity with command-line/terminal usage.
- 🐳 Docker Desktop (Windows/Mac) or Docker Engine + Docker Compose (Linux) if using Docker.

**Specific to Local Machine Setup (Alternative to full Docker setup):**
| Requirement         | Description                                                                 | Icon |
|--------------------|-----------------------------------------------------------------------------|------|
| ☕ Java 17+         | Java Development Kit (JDK).                                                 | ☕   |
| 🛠️ Maven 3.6+      | Build automation tool (or use the included `mvnw` wrapper).                 | 🛠️   |
| 🧠 Tesseract OCR   | OCR engine. Ensure it's installed and the `TESSDATA_PREFIX` environment variable is correctly set. The `tessdata` directory is included in the project. | 🧠   |
| 🤖 Ollama          | Local LLM server for AI-powered OCR text enhancement (e.g., `ollama pull llama3`). | 🤖   |

---

### 🐳 Running with Docker Compose (Recommended)

#### Important Note on Docker Compose File Names
This project uses specific filenames for Docker Compose configurations:
-   `docker-compose.yml` (with a hyphen): For the full application stack.
-   `docker-compose.dbonly.yml`: For running only the PostgreSQL database.

If you encounter validation errors like "Additional property POSTGRES_DB is not allowed", ensure you are using the correct file with the `docker-compose -f <filename> ...` command and that there isn't an older, incorrectly named file (e.g., `docker_compose.yml` with an underscore) causing conflicts with your Docker tools or linters.

#### Option 1: Full Stack (App + Database + Ollama)
This method provides a fully containerized environment for the application, PostgreSQL database, and Ollama LLM server.
**File used:** `docker-compose.yml`

1.  **Clone the Repository (if not already done):**
    ```bash
    git clone <repository_url>
    cd <repository_directory>/tool/pdfdemo
    ```

2.  **Build the Application JAR:**
    The Docker build process requires the application JAR.
    ```bash
    # In tool/pdfdemo directory
    ./mvnw.cmd clean package 
    # or ./mvnw clean package for Linux/Mac
    ```

3.  **Start Services:**
    From the `tool/pdfdemo` directory:
    ```bash
    docker-compose -f docker-compose.yml up --build -d
    ```
    - `--build`: Ensures the `pdfdemo-app` image is built (or rebuilt if changed).
    - `-d`: Runs containers in detached mode.
    - Application: `http://localhost:8080`
    - Ollama: `http://localhost:11434` (on host, for management)

4.  **Managing Ollama Models in Docker (for Full Stack):**
    After the `ollama` service is up, pull models into its container:
    - Find container name: `docker ps` (look for `pdfdemo_ollama`).
    - Pull model:
      ```bash
      docker exec -it pdfdemo_ollama ollama pull llama3
      # docker exec -it pdfdemo_ollama ollama pull mistral
      ```
    Models are persisted in the `ollama_data` Docker volume.

5.  **Viewing Logs:**
    ```bash
    docker-compose -f docker-compose.yml logs -f pdfdemo-app
    # or ollama, postgres
    ```

6.  **Stopping Services:**
    ```bash
    docker-compose -f docker-compose.yml down
    ```
    To remove volumes (database, Ollama models): `docker-compose -f docker-compose.yml down -v`

#### Option 2: Database Only (for Local App Development)
Use this if you want to run the Spring Boot application locally (e.g., from your IDE) but use a Dockerized PostgreSQL database.
**File used:** `docker-compose.dbonly.yml`

1.  **Start PostgreSQL Service:**
    From the `tool/pdfdemo` directory:
    ```bash
    docker-compose -f docker-compose.dbonly.yml up -d
    ```
    - This starts only the PostgreSQL service, named `pdfdemo_postgres_dbonly`.
    - It uses a separate data volume `postgres_data_dbonly`.
    - PostgreSQL will be accessible on `localhost:5432`.

2.  **Configure Local Application:**
    Ensure your `src/main/resources/application-postgres.properties` (if using `postgres` profile locally) is configured to connect to `localhost:5432` with user `pdfuser` and password `pdfpassword`, and database `pdfdb`.
    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/pdfdb
    spring.datasource.username=pdfuser
    spring.datasource.password=pdfpassword
    ```

3.  **Run your Spring Boot application locally** (e.g., `./mvnw.cmd spring-boot:run` or from your IDE). It will connect to the Dockerized PostgreSQL.

4.  **Stopping DB Only Service:**
    ```bash
    docker-compose -f docker-compose.dbonly.yml down
    ```
    To remove its volume: `docker-compose -f docker-compose.dbonly.yml down -v`

---
### 🚀 Installation & Setup (Local Machine - Alternative)
(Content largely unchanged, ensure DB setup matches `docker-compose.dbonly.yml` if using it as a reference)

Follow these steps if you prefer to run the application directly on your host system without Docker.

#### 🐘 1. Database Setup (Local)
*   **PostgreSQL:**
    1. Install and run PostgreSQL.
    2. Create a database `pdfdb`, user `pdfuser` with password `pdfpassword`.
    3. Ensure `src/main/resources/application-postgres.properties` points to `localhost:5432/pdfdb`.
    4. Set `spring.profiles.active=postgres` in `application.properties`.
*   **H2 Database:**
    1. Set `spring.profiles.active=dev` in `application.properties`.

#### 🤖 2. Ollama LLM Server (Local)
1.  Install and run Ollama from [ollama.ai](https://ollama.ai/).
2.  Pull models: `ollama pull llama3`.
3.  Ensure Ollama is at `http://localhost:11434`.

#### 🏗️ 3. Build the Application (Local)
Navigate to `tool/pdfdemo` and run:
```bash
# Linux/macOS:
./mvnw clean install
# Windows:
mvnw.cmd clean install
```

#### ▶️ 4. Run the Application (Local)
```bash
# Linux/macOS:
./mvnw spring-boot:run
# Windows:
mvnw.cmd spring-boot:run
# Or:
java -jar target/pdfdemo-0.0.1-SNAPSHOT.jar
```
Access at `http://localhost:8080`.

---

## 🌟 Features Showcase
(Content unchanged)
...

---

## 🌐 Application Endpoints
(Content unchanged)
...

---

## 🛠️ Technologies Powering the Suite
(Content unchanged)
...

---

## 🗂️ Future Enhancements (Roadmap)
(Content unchanged, "Tesseract in Docker Image" item is now effectively done)
We're always looking to improve! Here are some features planned for future releases:

- 🧩 **Advanced PDF Splitting**: Introduce options for splitting PDFs by custom page ranges or bookmarks.
- 💧 **Watermarking**: Implement functionality to add text or image watermarks to PDF documents.
- 🖋️ **PDF Form Filling**: Enable programmatic filling of PDF form fields.
- 📏 **Granular PDF Generation Options**: Provide more control over page size, orientation, and margins during PDF creation.
- 🔍 **Dynamic Ollama Model Listing**: Automatically fetch and display available LLM models from the Ollama API.
- 🔐 **Enhanced PDF Permissions**: Offer more fine-grained control over document permissions when applying password protection (e.g., allow/disallow commenting, form filling).
- ✨ **Improved Docker Image**: Further optimize the application's Docker image for size and startup speed.

---

## 🙌 Credits & License
(Content unchanged)
...

---

Enjoy managing and manipulating your PDFs with ease! 🚀