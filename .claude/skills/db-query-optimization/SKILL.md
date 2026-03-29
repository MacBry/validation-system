---
name: db-query-optimization
description: Optimize MySQL queries, add indexes, and detect N+1 problems
context: fork
agent: db-specialist-mysql-flyway
disable-model-invocation: false
allowed-tools: Read, Write, Bash(/path/to/mysql.exe *), Bash(mvn clean compile)
---

# Query Optimization Expert

Optimize **MySQL queries** in JPA/Hibernate repositories by detecting N+1 problems, adding indexes, and using FETCH JOINs.

## Common Performance Problems

### Problem 1: N+1 Query Problem

**Symptom**: Loading 100 devices takes 101 queries (1 main query + 100 for related data)

```java
// ❌ BAD: N+1 Problem
List<CoolingDevice> devices = repository.findAll();  // 1 query
for (CoolingDevice d : devices) {
    String deptName = d.getDepartment().getName();  // +100 queries!
}
```

**Solution**: Use FETCH JOIN

```java
// ✅ GOOD: Single query with join
@Query("""
    SELECT DISTINCT cd FROM CoolingDevice cd
    JOIN FETCH cd.department d
    LEFT JOIN FETCH cd.laboratory l
""")
List<CoolingDevice> findAllWithRelations();
```

### Problem 2: Missing Indexes

**Symptom**: Query takes 5+ seconds on large tables (10M+ rows)

```sql
-- ❌ BAD: No index on foreign key
SELECT * FROM cooling_devices WHERE department_id = 123;  -- Full table scan

-- ✅ GOOD: Add index
ALTER TABLE cooling_devices
  ADD INDEX idx_department_id (department_id);
```

### Problem 3: LIKE Queries on Large Text

**Symptom**: LIKE '%term%' scans entire table

```sql
-- ❌ BAD: Leading wildcard prevents index use
SELECT * FROM devices WHERE inventory_number LIKE '%INV%';

-- ✅ GOOD: Use fulltext search or prefix matching
SELECT * FROM devices WHERE inventory_number LIKE 'INV%';
-- Or add fulltext index:
ALTER TABLE devices ADD FULLTEXT INDEX ft_inventory (inventory_number);
```

## Optimization Techniques

### 1. FETCH JOIN for Relationships

```java
@Query("""
    SELECT cd FROM CoolingDevice cd
    JOIN FETCH cd.department d
    LEFT JOIN FETCH cd.laboratory l
    LEFT JOIN FETCH cd.materialType mt
    WHERE cd.id = :id
""")
Optional<CoolingDevice> findByIdWithRelations(@Param("id") Long id);
```

**Why FETCH JOIN?**
- Loads related entities in same query
- Prevents N+1 problem
- Works with Spring Data JPA repositories

### 2. Add Strategic Indexes

```sql
-- Foreign keys (usually queried)
ALTER TABLE cooling_devices
  ADD INDEX idx_department_id (department_id),
  ADD INDEX idx_laboratory_id (laboratory_id),
  ADD INDEX idx_material_type_id (material_type_id);

-- Status columns (frequently filtered)
ALTER TABLE validations
  ADD INDEX idx_status (status);

-- Date columns (range queries)
ALTER TABLE measurements
  ADD INDEX idx_created_at (created_at);

-- Composite index (multi-column WHERE)
ALTER TABLE validations
  ADD INDEX idx_device_status_date (cooling_device_id, status, created_at);

-- Unique indexes (prevent duplicates)
ALTER TABLE users
  ADD UNIQUE INDEX uk_email (email);
```

### 3. Pagination for Large Result Sets

```java
// ❌ BAD: Load all 100,000 devices
List<CoolingDevice> all = repository.findAll();

// ✅ GOOD: Load in pages
@Query("""
    SELECT cd FROM CoolingDevice cd
    JOIN FETCH cd.department d
    WHERE :isSuperAdmin = true OR cd.department.id IN :deptIds
    ORDER BY cd.createdDate DESC
""")
Page<CoolingDevice> findAccessible(
    @Param("isSuperAdmin") boolean isSuperAdmin,
    @Param("deptIds") Collection<Long> deptIds,
    Pageable pageable);  // Returns pages of 20 by default
```

### 4. Count Optimization

```java
// ❌ BAD: COUNT(*) after complex JOIN
@Query("""
    SELECT COUNT(cd) FROM CoolingDevice cd
    LEFT JOIN cd.validations v
    WHERE v.status = 'COMPLETED'
""")
long countWithValidations();

// ✅ GOOD: Simple COUNT on index
@Query("""
    SELECT COUNT(cd) FROM CoolingDevice cd
    WHERE cd.department.id = :deptId
""")
long countByDepartment(@Param("deptId") Long deptId);
```

### 5. Specifications for Dynamic Filtering

