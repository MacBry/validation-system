# 🚀 Validation System V2 (Enterprise Edition)

[![Java Version](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Internal-blue.svg)](#)

A high-compliance web application designed for the pharmaceutical and laboratory sectors to manage, analyze, and validate cooling device performance. Features a proprietary binary decoder for TESTO `.vi2` data logger files.

---

## ✨ Key Features

- 🛰️ **Proprietary Vi2 Decoder**: Parses complex OLE2/CFB binary streams from TESTO loggers with 100% accuracy compared to native software.
- 📦 **Validation Wizard**: Step-by-step workflow (OQ/PQ) for qualifying fridges, freezers, and cold rooms.
- 🛡️ **GMP Compliance**: Built-in Audit Trail, Electronic Signatures (TSA), and strict session security following FDA 21 CFR Part 11.
- 📊 **Advanced Analytics**: Automatic calculation of stability, drift, and spike detection using specialized laboratory algorithms.
- 📄 **Report Generation**: Fully automated PDF generation for validation packages and calibration certificates.

---

## 🛠️ Technology Stack

- **Backend**: Java 17, Spring Boot 3.x, Spring Security, Hibernate (Envers for Audit Trail).
- **Database**: MySQL 8.0, Redis (Rate Limiting).
- **Frontend**: Thymeleaf, Bootstrap 5, Highcharts (Data Visualization).
- **Infrastructure**: Docker & Docker Compose.

---

## 🚀 Quick Start

### Prerequisites
- JDK 17+
- Maven 3.6+
- Docker & Docker Compose

### 1. Configure Secrets
Create a `.env` file in the root directory (refer to `.env.example` if available):
```env
DB_PASSWORD=your_secure_password
REDIS_PASSWORD=your_redis_password
MAIL_PASSWORD=your_smtp_password
```

### 2. Launch Infrastructure
```bash
docker-compose up -d
```

### 3. Build & Run
```bash
mvn clean spring-boot:run
```
The application will be available at `https://localhost:8443`.

---

## 📂 Project Structure

- `measurement/`: Core logic for `.vi2` binary parsing and temperature analysis.
- `validation/`: Workflow engine for OQ/PQ processes.
- `security/`: Advanced security filters and compliance-related access control.
- `docs/`: GMP-required technical and functional specifications.

---

## 🔍 Technical Details: Vi2 Decoding

The system decodes `.vi2` files by accessing internal OLE2 streams:
- **Time Epoch**: 1961-07-09 01:30:00.
- **Tick Resolution**: 131072 ticks per 24 hours.
- **Data Block**: 8-byte sequences (4B Float32 Temperature + 4B Metadata Tick).

For more details, see [Detailed Vi2 Specification](docs/DOKLADNY_DEKODER_VI2_V2.md).

---

## 📄 License
Internal application. All rights reserved.

**Version**: 2.12.0-ENTERPRISE  
**Status**: ✅ Production Ready
