---
name: db-flyway-migration
description: Generate Flyway database migrations following validation-system schema patterns
context: fork
agent: db-specialist-mysql-flyway
disable-model-invocation: false
allowed-tools: Read, Write, Bash(/path/to/mysql.exe *), Bash(mvn flyway:*)
---

# Flyway Migration Generator

Generate **Flyway database migrations** following validation-system v2.11.0 patterns.

## Migration Naming Convention

**Format**: `V{VERSION}__{DESCRIPTION}.sql`

**Examples**:
- `V2.28.0__add_periodic_revalidation_type.sql` - Add ENUM type
- `V2.28.1__validation_plan_data.sql` - Create new table
- `V2.28.2__add_role_qa.sql` - Insert test data
- `V2.28.3__fix_revinfo_timestamp_default.sql` - Fix column default

**Rules**:
- Version format: X.Y.Z (e.g., 2.28.0)
- Description: UPPERCASE_SNAKE_CASE, max 50 chars
- Single underscore separates version and description
- Must be unique (Flyway enforces this)

## Schema Standards

### 1. CREATE TABLE Pattern

```sql
CREATE TABLE validation_plan_data (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,

    -- ===== COLUMNS (grouped by feature) =====

    revalidation_reason LONGTEXT NULL
        COMMENT 'Uzasadnienie dla rewalidacji',

    mapping_status VARCHAR(20) NULL
        COMMENT 'CURRENT | OVERDUE | NEVER',

    mapping_overdue_acknowledged BOOLEAN DEFAULT FALSE
        COMMENT 'Potwierdzenie świadomej kontynuacji',

    -- ===== TIMESTAMPS =====

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- ===== INDEXES =====

    INDEX idx_mapping_status (mapping_status),
    INDEX idx_created_at (created_at)
)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Plan danych rewalidacji okresowej (GMP Annex 15)';
```

### 2. ALTER TABLE Pattern

```sql
-- Add new column
ALTER TABLE validation_drafts
  ADD COLUMN plan_data_id BIGINT UNIQUE NULL
    COMMENT 'Link to validation plan (PERIODIC_REVALIDATION only)';

-- Add foreign key
ALTER TABLE validation_drafts
  ADD CONSTRAINT fk_draft_plan_data
    FOREIGN KEY (plan_data_id) REFERENCES validation_plan_data(id)
      ON DELETE SET NULL;

-- Modify ENUM
ALTER TABLE validation_drafts
  MODIFY COLUMN procedure_type
    ENUM('OQ', 'PQ', 'MAPPING', 'PERIODIC_REVALIDATION') NOT NULL
    COMMENT 'Typ procedury walidacji';

-- Add index
ALTER TABLE validation_plan_data
  ADD INDEX idx_technik_username (plan_technik_username);
```

### 3. INSERT Pattern (Test Data / System Data)

```sql
INSERT IGNORE INTO roles (name, description) VALUES
  ('ROLE_QA', 'Quality Assurance - Plan reviewer & approver');

INSERT IGNORE INTO mapping_lookup (status, threshold_days, description) VALUES
  ('CURRENT', 730, 'Mapowanie w ciągu ostatnich 730 dni'),
  ('OVERDUE', 731, 'Mapowanie po 730+ dniach'),
  ('NEVER', NULL, 'Mapowanie nigdy nie wykonane');
```

## Column Standards

### Data Types
- **Identifiers**: `BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY`
- **Text**: `VARCHAR(length)` for names, `LONGTEXT` for descriptions
- **Numbers**: `DECIMAL(10,4)` for precise values (temperatures), `DOUBLE` for stats
- **Booleans**: `BOOLEAN` (maps to TINYINT(1))
- **Dates**: `DATE` for dates only, `DATETIME(6)` for timestamps with microseconds
- **Enums**: `ENUM('VALUE1', 'VALUE2')` for fixed sets
- **JSON**: `JSON NULL` for flexible structures

### Constraints

```sql
-- Primary key (implicit with id BIGINT AUTO_INCREMENT)
id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,

-- Not null
column_name VARCHAR(50) NOT NULL,

-- Unique constraint
UNIQUE KEY uk_unique_column (column_name),

-- Check constraint
CHECK (current_step >= 1 AND current_step <= 13),

-- Foreign key
CONSTRAINT fk_table_ref
    FOREIGN KEY (ref_id) REFERENCES referenced_table(id)
      ON DELETE CASCADE,  -- or SET NULL, RESTRICT

-- Indexes
INDEX idx_column_name (column_name),
INDEX idx_compound (column1, column2),  -- Composite index
UNIQUE INDEX uk_unique (column_name),   -- Unique index
```

