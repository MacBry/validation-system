---
name: spring-repository-patterns
description: Generate JPA Repository interfaces with queries and Specifications for data access
context: fork
agent: spring-boot-backend-dev
disable-model-invocation: false
allowed-tools: Read, Write, Bash(mvn clean compile)
---

# Spring Repository Patterns

Generate **JPA Repository** interfaces with custom @Query methods and JpaSpecificationExecutor for advanced filtering.

## Repository Architecture

### 1. Repository Interface (`XxxRepository.java`)

```java
@Repository
public interface CoolingDeviceRepository extends JpaRepository<CoolingDevice, Long>,
                                               JpaSpecificationExecutor<CoolingDevice> {

    // Simple queries by field
    Optional<CoolingDevice> findByInventoryNumber(String inventoryNumber);

    List<CoolingDevice> findByDepartmentId(Long departmentId);

    // JPQL with joins and eager loading
    @Query("""
        SELECT cd FROM CoolingDevice cd
        JOIN FETCH cd.department d
        LEFT JOIN FETCH cd.laboratory l
        LEFT JOIN FETCH cd.materialType mt
        WHERE cd.id = :id
    """)
    Optional<CoolingDevice> findByIdWithRelations(@Param("id") Long id);

    // Pagination with filtering
    @Query("""
        SELECT cd FROM CoolingDevice cd
        JOIN cd.department dept
        WHERE (:isSuperAdmin = true
           OR dept.company.id IN :companyIds
           OR dept.id IN :deptIds)
        AND (:inventoryNumber IS NULL OR cd.inventoryNumber LIKE %:inventoryNumber%)
        AND (:name IS NULL OR LOWER(cd.name) LIKE LOWER(CONCAT('%', :name, '%')))
        ORDER BY cd.createdDate DESC
    """)
    Page<CoolingDevice> findAllAccessible(
        @Param("isSuperAdmin") boolean isSuperAdmin,
        @Param("companyIds") Collection<Long> companyIds,
        @Param("deptIds") Collection<Long> deptIds,
        @Param("inventoryNumber") String inventoryNumber,
        @Param("name") String name,
        Pageable pageable);

    // Count for validation
    @Query("""
        SELECT COUNT(cd) FROM CoolingDevice cd
        WHERE cd.department.id = :deptId
    """)
    long countByDepartmentId(@Param("deptId") Long deptId);
}
```

### 2. Specifications Class (`XxxSpecifications.java`)

```java
public class CoolingDeviceSpecifications {

    public static Specification<CoolingDevice> belongsToAccessibleDepartments(
        boolean isSuperAdmin,
        Collection<Long> allowedCompanyIds,
        Collection<Long> allowedDeptIds) {

        return (root, query, cb) -> {
            if (isSuperAdmin) {
                return cb.conjunction();  // No filter
            }

            Join<CoolingDevice, Department> deptJoin =
                root.join("department", JoinType.INNER);

            Predicate companyPredicate =
                deptJoin.get("company").get("id").in(allowedCompanyIds);
            Predicate deptPredicate =
                deptJoin.get("id").in(allowedDeptIds);

            return cb.or(companyPredicate, deptPredicate);
        };
    }

    public static Specification<CoolingDevice> hasInventoryNumber(String inventoryNumber) {
        return (root, query, cb) -> {
            if (inventoryNumber == null || inventoryNumber.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("inventoryNumber")),
                          "%" + inventoryNumber.toLowerCase() + "%");
        };
    }

    public static Specification<CoolingDevice> hasName(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("name")),
                          "%" + name.toLowerCase() + "%");
        };
    }
}
```

### 3. Usage in ServiceImpl

```java
@Service
@RequiredArgsConstructor
public class CoolingDeviceServiceImpl implements CoolingDeviceService {

    private final CoolingDeviceRepository repository;
    private final SecurityService securityService;

    @Override
    public Page<CoolingDevice> searchAccessible(String inventoryNumber, String name,
                                               Pageable pageable) {
        // Get user's accessible departments from SecurityService
        var accessInfo = securityService.getAccessibleDepartments();

        // Use Specifications for complex filtering
        Specification<CoolingDevice> spec =
            CoolingDeviceSpecifications.belongsToAccessibleDepartments(
                accessInfo.isSuperAdmin(),
                accessInfo.companyIds(),
                accessInfo.deptIds()
            )
            .and(CoolingDeviceSpecifications.hasInventoryNumber(inventoryNumber))
            .and(CoolingDeviceSpecifications.hasName(name));

        return repository.findAll(spec, pageable);
    }
}
```

## Query Patterns

### Pattern 1: Simple Queries (Auto-generated)
```java
Optional<CoolingDevice> findByInventoryNumber(String inventoryNumber);
Page<Device> findByDepartmentId(Long deptId, Pageable pageable);
```

### Pattern 2: JPQL with @Query
```java
@Query("SELECT cd FROM CoolingDevice cd WHERE cd.id = :id")
Optional<CoolingDevice> findById(@Param("id") Long id);
```

### Pattern 3: JPQL with FETCH JOIN (prevent N+1)
```java
@Query("""
    SELECT cd FROM CoolingDevice cd
    JOIN FETCH cd.department d
    LEFT JOIN FETCH cd.laboratory l
""")
List<CoolingDevice> findAllWithRelations();
```

### Pattern 4: Multi-tenancy with Security Checks
```java
@Query("""
    SELECT cd FROM CoolingDevice cd
    JOIN cd.department dept
    WHERE :isSuperAdmin = true OR dept.id IN :allowedDeptIds
""")
Page<CoolingDevice> findAccessible(...);
```

### Pattern 5: Specifications for Dynamic Filtering
```java
repository.findAll(
    CoolingDeviceSpecifications.active()
        .and(CoolingDeviceSpecifications.inDepartment(deptId))
        .and(CoolingDeviceSpecifications.byName(name)),
    pageable);
```

## Steps

1. **Read** existing repository from `src/main/java/com/mac/bry/validationsystem/device/CoolingDeviceRepository.java`
2. **Analyze** entity relationships (JoinColumn, ManyToOne, etc.)
3. **Generate** Repository interface extending JpaRepository<T, Long>, JpaSpecificationExecutor<T>
4. **Add** @Query methods for common searches (avoid N+1 with FETCH JOIN)
5. **Implement** multi-tenancy filters using SecurityService
6. **Create** companion Specifications class for complex filtering
7. **Test** with `mvn clean compile`

## Performance Checklist

- [ ] Queries use FETCH JOIN to prevent N+1 problems
- [ ] Pagination used for large result sets
- [ ] Specifications used for dynamic filtering
- [ ] Indexes exist on frequently queried columns (ForeignKey, status, created_date)
- [ ] COUNT queries optimized (if needed)
- [ ] Compiles: `mvn clean compile`

## Key Patterns

- **N+1 Prevention**: Always use FETCH JOIN in queries
- **Multi-tenancy**: All queries filter by accessible departments
- **Specifications**: Use for dynamic WHERE clauses
- **Pagination**: Always return Page<T> for large result sets
- **Lazy Loading**: Explicitly FETCH required relationships
