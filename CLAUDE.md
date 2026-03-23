# CLAUDE.md - Project Onboarding Guide

## 🚀 Project Overview
**Validation System V2** is a specialized laboratory application designed for the pharmaceutical and medical industry. It manages the validation of cooling devices (fridges, freezers) by importing and analyzing binary data from **TESTO data loggers** (format: `.vi2`).

### 🛡️ Compliance Standards
The system is built to meet **GAMP 5**, **FDA 21 CFR Part 11**, and **EU GMP Annex 11** requirements, including:
- Electronic Signatures (TSA-backed).
- Comprehensive Audit Trail.
- Session Management & Forced Password Policies.

---

## 🛠️ Build & Run Commands

### Backend (Spring Boot + Java 17)
- **Full Build**: `mvn clean install`
- **Run Development**: `mvn spring-boot:run` (Active profile: `dev`)
- **Run Tests**: `mvn test` (Includes H2-based integration tests)
- **Package JAR**: `mvn clean package -DskipTests`

### Infrastructure (Docker)
- **Start Stack**: `docker-compose up -d` (MySQL 8, Redis, SMTP)
- **Stop Stack**: `docker-compose down`

---

## 🏗️ Architecture & Core Components

### 1. Vi2 Binary Decoder (`com.mac.bry.validationsystem.measurement.Vi2FileDecoder`)
The most critical part of the intellectual property.
- **Format**: OLE2 / CFB (Compound File Binary).
- **Streams**:
    - `summary`: Contains `measurementCount` (offset 12) and `intervalMs` (offset 28).
    - `t17b`: Contains the hardware Serial Number (offset 13).
    - `data/values`: Contains raw measurements (8-byte blocks: 4B Float32 Temp + 4B Metadata Tick).
    - `data/timezone`: Contains UTC Bias and Timezone names.
- **Time Calculation**: `BASE_DATE` = 1961-07-09 01:30:00. Ticks per day: 131072.

### 2. Security System
- **Filter Chain**: Custom filters for Session Security (`SessionSecurityFilter`) and Forced Password Changes (`ForcedPasswordChangeFilter`).
- **Audit Trail**: Hibernate Envers is used for entity versioning. Custom logging for security events is stored in `security_audit_log`.

---

## ✍️ Coding Guidelines

- **Language**: Source code and comments should be in **English**. Key business logic (Validation, Calibration) may contain dual-language terms.
- **Styling**: Bootstrap 5 for UI, Vanilla CSS for custom overrides.
- **Error Handling**: Use the Global Exception Handler (`GlobalExceptionHandler`). Avoid exposing stack traces in production.
- **Logging**: Use `@Slf4j` (Lombok). Log critical security events and file parsing steps.

## 📁 Repository Structure
- `src/main/java`: Backend source code.
- `src/main/resources/templates`: Thymeleaf views.
- `docs/`: Technical specifications and GMP documentation (Note: Work in progress to unify to English).
- `.env`: (Local only) Environment secrets.