### Comments

Every column and table must have Polish COMMENT:
```sql
column_name VARCHAR(50) NOT NULL COMMENT 'Opis pola po polsku',

CREATE TABLE users (
    ...
)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Użytkownicy systemu (wymaga Spring Security)';
```

## Migration Checklist

### Before Migration

- [ ] Migration naming follows convention (V{VERSION}__{DESCRIPTION}.sql)
- [ ] Version number incremented from last migration
- [ ] No existing migration with same version
- [ ] SQL tested on dev database

### In Migration Script

- [ ] Comment block explaining purpose (see template below)
- [ ] All columns have COMMENT
- [ ] All tables have ENGINE=InnoDB, CHARSET=utf8mb4
- [ ] Foreign keys have ON DELETE strategy (CASCADE/SET NULL)
- [ ] Indexes created for:
  - Primary keys (auto)
  - Foreign keys
  - Frequently queried columns (status, created_at, username)
- [ ] All data type sizes are appropriate
- [ ] No reserved SQL keywords used
- [ ] All UPPERCASE: CREATE, TABLE, ALTER, INSERT, VALUES, etc.

### After Migration

- [ ] Run: `mvn flyway:info` - shows migration status
- [ ] Run: `mvn flyway:migrate` - applies migration to dev DB
- [ ] Verify: `mvn flyway:validate` - checks for errors
- [ ] Backup production before deploying

## Migration Template

```sql
-- ============================================================================
-- {DESCRIPTION}
-- ============================================================================
-- Migration: V{VERSION}
-- Purpose: {Why this migration is needed - GMP compliance, bug fix, feature, etc.}
-- Compliance: {GMP Annex 15, FDA 21 CFR Part 11, etc. if applicable}
-- ============================================================================

-- Step 1: Create table / Add columns / Modify constraints
CREATE TABLE new_table (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- columns here
);

-- Step 2: Add foreign key constraints
ALTER TABLE other_table
  ADD CONSTRAINT fk_reference FOREIGN KEY (ref_id) REFERENCES new_table(id);

-- Step 3: Add indexes for performance
ALTER TABLE new_table
  ADD INDEX idx_key_column (key_column);

-- Step 4: Insert system data (if needed)
INSERT IGNORE INTO system_lookup VALUES (...);

-- ============================================================================
-- VERIFICATION (run locally after migration)
-- ============================================================================
-- SELECT * FROM information_schema.COLUMNS WHERE TABLE_NAME = 'new_table';
-- SHOW CREATE TABLE new_table;
-- SELECT COUNT(*) FROM new_table;
```

## Examples from Project

### V2.24.0__wizard_schema.sql (120+ lines)
- Creates validation_drafts table with procedure_type ENUM
- Creates custom_acceptance_criteria (one-to-many)
- Creates oq_test_results, pq_checklist_items (OQ/PQ specific)
- Adds indexes for draft_id, procedure_type, created_by

### V2.28.0__add_periodic_revalidation_type.sql
- Extends procedure_type ENUM to include PERIODIC_REVALIDATION
- Extends status ENUM to include AWAITING_QA_APPROVAL
- Relaxes step_lock_from CHECK constraint
- Extends current_step CHECK to 13

### V2.28.1__validation_plan_data.sql
- Creates validation_plan_data table with all plan fields
- Adds foreign key to validation_drafts (plan_data_id)
- Includes both QA approval paths (electronic + scanned)
- Includes audit trail fields (rejection reason, timestamps)

## Steps

1. **Read** existing migrations from `src/main/resources/db/migration/`
2. **Analyze** entity structure to determine column types and constraints
3. **Check** for naming conflicts (column names, table names)
4. **Write** migration SQL following template above
5. **Test** locally: `mvn flyway:migrate` on dev DB
6. **Verify** schema: Check table structure, indexes, constraints
7. **Run** tests: `mvn test` to ensure no ORM mapping breaks

## Key Patterns

- **Never modify existing migrations** - Flyway checksums would fail
- **Always test migrations locally first** - reversible on dev, not on prod
- **Foreign key cascading** - Think about data consistency
- **Indexing strategy** - Index what you query (FK, status, dates)
- **Charset consistency** - Always utf8mb4_unicode_ci for Polish support
