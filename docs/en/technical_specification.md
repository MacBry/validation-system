# Technical Specification & Architecture: Validation Cold Control (VCC)

## 1. Tech Stack
VCC is a modern web application built on a monolithic architecture with clear layer separation.

- **Programming Language**: Java 17
- **Framework**: Spring Boot 3.2.2
- **Data Layer**: Spring Data JPA / Hibernate 6.4
- **Database**: MySQL 8.0 (Production) / H2 (Test/Dev)
- **Security**: Spring Security 6, BCrypt, Envers (Audit Log)
- **Frontend**: Thymeleaf 3.1, HTML5, Vanilla CSS, JavaScript (AJAX)
- **Reporting**: iText 7, JFreeChart, Apache POI
- **Cache & Rate Limiting**: Redis, Lettuce, Bucket4j

---

## 2. System Architecture

### 2.1 Application Layers
The system follows a classic layered pattern:
1. **Controller**: Handles HTTP requests, input validation, and DTO mapping.
2. **Service**: Business logic, transaction management, and statistical calculations.
3. **Repository**: Data access abstraction (JPA/QueryDSL).
4. **Entity**: Relational database model mapping.

### 2.2 Multi-tenancy Architecture
The system implements logical data isolation (Discriminated Multi-tenancy). Key entities (Device, Validation) are linked to a `Company` and `Department`. Data filtering occurs at the Repository query level or via automatic Hibernate filters.

---

## 3. Core Modules

- **Validation Module (`validation`)**: The heart of the system, responsible for `.vi2` file imports, data pairing from loggers, and result calculation.
- **Security Module (`security`)**: Manages sessions, role-based authorization, and password recovery mechanisms.
- **Audit Module (`audit`)**: Utilizes Hibernate Envers to track every change in critical entities (audit trail compliant with Annex 11 / 21 CFR Part 11).
- **Notification Module (`notification`)**: Real-time engine analyzing validation and calibration expiration dates.

---

## 4. Data Security
- **Encryption**: All passwords are hashed using the BCrypt algorithm with a random salt.
- **Rate Limiting**: Brute Force protection implemented via Bucket4j and Redis.
- **CSRF**: Build-in Spring Security protection against Cross-Site Request Forgery.
- **Sanitization**: Input data is validated using Bean Validation (JSR 303).

---

## 5. Integrations & File Formats
- **Import**: Support for the `.vi2` format (OLE2 binary format) via a dedicated decoder.
- **Export**:
    - **PDF**: Dynamically generated reports using iText.
    - **Excel**: Summaries generated via Apache POI.