```java
// Instead of many @Query methods, use Specifications
Specification<CoolingDevice> spec =
    CoolingDeviceSpecifications.belongsToAccessibleDepartments(...)
        .and(CoolingDeviceSpecifications.hasInventoryNumber(inventoryNumber))
        .and(CoolingDeviceSpecifications.hasName(name))
        .and(CoolingDeviceSpecifications.byStatus(status));

Page<CoolingDevice> results = repository.findAll(spec, pageable);
```

## MySQL EXPLAIN Analysis

### Check if Query Uses Indexes

```bash
mysql> EXPLAIN SELECT * FROM cooling_devices WHERE department_id = 5;
+----+-------------+------------------+------+---------------+--------------------+---------+-------+------+-------+
| id | select_type | table            | type | possible_keys | key                | key_len | ref   | rows | Extra |
+----+-------------+------------------+------+---------------+--------------------+---------+-------+------+-------+
|  1 | SIMPLE      | cooling_devices  | ref  | idx_department_id | idx_department_id | 8 | const | 12 | NULL  |
+----+-------------+------------------+------+---------------+--------------------+---------+-------+------+-------+
```

**Good signs**:
- `type = ref` or `type = range` (using index)
- `key` shows index name
- `rows` shows small number

**Bad signs**:
- `type = ALL` (full table scan) ❌
- `key = NULL` (no index used) ❌
- `rows = huge number` (scanning millions) ❌

### Example Query Analysis

```bash
# Query 1: Full table scan (BAD)
mysql> EXPLAIN SELECT * FROM devices WHERE inventory_number LIKE '%INV%';
type: ALL, rows: 1000000  ❌

# Query 2: With index (GOOD)
mysql> EXPLAIN SELECT * FROM devices WHERE inventory_number = 'INV-123';
type: ref, rows: 1  ✅

# Query 3: JOIN without FETCH (N+1 risk)
mysql> EXPLAIN SELECT cd.id FROM cooling_devices cd WHERE cd.department_id = 5;
type: ref, rows: 12  ✅ (SQL level OK, but ORM may load departments separately)

# Query 4: Compound index
mysql> EXPLAIN SELECT * FROM validations WHERE device_id = 5 AND status = 'COMPLETED' ORDER BY created_at DESC;
type: range, key: idx_device_status_date ✅
```

## Optimization Checklist

- [ ] No SELECT * without WHERE (full table scans)
- [ ] No LIKE '%term%' (use fulltext search or prefix matching)
- [ ] No N+1 problems (use FETCH JOIN for relationships)
- [ ] Foreign key columns have indexes
- [ ] Status/category columns have indexes
- [ ] Date range columns (created_at, updated_at) have indexes
- [ ] All JOINs have indexes on join columns
- [ ] EXPLAIN shows index usage (type != ALL)
- [ ] Pagination used for large result sets (Page<T>)
- [ ] Composite indexes for multi-column WHERE

## Performance Monitoring

```bash
# Check slow query log
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;  # Log queries > 1 second

# Find missing indexes
ANALYZE TABLE cooling_devices;

# Check table statistics
SHOW TABLE STATUS LIKE 'cooling_devices';

# Monitor connections
SHOW PROCESSLIST;
```

## JPA Tips for Performance

### Eager vs Lazy Loading

```java
// ❌ Lazy loading (default) - causes N+1
public class Device {
    @ManyToOne(fetch = FetchType.LAZY)  // Default
    private Department department;
}

// ✅ Better: Use FETCH JOIN in query
@Query("""
    SELECT cd FROM CoolingDevice cd
    JOIN FETCH cd.department d
""")

// ⚠️ Eager loading - loads even when not needed
public class Device {
    @ManyToOne(fetch = FetchType.EAGER)  // Avoid!
    private Department department;
}
```

### Batch Processing

```java
// Process large data in batches to avoid memory issues
List<Long> ids = ...;  // 1M devices
int BATCH_SIZE = 1000;

for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
    List<Long> batch = ids.subList(i, Math.min(i + BATCH_SIZE, ids.size()));
    List<CoolingDevice> devices = repository.findByIds(batch);
    // Process batch
}
```

## Steps to Optimize Query

1. **Identify** slow query from logs or EXPLAIN plan
2. **Analyze** with EXPLAIN (check for type=ALL, key=NULL)
3. **Add** missing indexes if needed
4. **Fix** N+1 problem with FETCH JOIN
5. **Test** with `EXPLAIN` again
6. **Deploy** migration to add index
7. **Monitor** slow_query_log after deployment

## Key Patterns from validation-system

- **Multi-tenancy**: All queries filter by accessible departments (SecurityService)
- **Pagination**: Always use Page<T> for large result sets
- **Fetch Joins**: All queries with relationships use JOIN FETCH
- **Compound Indexes**: Common multi-column filters have composite indexes
- **No Wildcard Prefixes**: LIKE 'prefix%' not LIKE '%term%'
