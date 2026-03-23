# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🎯 System Overview
Enterprise validation system for pharmaceutical/laboratory cooling device qualification (OQ/PQ) with TESTO recorder integration. Version 2.12.0-ENTERPRISE supports multi-tenancy, electronic signatures, audit trails, and GMP/GDP compliance.

**Critical Accuracy Requirement:** All temperature calculations, MKT (Mean Kinetic Temperature), and compliance statistics are subject to regulatory inspection (GIF, WIF, FDA, EMA). Mathematical precision is mandatory - use Double/BigDecimal and account for calibration uncertainties.

## 🛠️ Technology Stack
- **Backend:** Spring Boot 3.2.2, Java 17, Spring Security, Hibernate Envers
- **Database:** MySQL 8.0+ with audit trails (_AUD tables)
- **Security:** HTTPS, Spring Security, Redis rate limiting (Bucket4j), password policies
- **Frontend:** Thymeleaf, Bootstrap 5, Chart.js
- **PDF/Signatures:** iText 7 with electronic signatures (PKCS12)
- **File Processing:** TESTO .vi2 binary decoder (OLE2/CFB format)

## 🚀 Development Commands

### Application Startup
```bash
# Development (HTTPS on port 8443)
mvn clean spring-boot:run

# Production JAR
mvn clean package -DskipTests
java -jar target/validation-system-2.12.0-ENTERPRISE.jar
```

### Testing
```bash
# All tests
mvn clean test

# Specific test class
mvn test -Dtest="ForcedPasswordChangeTest"

# Controller integration tests
mvn test -Dtest="*Controller*"

# Service layer tests
mvn test -Dtest="*Service*"
```

### Database Operations
```bash
# Access MySQL (Windows)
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -u root -padmin validation_system

# Run migration (if needed)
mysql -u root -padmin validation_system < src/main/resources/db/migration/V2.12.0__security_schema.sql
```

### Development Services
```bash
# Start Redis (required for rate limiting)
redis-server

# Start MySQL
net start mysql  # Windows
sudo systemctl start mysql  # Linux
```

## 📁 Architecture Overview

### Core Domain Packages
- `measurement/` - TESTO .vi2 decoder, temperature data processing
- `validation/` - Validation protocols, PDF generation, electronic signatures
- `device/` & `laboratory/` - Equipment and facility management
- `calibration/` - Certificate management, traceability

### Enterprise Security (v2.12.0+)
- `security/` - Authentication, authorization, password policies, multi-tenancy
- `audit/` - Hibernate Envers integration, GMP Annex 11 compliance
- `certificates/` - Company certificate management for signing

### Supporting Modules
- `deviation/` - Temperature excursion detection and CAPA tracking
- `stats/` - Advanced statistical calculations for validation reports
- `materialtype/` - Product categorization for validation requirements

## 🔧 Configuration Profiles

### Development Profile (application-dev.properties)
- **URL:** `https://localhost:8443` (HTTPS required)
- **SSL:** Self-signed certificate (keystore.p12)
- **Database:** MySQL on localhost:3306
- **Redis:** Required for rate limiting (localhost:6379)
- **Logging:** DEBUG level enabled

### Production Considerations
- Configure proper SSL certificates
- Update database connection pools
- Disable SQL logging
- Configure proper Redis clustering

## 🛡️ Security Architecture

### Authentication & Authorization
- Spring Security with custom UserDetailsService
- Role-based access (SUPER_ADMIN, ADMIN, USER)
- Account locking after failed attempts
- Remember-me functionality

### Password Management
- Forced password change for new users
- Policy enforcement (8+ chars, mixed case, numbers, symbols)
- BCrypt encryption (strength 12)
- Password change tracking

### Session & Rate Limiting
- HTTPS-only cookies, 30-minute timeout
- Redis-backed rate limiting (Bucket4j)
- CSRF protection enabled

### Audit Trail (Hibernate Envers)
- Automatic audit logging for all entities
- GMP Annex 11 compliant audit trail
- Track who changed what and when
- Immutable audit records in *_AUD tables

## 📊 Electronic Signatures (GMP Compliance)
- PDF signing with PKCS12 certificates
- Validation status locks after signing
- Signed documents stored in `uploads/signed/`
- Compliance with FDA 21 CFR Part 11

## 🗄️ Database Schema Evolution
- **Core:** Hibernate auto-DDL (`spring.jpa.hibernate.ddl-auto=update`)
- **Migrations:** Manual SQL files in `src/main/resources/db/migration/`
- **Audit Tables:** Auto-generated *_AUD tables via Envers

Key migration files:
- V2.12.0__security_schema.sql - User management tables
- V2.13.0__envers_audit_schema.sql - Audit trail setup
- V2.14.0__company_certificates.sql - Certificate management

## 🎨 UI Guidelines
- Use existing Bootstrap 5 classes, avoid custom CSS
- Responsive design for all screen sizes
- Polish language throughout interface
- Modern gradient styling for forms

## ⚡ Performance Considerations
**Critical:** `MeasurementPoint` tables can contain 100,000+ records from .vi2 files.
- Use JPA aggregates, avoid loading all measurement points into memory
- Leverage calculated fields in `MeasurementSeries`
- Implement pagination for large datasets
- Use @Query with database-level calculations

## 🐛 Development Guidelines

### Error Handling
- Use `@Slf4j` for logging
- Return meaningful JSON responses in REST controllers
- Use `FlashAttributes` for redirects with user feedback
- Catch domain-specific exceptions at controller level

### Code Conventions
- **Variables/Methods:** English naming
- **Comments/JavaDoc:** Polish preferred for domain context
- **UI Text:** Polish throughout
- **Critical Calculations:** Use Double/BigDecimal for temperature/time

### Testing Strategy
- Service layer: Unit tests with Mockito
- Controllers: `@WebMvcTest` with MockMvc
- Integration: Full Spring context for complex flows
- Security: Test authentication/authorization paths

## 🔍 Common Debugging

### SSL Certificate Issues
```bash
# Regenerate keystore if needed
keytool -genkeypair -alias tomcat -storetype PKCS12 -keyalg RSA -keysize 2048 -keystore keystore.p12 -storepass ***REMOVED***
```

### Database Connection
```bash
# Test connection
mysql -u root -padmin -h localhost -P 3306 validation_system
```

### Redis Connectivity
```bash
# Test Redis
redis-cli ping
```

### File Upload Issues
- Check `uploads/` directory permissions
- Verify `spring.servlet.multipart.max-file-size` settings

## 📝 Code Style Notes
- Follow existing patterns in domain services
- Use `Optional<T>` for nullable database results
- Implement proper validation in DTOs
- Log significant business operations at INFO level
- Use `@Transactional` for multi-step database operations

## 🔧 Memory Settings
For large .vi2 file processing:
```bash
java -Xmx2G -Xms512M -jar target/validation-system-2.12.0-ENTERPRISE.jar
```