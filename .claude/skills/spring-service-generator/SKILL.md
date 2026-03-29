---
name: spring-service-generator
description: Generate Spring Boot Service layer (interface + ServiceImpl) following validation-system patterns
context: fork
agent: spring-boot-backend-dev
disable-model-invocation: false
allowed-tools: Read, Write, Bash(mvn clean compile)
---

# Spring Service Generator

Generate a complete Spring Boot **Service layer** following validation-system v2.11.0 patterns.

## Input Pattern

```
Entity: ValidationPlanData
Methods: create, update, find, delete, findAll
Features: Transactional, Security-aware, Audit Trail
```

## Architecture

All services follow this **Service Layer Pattern**:

### 1. Service Interface (`XxxService.java`)

```java
public interface ValidationPlanDataService {
    ValidationPlanData save(ValidationPlanData entity);
    Optional<ValidationPlanData> findById(Long id);
    Page<ValidationPlanData> findAll(Pageable pageable);
    void deleteById(Long id);
}
```

### 2. ServiceImpl Implementation (`XxxServiceImpl.java`)

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)  // ← DEFAULT: readOnly
public class ValidationPlanDataServiceImpl implements ValidationPlanDataService {

    private final ValidationPlanDataRepository repository;
    private final SecurityService securityService;

    @Override
    @Transactional  // ← Override for write operations
    public ValidationPlanData save(ValidationPlanData entity) {
        log.debug("Saving ValidationPlanData: {}", entity);
        return repository.save(entity);
    }
}
```

## Key Features to Include

1. **Dependency Injection**: `@RequiredArgsConstructor` + `final` fields
2. **Logging**: `@Slf4j` from Lombok
3. **Transactions**: Class-level `@Transactional(readOnly=true)`, method-level `@Transactional` for writes
4. **Security Awareness**: Inject `SecurityService` for multi-tenancy checks
5. **Exception Handling**: Use `orElseThrow()` with meaningful messages
6. **Envers Audit**: All modifications automatically audited if entity is `@Audited`

## Typical Methods

- `XxxService save(Xxx entity)` - @Transactional write
- `Optional<Xxx> findById(Long id)` - @Transactional(readOnly=true)
- `Page<Xxx> findAll(Pageable pageable)` - readOnly with security filtering
- `void deleteById(Long id)` - @Transactional write
- `List<Xxx> findByXxx(String param)` - custom queries with @Query

## Steps

1. **Read** existing service pattern from `src/main/java/com/mac/bry/validationsystem/device/CoolingDeviceServiceImpl.java`
2. **Analyze** repository interface to understand available queries
3. **Generate** Service interface with domain methods
4. **Generate** ServiceImpl with @Service, @RequiredArgsConstructor, @Slf4j, @Transactional
5. **Add** security checks using SecurityService for multi-tenancy
6. **Test** with `mvn clean compile`

## Example Reference

Entity: `CoolingDevice`
Service: `CoolingDeviceServiceImpl`
Pattern file: `src/main/java/com/mac/bry/validationsystem/device/CoolingDeviceServiceImpl.java`

## Validation Checklist

- [ ] Service interface defined with business methods
- [ ] ServiceImpl has @Service, @RequiredArgsConstructor, @Slf4j
- [ ] @Transactional(readOnly=true) on class, @Transactional on write methods
- [ ] log.debug() calls for debugging
- [ ] SecurityService injected for multi-tenancy
- [ ] Exception handling with orElseThrow()
- [ ] Compiles: `mvn clean compile`
